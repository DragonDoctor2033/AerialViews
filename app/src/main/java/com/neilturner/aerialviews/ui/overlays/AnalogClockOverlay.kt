package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withTranslation
import com.neilturner.aerialviews.models.enums.AnalogClockSize
import com.neilturner.aerialviews.ui.overlays.utils.AnalogClockGeometry
import com.neilturner.aerialviews.ui.overlays.utils.AnalogClockMath
import com.neilturner.aerialviews.ui.overlays.utils.ClockHandAngles
import java.time.LocalTime
import kotlin.math.min

/**
 * Analog clock overlay, drawn on the canvas with no bitmaps: 12 tick marks (longer and thicker at
 * 12, 3, 6 and 9), thin hour and minute hands with rounded ends, a thin red second hand and a small
 * centre dot, on a transparent background.
 *
 * Like the other overlays it is a plain view that [OverlayHelper][com.neilturner.aerialviews.ui.helpers.OverlayHelper]
 * places in a corner slot, so the screen controller's fade, auto-hide and alternate position
 * handling (alpha and Flow constraints) work on it without any special cases. Opacity comes from
 * the view's alpha, the overlay colour from [configure].
 *
 * The clock only keeps time while it is attached to a window and actually shown: it redraws once
 * a second, on the second boundary, and stops as soon as it is detached or hidden (for example
 * when the screensaver goes to blackout).
 */
class AnalogClockOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val density = resources.displayMetrics.density
        private val shadowOffset = SHADOW_OFFSET_DP * density

        // Keeps the offset shadow and the anti-aliased edges inside the view bounds
        private val edgeInset = shadowOffset + 1f

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var diameterPx = AnalogClockGeometry.diameterPx(AnalogClockSize.DEFAULT, density)
        private var showSecondHand = true
        private var colour = Color.WHITE

        private var centreX = 0f
        private var centreY = 0f
        private var faceRadius = 0f
        private var sizes = AnalogClockGeometry.forRadius(faceRadius)

        // The time shown, refreshed by the tick. Drawing never reads the clock itself, so a
        // redraw caused by something else (such as a fade) can't nudge the second hand between ticks
        private var angles = ClockHandAngles(0f, 0f, 0f)

        private var ticking = false
        private val tick =
            object : Runnable {
                override fun run() {
                    if (!ticking) return
                    val millis = refreshAngles()
                    // Nothing to see while faded out by auto-hide, the fade in redraws it
                    if (alpha > 0f) invalidate()
                    postDelayed(this, AnalogClockMath.millisUntilNextSecond(millis))
                }
            }

        init {
            refreshAngles()
        }

        /**
         * @param size face size, scaled by the screen density
         * @param showSecondHand the red second hand can be turned off in the settings
         * @param colour overlay colour for the ticks, hour and minute hands and the centre dot.
         * The second hand always stays red
         */
        fun configure(
            size: AnalogClockSize,
            showSecondHand: Boolean,
            colour: Int,
        ) {
            val newDiameter = AnalogClockGeometry.diameterPx(size, density)
            if (newDiameter != diameterPx) {
                diameterPx = newDiameter
                requestLayout()
            }
            this.showSecondHand = showSecondHand
            this.colour = colour
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateTicking()
        }

        override fun onDetachedFromWindow() {
            stopTicking()
            super.onDetachedFromWindow()
        }

        // Also called when an ancestor changes, such as the overlay container being hidden
        override fun onVisibilityChanged(
            changedView: View,
            visibility: Int,
        ) {
            super.onVisibilityChanged(changedView, visibility)
            updateTicking()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            updateTicking()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(
                resolveSize(diameterPx, widthMeasureSpec),
                resolveSize(diameterPx, heightMeasureSpec),
            )
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            centreX = w / 2f
            centreY = h / 2f
            faceRadius = (min(w, h) / 2f - edgeInset).coerceAtLeast(0f)
            sizes = AnalogClockGeometry.forRadius(faceRadius)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (faceRadius <= 0f) return

            // A soft shadow, as behind the text overlays, keeps the thin lines readable on bright footage
            canvas.withTranslation(shadowOffset, shadowOffset) {
                drawFace(this, SHADOW_COLOUR, SHADOW_COLOUR)
            }
            drawFace(canvas, colour, SECOND_HAND_COLOUR)
        }

        private fun drawFace(
            canvas: Canvas,
            mainColour: Int,
            secondHandColour: Int,
        ) {
            paint.style = Paint.Style.STROKE
            paint.color = mainColour

            paint.strokeCap = Paint.Cap.BUTT
            for (index in 0 until AnalogClockGeometry.TICK_COUNT) {
                drawTick(canvas, index)
            }

            paint.strokeCap = Paint.Cap.ROUND
            drawHand(canvas, angles.hour, sizes.hourHandLength, sizes.hourHandWidth)
            drawHand(canvas, angles.minute, sizes.minuteHandLength, sizes.minuteHandWidth)
            if (showSecondHand) {
                paint.color = secondHandColour
                drawHand(canvas, angles.second, sizes.secondHandLength, sizes.secondHandWidth)
                paint.color = mainColour
            }

            // On top of the hands, where they meet
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centreX, centreY, sizes.centreDotRadius, paint)
        }

        private fun drawTick(
            canvas: Canvas,
            index: Int,
        ) {
            val quarter = AnalogClockGeometry.isQuarterTick(index)
            val length = if (quarter) sizes.quarterTickLength else sizes.regularTickLength
            val angle = AnalogClockGeometry.tickAngle(index)
            val inner = faceRadius - length

            paint.strokeWidth = if (quarter) sizes.quarterTickWidth else sizes.regularTickWidth
            canvas.drawLine(
                centreX + AnalogClockMath.offsetX(angle, inner),
                centreY + AnalogClockMath.offsetY(angle, inner),
                centreX + AnalogClockMath.offsetX(angle, faceRadius),
                centreY + AnalogClockMath.offsetY(angle, faceRadius),
                paint,
            )
        }

        private fun drawHand(
            canvas: Canvas,
            degrees: Float,
            length: Float,
            width: Float,
        ) {
            paint.strokeWidth = width
            canvas.drawLine(
                centreX,
                centreY,
                centreX + AnalogClockMath.offsetX(degrees, length),
                centreY + AnalogClockMath.offsetY(degrees, length),
                paint,
            )
        }

        // Reads the wall clock and returns how many milliseconds into the current second it is
        private fun refreshAngles(): Int {
            val now = LocalTime.now()
            val millis = now.nano / NANOS_PER_MILLI
            angles = AnalogClockMath.handAngles(now.hour, now.minute, now.second, millis)
            return millis
        }

        private fun updateTicking() {
            if (isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
                startTicking()
            } else {
                stopTicking()
            }
        }

        private fun startTicking() {
            if (ticking) return
            ticking = true
            tick.run()
        }

        private fun stopTicking() {
            if (!ticking) return
            ticking = false
            removeCallbacks(tick)
        }

        private companion object {
            const val SHADOW_OFFSET_DP = 1f
            const val NANOS_PER_MILLI = 1_000_000
            const val SECOND_HAND_COLOUR = 0xFFFF3B30.toInt()
            val SHADOW_COLOUR = Color.argb(120, 0, 0, 0)
        }
    }
