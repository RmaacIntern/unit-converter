package com.aivigil.unitconverter.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aivigil.unitconverter.BuildConfig
import com.aivigil.unitconverter.analytics.Telemetry
import com.aivigil.unitconverter.data.AdPrefsStore
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** What the bottom bar should do. */
enum class BannerState {
    /** Consent declined or banner switched off: no slot, no space, no requests. */
    Hidden,

    /** Consent still resolving: hold the space so the layout doesn't jump when the ad arrives. */
    Reserved,

    /** Consent granted and MobileAds initialised: load and show. */
    Ready,
}

/**
 * Owns every ad decision (NEW-APP-RUNBOOK Gates 7–8):
 *
 * 1. UMP consent first. Nothing calls MobileAds or loads an ad until consent allows it.
 * 2. Declined consent means zero ad requests for the session. The app stays fully usable.
 * 3. Remote Config can tune or switch off ads, but only after fetchAndActivate() completes.
 *    Until then the synchronous defaults in [AdsConfig.DEFAULTS] apply.
 * 4. The interstitial runs only after a completed conversion or unit change.
 *
 * Process-wide singleton: it holds the preloaded interstitial across Activity recreation.
 */
class AdsController private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adPrefs = AdPrefsStore(appContext)
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(appContext)

    private val consentRequested = AtomicBoolean(false)
    private val mobileAdsInitStarted = AtomicBoolean(false)
    private var mobileAdsInitialized = false // main thread only

    private val availability = MutableStateFlow(AdsAvailability.Pending)

    private val config = MutableStateFlow(AdsConfig.DEFAULTS)

    private val firstSession: Deferred<Boolean> = scope.async { adPrefs.isFirstSession() }

    private var interstitial: InterstitialAd? = null // main thread only
    private var interstitialLoading = false
    private var interstitialShowing = false

    private var lastUnitChangeInterstitialAt = 0L

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** Show a "Privacy choices" entry in Settings when UMP says the user must be able to revisit consent. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    val bannerState: StateFlow<BannerState> =
        combine(availability, config) { ads, cfg ->
            when {
                !cfg.bannerEnabled || ads == AdsAvailability.Off -> BannerState.Hidden
                ads == AdsAvailability.Ready -> BannerState.Ready
                else -> BannerState.Reserved
            }
        }.stateIn(scope, SharingStarted.Eagerly, BannerState.Reserved)

    init {
        fetchRemoteConfig()
    }

    // -----------------------------------------------------------------------------------------
    // Consent
    // -----------------------------------------------------------------------------------------

    @MainThread
    fun gatherConsent(activity: Activity) {
        if (!consentRequested.compareAndSet(false, true)) return
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w(TAG, "Consent form error ${it.errorCode}: ${it.message}") }
                    onConsentResolved()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed ${requestError.errorCode}: ${requestError.message}")
                onConsentResolved()
            },
        )
        if (adsPermittedByConsent()) startMobileAds()
    }

    @MainThread
    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            formError?.let { Log.w(TAG, "Privacy options error ${it.errorCode}: ${it.message}") }
            onConsentResolved()
        }
    }

    @MainThread
    private fun onConsentResolved() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (adsPermittedByConsent()) startMobileAds() else turnAdsOff()
    }

    private fun adsPermittedByConsent(): Boolean =
        consentInformation.canRequestAds() && !TcfConsent.userDeclinedDeviceStorage(appContext)

    @MainThread
    private fun turnAdsOff() {
        availability.value = AdsAvailability.Off
        interstitial = null
    }

    @MainThread
    private fun startMobileAds() {
        if (mobileAdsInitialized) {
            availability.value = AdsAvailability.Ready
            preloadInterstitialIfEligible()
            return
        }
        if (!mobileAdsInitStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {
                scope.launch {
                    mobileAdsInitialized = true
                    if (adsPermittedByConsent()) {
                        availability.value = AdsAvailability.Ready
                        preloadInterstitialIfEligible()
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Remote Config
    // -----------------------------------------------------------------------------------------

    private fun fetchRemoteConfig() {
        if (!Telemetry.isFirebaseReady(appContext)) return
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0L else REMOTE_CONFIG_INTERVAL_S)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)
            .continueWithTask { remoteConfig.setDefaultsAsync(AdsConfig.DEFAULTS.toRemoteDefaults()) }
            .continueWithTask { remoteConfig.fetchAndActivate() }
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Remote Config fetch failed; keeping last activated values", task.exception)
                }
                config.value = AdsConfig.from(remoteConfig)
                onConfigActivated()
            }
    }

    @MainThread
    private fun onConfigActivated() {
        if (config.value.interstitialEnabled) {
            preloadInterstitialIfEligible()
        } else {
            interstitial = null
        }
    }

    // -----------------------------------------------------------------------------------------
    // Interstitial
    // -----------------------------------------------------------------------------------------

    @MainThread
    private fun preloadInterstitialIfEligible() {
        if (availability.value != AdsAvailability.Ready || !config.value.interstitialEnabled) return
        if (interstitial != null || interstitialLoading || interstitialShowing) return
        interstitialLoading = true
        scope.launch {
            val isFirst = if (BuildConfig.DEBUG) false else firstSession.await()
            val cap = if (BuildConfig.DEBUG) 100 else config.value.interstitialDailyCap
            val eligible = !isFirst && adPrefs.canShowInterstitial(cap)
            if (!eligible || availability.value != AdsAvailability.Ready) {
                interstitialLoading = false
                return@launch
            }
            InterstitialAd.load(
                appContext,
                BuildConfig.ADMOB_INTERSTITIAL_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialLoading = false
                        ad.setOnPaidEventListener { value ->
                            logPaidImpression(
                                FORMAT_INTERSTITIAL, ad.adUnitId, value,
                                ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                            )
                        }
                        interstitial = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialLoading = false
                        Log.i(TAG, "Interstitial not loaded (${error.code}): ${error.message}")
                    }
                },
            )
        }
    }

    @MainThread
    fun onUnitChanged(activity: ComponentActivity) {
        if (availability.value != AdsAvailability.Ready || interstitialShowing) return
        if (!config.value.interstitialEnabled) return

        val now = System.currentTimeMillis()
        val cooldown = if (BuildConfig.DEBUG) 0L else UNIT_CHANGE_COOLDOWN_MS
        if (now - lastUnitChangeInterstitialAt < cooldown) return

        val ad = interstitial
        if (ad == null) {
            preloadInterstitialIfEligible()
            return
        }

        scope.launch {
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (interstitial !== ad || interstitialShowing) return@launch
            interstitial = null
            interstitialShowing = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    lastUnitChangeInterstitialAt = System.currentTimeMillis()
                }

                override fun onAdDismissedFullScreenContent() {
                    interstitialShowing = false
                    preloadInterstitialIfEligible()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialShowing = false
                    Log.w(TAG, "Unit-change interstitial failed to show (${error.code}): ${error.message}")
                    preloadInterstitialIfEligible()
                }
            }
            ad.show(activity)
        }
    }

    @MainThread
    fun onConversionCompleted(activity: ComponentActivity) {
        // Interstitial ads after convert disabled per user instruction.
        // Interstitials appear only on clicking the 6 unit categories.
    }

    // -----------------------------------------------------------------------------------------
    // Banner
    // -----------------------------------------------------------------------------------------

    fun createBannerAdView(context: Context, adSize: AdSize): AdView = AdView(context).apply {
        setAdSize(adSize)
        adUnitId = BuildConfig.ADMOB_BANNER_ID
        setOnPaidEventListener { value ->
            logPaidImpression(
                FORMAT_BANNER, adUnitId, value,
                responseInfo?.loadedAdapterResponseInfo?.adSourceName,
            )
        }
        loadAd(AdRequest.Builder().build())
    }

    private fun logPaidImpression(format: String, adUnitId: String, value: AdValue, adSource: String?) {
        if (!config.value.logPaidImpressions) return
        Telemetry.logAdImpression(appContext, format, adUnitId, value, adSource)
    }

    companion object {
        private const val TAG = "AdsController"
        private const val REMOTE_CONFIG_INTERVAL_S = 3_600L
        private const val FORMAT_BANNER = "banner"
        private const val FORMAT_INTERSTITIAL = "interstitial"

        private const val UNIT_CHANGE_COOLDOWN_MS = 30_000L

        @Volatile
        private var instance: AdsController? = null

        fun getInstance(context: Context): AdsController =
            instance ?: synchronized(this) {
                instance ?: AdsController(context.applicationContext).also { instance = it }
            }
    }
}

