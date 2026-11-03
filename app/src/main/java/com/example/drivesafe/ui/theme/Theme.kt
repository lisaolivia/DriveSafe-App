package com.example.drivesafe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Desain DriveSafe hanya punya varian terang, jadi tema tidak mengikuti mode gelap / dynamic color. */
private val DriveSafeColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = BrandBlueDark,
    secondary = BrandYellow,
    onSecondary = BrandBlueDark,
    secondaryContainer = BrandYellowSoft,
    onSecondaryContainer = BrandYellowText,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedSoft,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = BorderInput,
    outlineVariant = BorderLight
)

@Composable
fun DriveSafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DriveSafeColors,
        typography = Typography,
        content = content
    )
}
