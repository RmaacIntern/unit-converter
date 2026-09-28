package com.aivigil.unitconverter

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aivigil.unitconverter.ads.AdsController
import com.aivigil.unitconverter.ads.BannerAdSlot
import com.aivigil.unitconverter.ui.main.MainRoute
import com.aivigil.unitconverter.ui.settings.SettingsRoute
import com.aivigil.unitconverter.ui.theme.NeonBackground
import com.aivigil.unitconverter.ui.theme.UnitConverterTheme

/**
 * The single Activity displaying the Dark Neon UI and managing Settings/Ads integration.
 */
class MainActivity : ComponentActivity() {

    private val adsController: AdsController by lazy { AdsController.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // UMP runs first for consent handling
        adsController.gatherConsent(this)

        setContent {
            UnitConverterTheme {
                val bannerState by adsController.bannerState.collectAsStateWithLifecycle()
                val privacyOptionsRequired by adsController.privacyOptionsRequired.collectAsStateWithLifecycle()
                var destination by rememberSaveable { mutableStateOf(Destination.Main) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NeonBackground
                ) {
                    when (destination) {
                        Destination.Main -> MainRoute(
                            onOpenSettings = { destination = Destination.Settings },
                            onConversionCompleted = { adsController.onConversionCompleted(this@MainActivity) },
                            onUnitChanged = { adsController.onUnitChanged(this@MainActivity) },
                            onSplashEnter = { onDone -> adsController.onSplashEnter(this@MainActivity, onDone) },
                            onBackPressed = { onDone -> adsController.onBackPressed(this@MainActivity, onDone) },
                            onExitApp = { finish() },
                            bannerSlot = { BannerAdSlot(state = bannerState, controller = adsController) },
                        )

                        Destination.Settings -> {
                            BackHandler { destination = Destination.Main }
                            SettingsRoute(
                                onBack = { destination = Destination.Main },
                                privacyOptionsRequired = privacyOptionsRequired,
                                onOpenPrivacyOptions = { adsController.showPrivacyOptionsForm(this@MainActivity) },
                                bannerSlot = { BannerAdSlot(state = bannerState, controller = adsController) },
                            )
                        }
                    }
                }
            }
        }
    }

    private enum class Destination { Main, Settings }
}