private enum class AdsAvailability { Pending, Ready, Off }

internal data class AdsConfig(
    val bannerEnabled: Boolean,
    val interstitialEnabled: Boolean,
    val interstitialDailyCap: Int,
    val logPaidImpressions: Boolean,
) {
    fun toRemoteDefaults(): Map<String, Any> = mapOf(
        KEY_BANNER_ENABLED to bannerEnabled,
        KEY_INTERSTITIAL_ENABLED to interstitialEnabled,
        KEY_INTERSTITIAL_DAILY_CAP to interstitialDailyCap.toLong(),
        KEY_LOG_PAID_IMPRESSIONS to logPaidImpressions,
    )

    companion object {
        const val MAX_DAILY_CAP = 100
        const val KEY_BANNER_ENABLED = "banner_show"
        const val KEY_INTERSTITIAL_ENABLED = "interstitial_show"
        const val KEY_INTERSTITIAL_DAILY_CAP = "interstitial_daily_cap"
        const val KEY_LOG_PAID_IMPRESSIONS = "log_paid_ad_impressions"

        val DEFAULTS = AdsConfig(
            bannerEnabled = true,
            interstitialEnabled = true,
            interstitialDailyCap = MAX_DAILY_CAP,
            logPaidImpressions = true,
        )

        fun from(remoteConfig: FirebaseRemoteConfig) = AdsConfig(
            bannerEnabled = if (remoteConfig.all.containsKey("banner_show")) remoteConfig.getBoolean("banner_show") else true,
            interstitialEnabled = if (remoteConfig.all.containsKey("interstitial_show")) remoteConfig.getBoolean("interstitial_show") else true,
            interstitialDailyCap = MAX_DAILY_CAP,
            logPaidImpressions = true,
        )
    }
}

internal object TcfConsent {
    private const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
    private const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"

    fun userDeclinedDeviceStorage(context: Context): Boolean {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val gdprApplies = runCatching { prefs.getInt(KEY_GDPR_APPLIES, 0) }.getOrDefault(0) == 1
        if (!gdprApplies) return false
        val purposeConsents = runCatching { prefs.getString(KEY_PURPOSE_CONSENTS, null) }.getOrNull()
        return purposeConsents?.firstOrNull() != '1'
    }
}
