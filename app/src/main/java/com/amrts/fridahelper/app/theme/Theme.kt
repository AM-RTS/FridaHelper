package com.amrts.fridahelper.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = GreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = GreenOnSecondaryContainer,
    tertiary = GreenTertiary,
    tertiaryContainer = GreenTertiaryContainer,
    onTertiaryContainer = GreenOnTertiaryContainer,
    surface = Color(0xFFFCFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F1),
    surfaceContainer = Color(0xFFF0F1EB),
    surfaceContainerHigh = Color(0xFFEBECE6),
    surfaceContainerHighest = Color(0xFFE5E6E0),
    outline = GreenOutline,
    outlineVariant = GreenOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenAccent,
    onPrimary = GreenDark,
    primaryContainer = GreenPrimary,
    onPrimaryContainer = GreenLight,
    secondary = Color(0xFFB9CCB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = GreenSecondaryContainer,
    tertiary = Color(0xFFA0CFD3),
    tertiaryContainer = Color(0xFF1E4D50),
    onTertiaryContainer = GreenTertiaryContainer,
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFF0D0F0D),
    surfaceContainerLow = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFF1E201D),
    surfaceContainerHigh = Color(0xFF292B27),
    surfaceContainerHighest = Color(0xFF343632),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940)
)

@Composable
fun FridaHelperTheme(
    themeMode: Int = 0,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        1 -> false   // MODE_LIGHT
        2 -> true    // MODE_DARK
        else -> isSystemInDarkTheme()  // MODE_SYSTEM
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FridaTypography,
        shapes = AppShapes,
        content = content
    )
}
