package com.androidfung.departureboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.androidfung.departureboard.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// Test banner ID provided by Google for development / debug builds
private const val TEST_RECTANGLE_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
// Production Banner Ad Unit ID
private const val PROD_RECTANGLE_AD_UNIT_ID = "ca-app-pub-9890667024752973/3028288826"

/**
 * Medium Rectangle (300x250) or Banner Ad placed inside a card that seamlessly blends
 * with the Departure Board's Material 3 card styling.
 */
@Composable
fun AdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = if (BuildConfig.DEBUG) TEST_RECTANGLE_AD_UNIT_ID else PROD_RECTANGLE_AD_UNIT_ID,
    adSize: AdSize = AdSize.MEDIUM_RECTANGLE,
) {
    val context = LocalContext.current

    val adView = remember(adUnitId, adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // "Ad" badge header matching card aesthetic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { adView },
                    modifier = Modifier.wrapContentHeight()
                )
            }
        }
    }
}
