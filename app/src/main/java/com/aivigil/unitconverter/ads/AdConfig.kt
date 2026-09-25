package com.aivigil.unitconverter.ads

/**
 * Central place for test ad unit IDs and Remote Config keys.
 * Replace test IDs with real ones only after Gate 6 / production readiness.
 */
object AdConfig {
    // Google official test units — safe for development
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Remote Config keys
    const val KEY_BANNER_ENABLED = "banner_enabled"
    const val KEY_INTERSTITIAL_ENABLED = "interstitial_enabled"
    const val KEY_INTERSTITIAL_DAILY_CAP = "interstitial_daily_cap"

    // Safe defaults used before Remote Config finishes loading
    const val DEFAULT_BANNER_ENABLED = true
    const val DEFAULT_INTERSTITIAL_ENABLED = true
    const val DEFAULT_INTERSTITIAL_DAILY_CAP = 4
}
