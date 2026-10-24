package com.neilturner.aerialviews.ui.effects

import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/**
 * The shape of one flash of lightning: it lights up almost at once, and fades away. A double flash
 * is a second, weaker one a moment after the first.
 */
object LightningFlash {
    // A flash lights up in this time, and then fades with this time constant, to a hundredth in about half a second
    private const val ATTACK_MILLIS = 25f
    private const val DECAY_MILLIS = 110f

    // The second flash of a double flash is weaker, and a little quicker
    const val SECOND_FLASH_PEAK = 0.7f
    private const val SECOND_ATTACK_MILLIS = 20f
    private const val SECOND_DECAY_MILLIS = 90f

    // Below this a flash is over. It is invisible at that point, so nothing jumps when it is cut off
    private const val GONE = 0.01f

    /** How long a single flash takes to be over, with a margin: it is under a hundredth after about 530 ms */
    const val SINGLE_MILLIS = 540L

    /** The time after the start of a second flash it takes to be over, with a margin */
    private const val SECOND_MILLIS = 440L

    /**
     * How bright a flash is [sinceMillis] after it started, from 0 to 1. It is 0 before it starts
     * and after it is over.
     */
    fun flash(
        sinceMillis: Float,
        attackMillis: Float = ATTACK_MILLIS,
        decayMillis: Float = DECAY_MILLIS,
    ): Float {
        if (!(sinceMillis > 0f)) return 0f
        if (sinceMillis < attackMillis) return sinceMillis / attackMillis
        val level = exp(-(sinceMillis - attackMillis) / decayMillis)
        return if (level < GONE) 0f else level
    }

    /**
     * How bright a strike is [sinceMillis] after it started: the first flash and, for a double
     * flash, the second one [secondDelayMillis] later, whichever is brighter.
     */
    fun level(
        sinceMillis: Long,
        doubleFlash: Boolean,
        secondDelayMillis: Long,
    ): Float {
        val first = flash(sinceMillis.toFloat())
        if (!doubleFlash) return first
        val second = SECOND_FLASH_PEAK * flash((sinceMillis - secondDelayMillis).toFloat(), SECOND_ATTACK_MILLIS, SECOND_DECAY_MILLIS)
        return max(first, second)
    }

    /** How long a strike takes from its start to be over */
    fun durationMillis(
        doubleFlash: Boolean,
        secondDelayMillis: Long,
    ): Long = if (doubleFlash) max(SINGLE_MILLIS, secondDelayMillis + SECOND_MILLIS) else SINGLE_MILLIS
}

/**
 * Decides when the lightning strikes, for a storm. A strike comes at random, between 8 and 25
 * seconds after the one before, and never within 4 seconds of it. Some strikes are a double flash.
 * Free of android classes, and made from a [random] which is handed in, so it can be tested.
 *
 * The time is whatever clock is handed to [brightness], in milliseconds, which must not go back.
 * A strike starts at the moment [brightness] sees that it is due, so if the screen was not drawn for
 * a while (it was hidden, or the device stalled) the strike is not made up for with a flash that is
 * late: the next strike is simply put off, from now.
 */
class LightningScheduler(
    private val random: Random,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val maxIntervalMillis: Long = MAX_INTERVAL_MILLIS,
    private val minGapMillis: Long = MIN_GAP_MILLIS,
    private val doubleFlashChance: Float = DOUBLE_FLASH_CHANCE,
) {
    private var nextStrikeAt = NOT_SCHEDULED
    private var lastStrikeAt = NEVER

    // The strike which is on: when it started, and what it looks like
    private var strikeAt = NEVER
    private var strikeIsDouble = false
    private var secondFlashDelay = 0L
    private var strikePeak = 1f

    /** How many strikes there have been */
    var strikes = 0
        private set

    /**
     * How bright the sky is at [nowMillis] because of lightning, from 0 to 1.
     *
     * @param enabled whether the storm is on. When it is not, no new strike is planned, but a strike
     * which has started goes on until it is over
     */
    fun brightness(
        nowMillis: Long,
        enabled: Boolean,
    ): Float {
        if (enabled) {
            plan(nowMillis)
        } else {
            nextStrikeAt = NOT_SCHEDULED
        }

        if (strikeAt == NEVER) return 0f
        val since = nowMillis - strikeAt
        if (since > LightningFlash.durationMillis(strikeIsDouble, secondFlashDelay)) {
            strikeAt = NEVER
            return 0f
        }
        return LightningFlash.level(since, strikeIsDouble, secondFlashDelay) * strikePeak
    }

    private fun plan(nowMillis: Long) {
        when {
            nextStrikeAt == NOT_SCHEDULED -> {
                // The first strike of a storm comes sooner than the others
                nextStrikeAt = max(nowMillis + interval() / 2, lastStrikeAt + minGapMillis)
            }

            nowMillis < nextStrikeAt -> {
                // Not yet
            }

            nowMillis - nextStrikeAt > STALE_MILLIS -> {
                // It was due long ago, while nothing was looking
                nextStrikeAt = max(nowMillis + interval(), lastStrikeAt + minGapMillis)
            }

            else -> {
                startStrike(nowMillis)
            }
        }
    }

    private fun startStrike(nowMillis: Long) {
        strikeAt = nowMillis
        lastStrikeAt = nowMillis
        strikes++
        strikeIsDouble = random.nextFloat() < doubleFlashChance
        secondFlashDelay = SECOND_FLASH_DELAY_MIN_MILLIS + (random.nextFloat() * SECOND_FLASH_DELAY_RANGE_MILLIS).toLong()
        strikePeak = PEAK_MIN + random.nextFloat() * (1f - PEAK_MIN)
        nextStrikeAt = nowMillis + max(interval(), minGapMillis)
    }

    private fun interval(): Long = minIntervalMillis + (random.nextFloat() * (maxIntervalMillis - minIntervalMillis)).toLong()

    companion object {
        const val MIN_INTERVAL_MILLIS = 8_000L
        const val MAX_INTERVAL_MILLIS = 25_000L

        /** No two strikes are closer than this, whatever the intervals are */
        const val MIN_GAP_MILLIS = 4_000L
        const val DOUBLE_FLASH_CHANCE = 0.35f

        // The second flash of a double flash comes this long after the first, in milliseconds
        private const val SECOND_FLASH_DELAY_MIN_MILLIS = 130L
        private const val SECOND_FLASH_DELAY_RANGE_MILLIS = 130f

        // Strikes are between this and the full brightness
        private const val PEAK_MIN = 0.75f

        // A strike which is later than this was missed, as the screen was not being drawn
        private const val STALE_MILLIS = 1_000L

        private const val NOT_SCHEDULED = Long.MIN_VALUE
        private const val NEVER = Long.MIN_VALUE / 4
    }
}
