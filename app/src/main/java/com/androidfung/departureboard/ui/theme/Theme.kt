package com.androidfung.departureboard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val TflDarkColorScheme = darkColorScheme(
    primary = TflDarkPrimary,
    onPrimary = TflDarkOnPrimary,
    primaryContainer = TflDarkPrimaryContainer,
    onPrimaryContainer = TflDarkOnPrimaryContainer,
    secondary = TflDarkSecondary,
    onSecondary = TflDarkOnSecondary,
    secondaryContainer = TflDarkSecondaryContainer,
    onSecondaryContainer = TflDarkOnSecondaryContainer,
    tertiary = TflDarkTertiary,
    onTertiary = TflDarkOnTertiary,
    tertiaryContainer = TflDarkTertiaryContainer,
    onTertiaryContainer = TflDarkOnTertiaryContainer,
    background = TflDarkBackground,
    onBackground = TflDarkOnBackground,
    surface = TflDarkSurface,
    onSurface = TflDarkOnSurface,
    surfaceVariant = TflDarkSurfaceVariant,
    onSurfaceVariant = TflDarkOnSurfaceVariant,
    outline = TflDarkOutline,
    outlineVariant = TflDarkOutlineVariant,
    surfaceContainer = TflDarkSurfaceContainer,
    surfaceContainerHigh = TflDarkSurfaceContainerHigh,
    surfaceContainerHighest = TflDarkSurfaceContainerHighest,
    surfaceContainerLow = TflDarkSurfaceContainerLow
)

private val TflLightColorScheme = lightColorScheme(
    primary = TflLightPrimary,
    onPrimary = TflLightOnPrimary,
    primaryContainer = TflLightPrimaryContainer,
    onPrimaryContainer = TflLightOnPrimaryContainer,
    secondary = TflLightSecondary,
    onSecondary = TflLightOnSecondary,
    secondaryContainer = TflLightSecondaryContainer,
    onSecondaryContainer = TflLightOnSecondaryContainer,
    tertiary = TflLightTertiary,
    onTertiary = TflLightOnTertiary,
    tertiaryContainer = TflLightTertiaryContainer,
    onTertiaryContainer = TflLightOnTertiaryContainer,
    background = TflLightBackground,
    onBackground = TflLightOnBackground,
    surface = TflLightSurface,
    onSurface = TflLightOnSurface,
    surfaceVariant = TflLightSurfaceVariant,
    onSurfaceVariant = TflLightOnSurfaceVariant,
    outline = TflLightOutline,
    outlineVariant = TflLightOutlineVariant,
    surfaceContainer = TflLightSurfaceContainer,
    surfaceContainerHigh = TflLightSurfaceContainerHigh,
    surfaceContainerHighest = TflLightSurfaceContainerHighest,
    surfaceContainerLow = TflLightSurfaceContainerLow
)

@Composable
fun DepartureBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material 3 Dynamic Color (Android 12+) enabled by default
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> TflDarkColorScheme
        else -> TflLightColorScheme
    }

    CompositionLocalProvider(
        LocalDimensions provides Dimensions()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}