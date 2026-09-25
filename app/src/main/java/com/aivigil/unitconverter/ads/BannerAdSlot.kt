package com.aivigil.unitconverter.ads

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
import com.aivigil.unitconverter.ui.theme.NeonSurface
import com.google.android.gms.ads.AdSize

private const val MIN_BANNER_HEIGHT_DP = 50

@Composable
fun BannerAdSlot(
    state: BannerState,
    controller: AdsController,
    modifier: Modifier = Modifier,
) {
    if (state == BannerState.Hidden) return

    Surface(
        color = NeonSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val context = LocalContext.current
            val widthDp = maxWidth.value.toInt()
            val adSize = remember(widthDp) {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            }
            val slotHeight = maxOf(adSize.getHeight(), MIN_BANNER_HEIGHT_DP).dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight),
                contentAlignment = Alignment.Center,
            ) {
                if (state == BannerState.Ready) {
                    key(adSize) {
                        BannerAdView(controller = controller, adSize = adSize)
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerAdView(controller: AdsController, adSize: AdSize) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adView = remember { controller.createBannerAdView(context, adSize) }

    DisposableEffect(adView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE  -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
}