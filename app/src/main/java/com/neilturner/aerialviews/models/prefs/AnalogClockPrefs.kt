package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import com.neilturner.aerialviews.models.enums.AnalogClockSize

/**
 * Settings for the analog clock overlay. They live in the same preferences file as the other
 * overlay settings, so the settings screen (settings_overlays_analog_clock.xml) can read and write
 * them through the same keys.
 */
object AnalogClockPrefs : KotprefModel() {
    const val KEY_SIZE = "analog_clock_size"
    const val KEY_SHOW_SECOND_HAND = "analog_clock_show_second_hand"
    const val KEY_HALO = "analog_clock_halo"

    override val kotprefName = "${context.packageName}_preferences"

    // Stored as the AnalogClockSize constant name, read it with AnalogClockSize.fromValue()
    var size by stringPref(AnalogClockSize.DEFAULT.name, KEY_SIZE)
    var showSecondHand by booleanPref(true, KEY_SHOW_SECOND_HAND)

    // Stored as the AnalogClockHalo constant name, read it with AnalogClockHalo.fromValue()
    var halo by stringPref(AnalogClockHalo.DEFAULT.name, KEY_HALO)
}
