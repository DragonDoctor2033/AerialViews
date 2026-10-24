package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.WeatherEffectKind
import com.neilturner.aerialviews.models.enums.WeatherEffectsIntensity
import com.neilturner.aerialviews.services.weather.WeatherEffectState

/**
 * What the weather effects are moving towards: how much of each layer there should be, where 0
 * means none of it. Made from the weather by [from], and handed to the view, which fades the
 * picture there with an [EffectMix].
 *
 * @param rain how heavy the rain is, from 0 to 1. A storm has rain, and lightning
 * @param snow how heavy the snow is, from 0 to 1
 * @param fog how thick the fog is, from 0 to 1
 * @param tint how strong the grey-blue tone over the footage is, from 0 to 1. Rain, snow and fog
 * come with some of it, as the sky is grey then, and clouds have nothing else
 * @param lightning whether there are flashes of lightning
 * @param night the footage is dark, so the effects are dimmed
 * @param wind how far the wind pushes things sideways, from -1 (left) to 1 (right), see [WindMath]
 * @param shown whether the effects are to be seen. They are not while a clip is loading, in
 * blackout, or on a clip which shows its own weather
 */
data class EffectTargets(
    val rain: Float = 0f,
    val snow: Float = 0f,
    val fog: Float = 0f,
    val tint: Float = 0f,
    val lightning: Boolean = false,
    val night: Boolean = false,
    val wind: Float = 0f,
    val shown: Boolean = false,
) {
    companion object {
        /**
         * @param intensity the strength the viewer picked in the settings, which scales the intensity
         * of the weather. The result is kept between 0 and 1
         */
        fun from(
            state: WeatherEffectState,
            intensity: WeatherEffectsIntensity,
            shown: Boolean,
        ): EffectTargets {
            val level = (state.intensity * intensity.scale).coerceIn(0f, 1f)
            if (state.kind == WeatherEffectKind.NONE || level <= 0f) {
                return EffectTargets(night = state.isNight, shown = shown)
            }

            val wind = WindMath.lateralFactor(state.windSpeedMetersPerSecond, state.windFromDegrees)
            val targets = EffectTargets(night = state.isNight, wind = wind, shown = shown)
            return when (state.kind) {
                WeatherEffectKind.RAIN -> {
                    targets.copy(rain = level, tint = tone(RAIN_TONE_BASE, RAIN_TONE_SHARE, level))
                }

                WeatherEffectKind.SNOW -> {
                    targets.copy(snow = level, tint = tone(SNOW_TONE_BASE, SNOW_TONE_SHARE, level))
                }

                WeatherEffectKind.FOG -> {
                    targets.copy(fog = level, tint = tone(0f, FOG_TONE_SHARE, level))
                }

                WeatherEffectKind.STORM -> {
                    targets.copy(
                        rain = level,
                        tint = tone(STORM_TONE_BASE, STORM_TONE_SHARE, level),
                        lightning = true,
                    )
                }

                WeatherEffectKind.OVERCAST -> {
                    targets.copy(tint = level)
                }

                WeatherEffectKind.NONE -> {
                    targets
                }
            }
        }

        // A grey sky comes with the rain, a little less with snow, and the darkest with a storm.
        // The tone is a base and a share of the intensity, kept between 0 and 1
        private const val RAIN_TONE_BASE = 0.30f
        private const val RAIN_TONE_SHARE = 0.45f
        private const val SNOW_TONE_BASE = 0.15f
        private const val SNOW_TONE_SHARE = 0.30f
        private const val STORM_TONE_BASE = 0.55f
        private const val STORM_TONE_SHARE = 0.40f
        private const val FOG_TONE_SHARE = 0.25f

        private fun tone(
            base: Float,
            share: Float,
            level: Float,
        ): Float = (base + share * level).coerceIn(0f, 1f)
    }
}

/**
 * One layer of the weather effects while it fades: how much of it is there ([presence], from 0
 * to 1, which is what fades in and out) and how strong it is ([level], from 0 to 1, which is the
 * intensity of the weather and moves on its own, without fading the layer).
 */
class EffectFade {
    var presence = 0f
        private set
    var level = 0f
        private set

    /** The presence as the opacity to draw with, eased so that it starts and stops softly */
    val opacity: Float get() = EffectMath.smoothstep(presence)

