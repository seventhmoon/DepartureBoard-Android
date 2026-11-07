package com.androidfung.departureboard.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidfung.departureboard.R
import com.androidfung.departureboard.ui.theme.DepartureBoardTheme

/**
 * Preview gallery showcasing the 4 custom Splash Screen icon designs and branding footer
 * across both Dark and Light splash screen backgrounds.
 */
@Composable
private fun SplashMockPhoneCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDark) Color(0xFF0D1322) else Color(0xFFF8F9FE)
    val textColor = if (isDark) Color(0xFFE2E2E9) else Color(0xFF191C20)
    val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF5F6368)
    val borderColor = if (isDark) Color(0xFF262A38) else Color(0xFFC4C6D0)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = subtextColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Android 12+ 160dp circular mask boundary indicator + 288dp icon scaled proportionally
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .border(1.dp, borderColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(210.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Branding footer (200x80dp aspect ratio)
        Image(
            painter = painterResource(id = R.drawable.ic_splash_branding),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(60.dp)
        )
    }
}

@Preview(name = "Splash Screen Designs (Dark)", widthDp = 760, heightDp = 680)
@Composable
fun SplashScreenDesignsDarkPreview() {
    DepartureBoardTheme {
        Surface(color = Color(0xFF070A12), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SplashMockPhoneCard(
                        title = "1. Concourse LED Board",
                        subtitle = "avd_splash_screen",
                        iconRes = R.drawable.ic_splash_logo,
                        isDark = true,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                    SplashMockPhoneCard(
                        title = "2. Solari Split-Flap",
                        subtitle = "avd_splash_split_flap",
                        iconRes = R.drawable.ic_splash_split_flap,
                        isDark = true,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SplashMockPhoneCard(
                        title = "3. Express Train Roundel",
                        subtitle = "avd_splash_express_train",
                        iconRes = R.drawable.ic_splash_express_train,
                        isDark = true,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                    SplashMockPhoneCard(
                        title = "4. Metro Pulse Dial",
                        subtitle = "avd_splash_metro_pulse",
                        iconRes = R.drawable.ic_splash_metro_pulse,
                        isDark = true,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
        }
    }
}
