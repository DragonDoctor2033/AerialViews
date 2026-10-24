package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.models.enums.AnalogClockCenterSize
import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import com.neilturner.aerialviews.models.enums.AnalogClockSecondHandMotion
import com.neilturner.aerialviews.models.enums.AnalogClockSize

/**
 * Settings for the analog clock overlay. They live in the same preferences file as the other
 * overlay settings, so the settings screen (settings_overlays_analog_clock.xml) can read and write
 * them through the same keys.
 */
object AnalogClockPrefs : KotprefModel() {
    const val KEY_SIZE = "analog_clock_size"
    const val KEY_SHOW_SECOND_HAND = "analog_clock_show_second_hand"
    const val KEY_SECOND_HAND_MOTION = "analog_clock_second_hand_motion"
    const val KEY_HALO = "analog_clock_halo"
    const val KEY_SHOW_IN_CENTER = "analog_clock_show_in_center"
    const val KEY_CENTER_SIZE = "analog_clock_center_size"

    override val kotprefName = "${context.packageName}_preferences"

    // Stored as the AnalogClockSize constant name, read it with AnalogClockSize.fromValue()
    var size by stringPref(AnalogClockSize.DEFAULT.name, KEY_SIZE)
    var showSecondHand by booleanPref(true, KEY_SHOW_SECOND_HAND)

    // Stored as the AnalogClockSecondHandMotion constant name, read it with AnalogClockSecondHandMotion.fromValue()
    var secondHandMotion by stringPref(AnalogClockSecondHandMotion.DEFAULT.name, KEY_SECOND_HAND_MOTION)

    // Stored as the AnalogClockHalo constant name, read it with AnalogClockHalo.fromValue()
    var halo by stringPref(AnalogClockHalo.DEFAULT.name, KEY_HALO)

    // One big clock in the middle of the screen, apart from any analog clock in a slot
    var showInCenter by booleanPref(false, KEY_SHOW_IN_CENTER)

    // Stored as the AnalogClockCenterSize constant name, read it with AnalogClockCenterSize.fromValue()
    var centerSize by stringPref(AnalogClockCenterSize.DEFAULT.name, KEY_CENTER_SIZE)
}