    /** Fades towards the [target] level, where 0 fades the layer out */
    fun step(
        dtMillis: Float,
        target: Float,
    ) {
        if (target > 0f) {
            // A layer which can't be seen yet doesn't have to move to its level, it just starts there
            level =
                if (presence <= 0f) {
                    target
                } else {
                    CrossfadeMath.approach(level, target, dtMillis, CrossfadeMath.FADE_MILLIS)
                }
            presence = CrossfadeMath.approach(presence, 1f, dtMillis, CrossfadeMath.FADE_MILLIS)
        } else {
            presence = CrossfadeMath.approach(presence, 0f, dtMillis, CrossfadeMath.FADE_MILLIS)
        }
    }

    fun snapTo(target: Float) {
        presence = if (target > 0f) 1f else 0f
        level = target.coerceAtLeast(0f)
    }

    fun isAt(target: Float): Boolean =
        if (target > 0f) {
            CrossfadeMath.isNear(presence, 1f) && CrossfadeMath.isNear(level, target)
        } else {
            presence <= 0f
        }
}

/**
 * What the weather effects look like at the moment, on its way to the [EffectTargets]. Every frame
 * [step] moves it on by the time which has passed:
 *
 * - Layers which are no longer wanted fade out while the ones which are wanted fade in, over
 *   about two seconds, so a change of the weather is a crossfade and nothing pops.
 * - The gate is a fade of everything together. It opens slowly when a clip starts, and closes
 *   quickly when the clip changes, or on a clip with weather of its own, or in blackout.
 * - While the gate is closed nothing can be seen, so the layers get where they are going at once.
 *   That way the next time the gate opens only the gate fades, and not the layers of the old
 *   weather on top of it.
 */
class EffectMix {
    /** Everything together, from 0 to 1. Draw with [gateOpacity] */
    var gate = 0f
        private set

    val rain = EffectFade()
    val snow = EffectFade()
    val fog = EffectFade()
    val tint = EffectFade()

    /** 0 by day, 1 by night */
    var night = 0f
        private set

    /** How far the wind pushes things sideways, from -1 to 1 */
    var wind = 0f
        private set

    val gateOpacity: Float get() = EffectMath.smoothstep(gate)

    /** Closes the gate at once, for when nothing can be seen any more, such as in blackout. The next [step] gets the layers to their targets */
    fun closeGate() {
        gate = 0f
    }

    fun step(
        dtMillis: Float,
        targets: EffectTargets,
    ) {
        val gateTarget = if (targets.shown) 1f else 0f
        val gateMillis = if (gateTarget > gate) CrossfadeMath.FADE_MILLIS else CrossfadeMath.QUICK_FADE_OUT_MILLIS
        gate = CrossfadeMath.approach(gate, gateTarget, dtMillis, gateMillis)

        if (gate <= 0f && gateTarget <= 0f) {
            snapTo(targets)
            return
        }

        rain.step(dtMillis, targets.rain)
        snow.step(dtMillis, targets.snow)
        fog.step(dtMillis, targets.fog)
        tint.step(dtMillis, targets.tint)
        night = CrossfadeMath.approach(night, if (targets.night) 1f else 0f, dtMillis, CrossfadeMath.SLOW_FADE_MILLIS)
        wind = CrossfadeMath.approach(wind, targets.wind, dtMillis, CrossfadeMath.SLOW_FADE_MILLIS)
    }

    private fun snapTo(targets: EffectTargets) {
        rain.snapTo(targets.rain)
        snow.snapTo(targets.snow)
        fog.snapTo(targets.fog)
        tint.snapTo(targets.tint)
        night = if (targets.night) 1f else 0f
        wind = targets.wind
    }

    /**
     * Is the picture final? Then nothing has to be drawn again, and the frames can stop. That is so
     * when the gate is closed, or when the gate is open and nothing moves: a grey tone is still,
     * once it has faded in. Rain, snow, fog and lightning are never final while they are there.
     */
    fun isSettled(targets: EffectTargets): Boolean {
        val gateTarget = if (targets.shown) 1f else 0f
        if (!CrossfadeMath.isNear(gate, gateTarget)) return false
        if (gate <= 0f) return true

        val somethingMoves =
            targets.lightning ||
                rain.presence > 0f || targets.rain > 0f ||
                snow.presence > 0f || targets.snow > 0f ||
                fog.presence > 0f || targets.fog > 0f
        if (somethingMoves) return false

        return tint.isAt(targets.tint) && CrossfadeMath.isNear(night, if (targets.night) 1f else 0f)
    }
}
