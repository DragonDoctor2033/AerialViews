package com.neilturner.aerialviews.ui.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import com.neilturner.aerialviews.models.enums.WeatherEffectsFrameRate
import timber.log.Timber
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

/**
 * What the weather effects need from the view which draws them. The controller only knows this,
 * so that it can be tested without a view.
 */
interface EffectsSurface {
    /** The most frames per second to draw */
    fun setFrameRate(fps: Int)

    /** Fades the picture to what the weather wants. See [EffectTargets] */
    fun setTargets(targets: EffectTargets)

    /** Hides the view and stops everything for a blackout, or shows it again */
    fun setBlackout(blackout: Boolean)

    /** The effects are off: nothing is drawn, nothing is scheduled, and the view takes no part in the layout */
    fun disable()

    /** The screen is over: stops everything for good and lets go of what was made */
    fun release()
}

/**
 * Draws the weather over the video, below the clock and the other overlays: rain, snow, fog,
 * lightning and a grey tone for overcast skies, all made on the canvas while the video plays.
 *
 * - Rain is a few hundred streaks in three depth layers, drawn with one `drawLines` for each layer.
 *   Near streaks are longer, faster and brighter, and the wind tilts them. See [RainLayer].
 * - Snow is flakes in three depth layers, drawn as soft sprites which are made once. They fall
 *   slowly, sway and drift with the wind. See [SnowLayer].
 * - Fog is three big soft textures of noise, made once at a low resolution on another thread and
 *   stretched with a filter, drifting slowly in different directions, and a light veil which lowers
 *   the contrast. See [FogNoise] and [FogLayers].
 * - A storm is rain and flashes of lightning, see [LightningScheduler]. Overcast is a grey-blue tone
 *   which dims the footage. Rain, snow and fog come with some of it.
 * - By night everything is dimmer.
 *
 * What there is on the screen moves to what the weather wants over about two seconds, so a change of
 * the weather is a crossfade, and the effects fade in at the start of a clip. See [EffectMix].
 *
 * Like the analog clock it only works while it can be seen, and it costs nothing otherwise. The frames
 * come from a Choreographer callback, at the frame rate that was picked (60 or 30 a second), and stop
 * completely when
 * - there is nothing to move: no weather, a grey tone which has faded in, or effects which have
 *   faded out (see [EffectMix.isSettled]),
 * - the view is detached or hidden, such as in blackout, or
 * - its alpha is 0, until the alpha is raised again.
 * What the view has to be doing is decided by [WeatherEffectsRefresh.plan].
 *
 * A frame allocates nothing: the particles are arrays, made once, the paints and the rectangle are
 * reused, and the time is whatever the Choreographer says. Everything is made when it is first
 * needed, so a view which is [disable]d, or has dry weather, costs no more than the view.
 */
