package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel

/**
 * Settings for the digital clock overlay which are not part of [GeneralPrefs]. They live in the
 * same preferences file as the other overlay settings, so the settings screen
 * (settings_overlays_clock.xml) can read and write them through the same keys.
 */
object ClockPrefs : KotprefModel() {
    const val KEY_SHOW_SECONDS = "clock_show_seconds"

    override val kotprefName = "${context.packageName}_preferences"

    // Shows 14:05:09 instead of 14:05
    var showSeconds by booleanPref(false, KEY_SHOW_SECONDS)
}
