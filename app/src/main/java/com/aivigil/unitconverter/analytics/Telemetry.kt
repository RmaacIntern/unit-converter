package com.aivigil.unitconverter.analytics

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Analytics wrapper. Every call is a no-op until google-services.json is added
 * (Gate 6), so a fresh clone runs without Firebase.
 */
object Telemetry {

    private const val MICROS_PER_UNIT = 1_000_000.0
    private const val PARAM_PRECISION = "precision_type"

    fun isFirebaseReady(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Impression-level ad revenue, called from OnPaidEventListener on each ad object. */
    fun logAdImpression(
        context: Context,
        adFormat: String,
        adUnitId: String,
        adValue: AdValue,
        adSourceName: String?,
    ) {
        if (!isFirebaseReady(context)) return
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
            putString(FirebaseAnalytics.Param.AD_FORMAT, adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            adSourceName?.let { putString(FirebaseAnalytics.Param.AD_SOURCE, it) }
            putDouble(FirebaseAnalytics.Param.VALUE, adValue.valueMicros / MICROS_PER_UNIT)
            putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode)
            putLong(PARAM_PRECISION, adValue.precisionType.toLong())
        }
        FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
    }
}