class WeatherEffectsView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr),
        EffectsSurface {
        // Set at the end of the construction: the view can be told that it is shown or hidden while
        // the super class is still being made, before any of the rest exists
        private var constructed = false

        private val random = Random(System.nanoTime())

        private var targets = EffectTargets()
        private val mix = EffectMix()
        private var fps = WeatherEffectsFrameRate.DEFAULT.fps

        // 1 for a hardware accelerated canvas, less for one which is not and can't take as much
        private var quality = 1f

        // Rain, snow and fog are made when they are first wanted
        private var rainLayers: Array<RainLayer>? = null
        private var snowLayers: Array<SnowLayer>? = null
        private var snowSprites: Array<Bitmap>? = null
        private var fogBitmaps: Array<Bitmap>? = null
        private var fogLoading = false
        private var released = false

        private val lightning = LightningScheduler(random)

        // How bright the sky is at this moment because of lightning, from 0 to 1
        private var flash = 0f

        private val fogX = FloatArray(FogLayers.COUNT)
        private val fogY = FloatArray(FogLayers.COUNT)
        private var fogLightStep = NO_STEP

        private val rainPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.BUTT
            }
        private val snowPaint = Paint().apply { isFilterBitmap = true }
        private val fogPaints = Array(FogLayers.COUNT) { Paint().apply { isFilterBitmap = true } }
        private val spriteRect = RectF()

        // On every frame, while something moves. The callback is posted again by every frame, so it
        // is stopped by not posting it again, or by removing it
        private var framing = false
        private var lastFrameNanos = 0L
        private val choreographer by lazy(LazyThreadSafetyMode.NONE) { Choreographer.getInstance() }
        private val frameCallback = Choreographer.FrameCallback { frameTimeNanos -> onFrame(frameTimeNanos) }

        // Nothing is scheduled while the view is faded out, but its fade in has to be noticed. A view
        // has no listener for its alpha going up, and an animation can change the alpha without calling
        // the view at all. But the screen is always drawn again for a change of the alpha, and a
        // pre-draw listener is called right before that, which is when the view looks
        private var watchingFadeIn = false
        private val fadeInWatcher =
            ViewTreeObserver.OnPreDrawListener {
                if (alpha > 0f) updateRefresh()
                true
            }

        init {
            constructed = true
        }

        // EffectsSurface

        override fun setFrameRate(fps: Int) {
            this.fps = fps.coerceAtLeast(1)
        }

        override fun setTargets(targets: EffectTargets) {
            if (targets != this.targets) Timber.d("WeatherEffects: $targets")
            this.targets = targets
            // While nothing can be seen the picture gets to the targets at once
            mix.step(0f, targets)
            updateRefresh()
        }

        override fun setBlackout(blackout: Boolean) {
            if (blackout) {
                // Nothing can be seen any more, so the picture is closed at once, and does not have to fade out in the dark
                mix.closeGate()
                mix.step(0f, targets)
            }
            visibility = if (blackout) INVISIBLE else VISIBLE
        }

        override fun disable() {
            visibility = GONE
            stopRefreshing()
        }

        override fun release() {
            released = true
            stopRefreshing()
            rainLayers = null
            snowLayers = null
            snowSprites = null
            fogBitmaps = null
            fogPaints.forEach { it.shader = null }
        }

        // View

        // The picture is drawn with the alpha of each part, so there is no need for the view to
        // draw into a layer of its own when it is faded: it never overlaps itself
        override fun hasOverlappingRendering(): Boolean = false

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateRefresh()
        }

        override fun onDetachedFromWindow() {
            stopRefreshing()
            super.onDetachedFromWindow()
        }

        // Also called when an ancestor changes, such as the view being hidden for a blackout
        override fun onVisibilityChanged(
            changedView: View,
            visibility: Int,
        ) {
            super.onVisibilityChanged(changedView, visibility)
            updateRefresh()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            updateRefresh()
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (!constructed) return
            rainLayers?.forEach { it.resize(w.toFloat(), h.toFloat()) }
            snowLayers?.forEach { it.resize(w.toFloat(), h.toFloat()) }
            // The sprites are as big as the flakes of this screen, so they are made again
            snowSprites = null
        }

        // This runs on every frame, so nothing in here, or in what it calls, may allocate anything:
        // no new objects, no boxing, no lambdas which capture something, no iterators
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            val gateOpacity = mix.gateOpacity
            if (gateOpacity <= 0f) return
            val night = mix.night

            // Back to front: the sky, the fog in front of it, the rain and snow in the fog, and the flash over all
            drawTint(canvas, gateOpacity, night)
            drawFog(canvas, gateOpacity, night)
            drawRain(canvas, gateOpacity, night)
            drawSnow(canvas, gateOpacity, night)
            drawFlash(canvas, gateOpacity)
        }

        private fun drawTint(
            canvas: Canvas,
            gateOpacity: Float,
            night: Float,
        ) {
            val opacity = mix.tint.opacity * gateOpacity
            if (opacity <= 0f) return
            val alpha = EffectPalette.TINT_MAX_ALPHA * mix.tint.level * opacity * EffectPalette.tintDim(night)
            if (EffectMath.toByte(alpha) == 0) return
            canvas.drawColor(EffectPalette.tint(night, alpha))
        }

        private fun drawFog(
            canvas: Canvas,
            gateOpacity: Float,
            night: Float,
        ) {
            val opacity = mix.fog.opacity * gateOpacity
            if (opacity <= 0f) return
            val level = mix.fog.level

            // The veil lifts the dark parts of the footage, which lowers the contrast
            val veil = EffectPalette.VEIL_MAX_ALPHA * level * opacity * EffectPalette.veilDim(night)
            if (EffectMath.toByte(veil) > 0) canvas.drawColor(EffectPalette.veil(night, veil))

            // The clouds. Each is a tile of noise which repeats, stretched over the screen and moved by
            // moving the canvas under it, which is cheaper than moving the texture
            if (fogBitmaps == null) return
            val w = width.toFloat()
            val h = height.toFloat()
            for (i in 0 until FogLayers.COUNT) {
                val paint = fogPaints[i]
                if (paint.shader == null) continue
                paint.alpha = EffectMath.toByte(EffectPalette.FOG_LAYER_ALPHA[i] * level * opacity * EffectPalette.fogDim(night))
                if (paint.alpha == 0) continue

                val scale = w * FogLayers.ZOOM[i] / FogNoise.TEXTURE_SIZE
                canvas.save()
                canvas.scale(scale, scale)
                canvas.translate(-fogX[i], -fogY[i])
                canvas.drawRect(fogX[i], fogY[i], fogX[i] + w / scale, fogY[i] + h / scale, paint)
                canvas.restore()
            }
        }

        private fun drawRain(
            canvas: Canvas,
            gateOpacity: Float,
            night: Float,
        ) {
            val layers = rainLayers ?: return
            val opacity = mix.rain.opacity * gateOpacity
            if (opacity <= 0f) return

            // Lighter rain is fainter, by night everything is dimmer, and a flash of lightning lights the rain up
            val strength =
                opacity * (RAIN_ALPHA_BASE + RAIN_ALPHA_SHARE * mix.rain.level) *
                    EffectPalette.rainDim(night) * (1f + EffectPalette.FLASH_RAIN_BOOST * flash)
            for (layer in layers) {
                if (layer.count == 0) continue
                rainPaint.strokeWidth = layer.strokePx
                rainPaint.color = EffectPalette.rain(night, layer.alpha * strength)
                canvas.drawLines(layer.lines, 0, layer.count * 4, rainPaint)
            }
        }

        private fun drawSnow(
            canvas: Canvas,
            gateOpacity: Float,
            night: Float,
        ) {
            val layers = snowLayers ?: return
            val sprites = snowSprites ?: return
            val opacity = mix.snow.opacity * gateOpacity
            if (opacity <= 0f) return

            val level = mix.snow.level
            val w = width.toFloat()
            val h = height.toFloat()
            for (l in layers.indices) {
                val layer = layers[l]
                if (layer.count == 0) continue
                val sprite = sprites[l]
                // Lighter snow is fainter, and by night it is a little dimmer
                snowPaint.alpha = EffectMath.toByte(EffectPalette.snowAlpha(layer.alpha, level, opacity, night))
                for (i in 0 until layer.count) {
                    val half = layer.diameterPx(i) / 2f
                    val x = layer.centreX(i)
                    val y = layer.centreY(i)
                    // Nothing is drawn which is off the screen
                    if (x + half < 0f || x - half > w || y + half < 0f || y - half > h) continue
                    spriteRect.set(x - half, y - half, x + half, y + half)
                    canvas.drawBitmap(sprite, null, spriteRect, snowPaint)
                }
            }
        }

        private fun drawFlash(
            canvas: Canvas,
            gateOpacity: Float,
        ) {
            if (flash <= 0f) return
            // A storm with heavier rain has brighter flashes
            val alpha = EffectPalette.FLASH_MAX_ALPHA * flash * gateOpacity * (FLASH_BASE + FLASH_SHARE * mix.rain.level)
            if (EffectMath.toByte(alpha) > 0) canvas.drawColor(EffectPalette.flash(alpha))
        }

        // Frames

        private fun isOnScreen(): Boolean = isAttachedToWindow && isShown && windowVisibility == VISIBLE

        private fun currentPlan(): EffectsRefresh = WeatherEffectsRefresh.plan(!mix.isSettled(targets), isOnScreen(), alpha)

        // Works out what the view has to be doing now and starts and stops things to match. It is
        // called whenever something which counts changes: being attached, shown or hidden, new targets
        // and, for a view which waits for it, the alpha
        private fun updateRefresh() {
            if (!constructed || released) return
            applyRefresh(currentPlan())
        }

        private fun applyRefresh(plan: EffectsRefresh) {
            when (plan) {
                EffectsRefresh.EVERY_FRAME -> {
                    stopWatchingFadeIn()
                    startFrames()
                }

                EffectsRefresh.WAITING_FOR_FADE_IN -> {
                    stopFrames()
                    startWatchingFadeIn()
                }

                EffectsRefresh.STOPPED -> {
                    stopRefreshing()
                }
            }
        }

        private fun stopRefreshing() {
            stopFrames()
            stopWatchingFadeIn()
        }

        private fun startFrames() {
            if (framing) return
            framing = true
            // The first frame after a stop has no time to move on by, as the time since the last one is a long one
            lastFrameNanos = 0L
            // Whatever flash there was when the frames stopped is long over
            flash = 0f
            quality = if (isHardwareAccelerated) 1f else EffectBudget.SOFTWARE_QUALITY
            Timber.d("WeatherEffects: Drawing up to $fps frames a second, hardware accelerated: $isHardwareAccelerated")
            postNextFrame()
        }

        private fun stopFrames() {
            if (!framing) return
            framing = false
            choreographer.removeFrameCallback(frameCallback)
        }

        private fun postNextFrame() {
            val wait = FramePacing.waitMillis(fps)
            if (wait <= 0L) {
                choreographer.postFrameCallback(frameCallback)
            } else {
                choreographer.postFrameCallbackDelayed(frameCallback, wait)
            }
        }

        // One frame, just before the screen is drawn
        private fun onFrame(frameTimeNanos: Long) {
            if (!framing) return

            // The view may have been hidden, or faded out, or have got nothing to move since the last frame
            val plan = currentPlan()
            if (plan != EffectsRefresh.EVERY_FRAME) {
                // This callback is already used up, so there is nothing to remove
                framing = false
                applyRefresh(plan)
                return
            }

            val elapsed = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
            if (lastFrameNanos != 0L && !FramePacing.shouldDraw(elapsed, fps)) {
                // Too early for the frame rate which was picked, such as on a display which is faster
                postNextFrame()
                return
            }
            lastFrameNanos = frameTimeNanos

            advance(FramePacing.stepMillis(elapsed), frameTimeNanos / NANOS_PER_MILLI)
            invalidate()

            if (mix.isSettled(targets)) {
                // The picture which was just drawn is the final one, so there is nothing more to draw
                framing = false
            } else {
                postNextFrame()
            }
        }

        private fun startWatchingFadeIn() {
            if (watchingFadeIn) return
            watchingFadeIn = true
            viewTreeObserver.addOnPreDrawListener(fadeInWatcher)
        }

        private fun stopWatchingFadeIn() {
            if (!watchingFadeIn) return
            watchingFadeIn = false
            // The observer of a window which is gone is dead, and the listener went with it
            val observer = viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(fadeInWatcher)
        }

        // Moves everything on by the time which has passed
        private fun advance(
            dtMillis: Float,
            nowMillis: Long,
        ) {
            mix.step(dtMillis, targets)
            if (width <= 0 || height <= 0) return

            val seconds = dtMillis / MILLIS_PER_SECOND
            stepRain(seconds)
            stepSnow(seconds)
            stepFog(seconds)

            // Lightning only strikes while the sky can be seen
            flash = lightning.brightness(nowMillis, targets.lightning && mix.gate > 0f)
        }

        private fun stepRain(seconds: Float) {
            val wanted = mix.rain.presence > 0f
            val layers = rainLayers ?: (if (wanted) createRainLayers() else return)

            val level = mix.rain.level
            val tilt = WindMath.rainTilt(mix.wind)
            val total = if (wanted) EffectBudget.rainCount(level, quality) else 0
            for (i in layers.indices) {
                val layer = layers[i]
                layer.step(seconds, tilt, level, EffectBudget.rainLayerCount(total, i))
                layer.writeLines(tilt, level)
            }
        }

        private fun stepSnow(seconds: Float) {
            val wanted = mix.snow.presence > 0f
            val layers = snowLayers ?: (if (wanted) createSnowLayers() else return)
            if (wanted && snowSprites == null) snowSprites = createSnowSprites(layers)

            val total = if (wanted) EffectBudget.snowCount(mix.snow.level, quality) else 0
            for (i in layers.indices) {
                layers[i].step(seconds, mix.wind, mix.snow.level, EffectBudget.snowLayerCount(total, i))
            }
        }

        private fun stepFog(seconds: Float) {
            if (mix.fog.presence <= 0f) return
            // The noise is made when the fog is first wanted, which can be when other weather is on the screen
            loadFogIfNeeded()
            updateFogLight()
            for (i in 0 until FogLayers.COUNT) {
                val wind = mix.wind * FogLayers.WIND_SPEED
                fogX[i] = FogLayers.drift(fogX[i], FogLayers.SPEED_X[i] + wind, FogLayers.ZOOM[i], seconds)
                fogY[i] = FogLayers.drift(fogY[i], FogLayers.SPEED_Y[i], FogLayers.ZOOM[i], seconds)
            }
        }

        // The fog is darker by night. The filter which does that is made again only when the night
        // has moved a good bit, so there are a few dozen of them in a fade, and none the rest of the time
        private fun updateFogLight() {
            val step = (mix.night * FOG_LIGHT_STEPS).toInt()
            if (step == fogLightStep) return
            fogLightStep = step
            val filter = LightingColorFilter(EffectPalette.fogLight(step / FOG_LIGHT_STEPS), 0)
            for (paint in fogPaints) paint.colorFilter = filter
        }

        // Making what is needed

        private fun createRainLayers(): Array<RainLayer> =
            Array(RainProfiles.ALL.size) { i ->
                RainLayer(EffectBudget.rainCapacity(i), RainProfiles.ALL[i], random).also { it.resize(width.toFloat(), height.toFloat()) }
            }.also { rainLayers = it }

        private fun createSnowLayers(): Array<SnowLayer> =
            Array(SnowProfiles.ALL.size) { i ->
                SnowLayer(EffectBudget.snowCapacity(i), SnowProfiles.ALL[i], random).also { it.resize(width.toFloat(), height.toFloat()) }
            }.also { snowLayers = it }

        // A soft round flake for each depth layer, as big as its biggest flake: white in the middle
        // and clear at the edge. Flakes are drawn smaller than that, never bigger
        private fun createSnowSprites(layers: Array<SnowLayer>): Array<Bitmap> =
            Array(layers.size) { i ->
                val diameter = max(MIN_SPRITE_PX, ceil(layers[i].biggestDiameterPx).toInt())
                val sprite = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
                val radius = diameter / 2f
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader =
                            RadialGradient(
                                radius,
                                radius,
                                radius,
                                intArrayOf(FLAKE_CENTRE, FLAKE_BODY, FLAKE_EDGE),
                                floatArrayOf(0f, SnowSprite.BODY_END, 1f),
                                Shader.TileMode.CLAMP,
                            )
                    }
                Canvas(sprite).drawCircle(radius, radius, radius, paint)
                sprite
            }

        // The noise is made on another thread, as it takes some tens of milliseconds, and then handed
        // over to this one. Until it is there the fog is only the veil. A canvas which is not
        // hardware accelerated is too slow to fill the screen with textures, so it has the veil alone.
        private fun loadFogIfNeeded() {
            val wanted = mix.fog.presence > 0f || targets.fog > 0f
            if (!wanted || fogBitmaps != null || fogLoading || released || !isHardwareAccelerated) return

            fogLoading = true
            thread(name = "weather-fog", isDaemon = true, priority = Thread.MIN_PRIORITY) {
                val bitmaps =
                    try {
                        Array(FogLayers.COUNT) { i ->
                            val pixels =
                                FogNoise.texturePixels(
                                    FogLayers.SEEDS[i],
                                    EffectPalette.FOG_RED,
                                    EffectPalette.FOG_GREEN,
                                    EffectPalette.FOG_BLUE,
                                )
                            Bitmap.createBitmap(pixels, FogNoise.TEXTURE_SIZE, FogNoise.TEXTURE_SIZE, Bitmap.Config.ARGB_8888)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "WeatherEffects: Could not make the fog")
                        null
                    } catch (e: OutOfMemoryError) {
                        Timber.e(e, "WeatherEffects: No memory for the fog")
                        null
                    }
                post { onFogReady(bitmaps) }
            }
        }

        private fun onFogReady(bitmaps: Array<Bitmap>?) {
            fogLoading = false
            if (released || bitmaps == null) return

            fogBitmaps = bitmaps
            for (i in 0 until FogLayers.COUNT) {
                fogPaints[i].shader = BitmapShader(bitmaps[i], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            invalidate()
        }

        private companion object {
            const val NO_STEP = -1
            const val NANOS_PER_MILLI = 1_000_000L
            const val MILLIS_PER_SECOND = 1_000f

            // Lighter rain is fainter: a base and a share of the intensity
            const val RAIN_ALPHA_BASE = 0.55f
            const val RAIN_ALPHA_SHARE = 0.45f

            // A flash is as bright as this much of the most, with the lightest rain, and the most with the heaviest
            const val FLASH_BASE = 0.6f
            const val FLASH_SHARE = 0.4f

            // The night fades the fog's light in this many steps
            const val FOG_LIGHT_STEPS = 32f

            // A flake: solid in the middle, softer towards the edge and clear at the very edge, see SnowSprite
            const val MIN_SPRITE_PX = 4
            val FLAKE_CENTRE = EffectMath.argb(SnowSprite.CENTRE_ALPHA, 255, 255, 255)
            val FLAKE_BODY = EffectMath.argb(SnowSprite.BODY_ALPHA, 255, 255, 255)
            val FLAKE_EDGE = EffectMath.argb(0, 255, 255, 255)
        }
    }
