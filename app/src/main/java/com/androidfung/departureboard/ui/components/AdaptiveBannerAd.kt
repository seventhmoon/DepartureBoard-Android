package com.androidfung.departureboard.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import com.androidfung.departureboard.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// Test banner ID provided by Google for development / debug builds
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
// Production Banner Ad Unit ID
private const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-9890667024752973/3028288826"

/**
 * Adaptive Banner Ad composable using Google Mobile Ads SDK.
 * Automatically computes current orientation anchored adaptive banner size
 * and uses test ads in debug builds to prevent AdMob policy violations.
 */
@Composable
fun AdaptiveBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else PROD_BANNER_AD_UNIT_ID,
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val containerWidthDp = with(density) { windowInfo.containerSize.width.toDp().value.toInt() }

    // Calculate anchored adaptive banner ad size based on current container width
    val adSize = remember(containerWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, containerWidthDp)
    }

    val adView = remember(adUnitId, adSize) {
        createAdView(context, adUnitId, adSize)
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun createAdView(context: Context, adUnitId: String, adSize: AdSize): AdView {
    return AdView(context).apply {
        setAdSize(adSize)
        this.adUnitId = adUnitId
        loadAd(AdRequest.Builder().build())
    }
}
