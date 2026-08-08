package com.deepeye.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Extended color tokens not covered by Material3's ColorScheme.
 */
@Immutable
data class DeepEyeColors(
    val glassSurface: Color = GlassDarkSurface,
    val glassSurfaceElevated: Color = GlassDarkSurfaceElevated,
    val glassOverlay: Color = GlassLightOverlay,
    val glassBorder: Color = GlassBorder,
    val glassBorderActive: Color = GlassBorderActive,
    val glassHighlight: Color = GlassHighlight,
    val accent: Color = AmberAccent,
    val statusSuccess: Color = StatusSuccess,
    val statusWarning: Color = StatusWarning,
    val statusError: Color = StatusError,
    val statusInfo: Color = StatusInfo,
    val policyPurple: Color = PolicyPurple
)

val LocalDeepEyeColors = staticCompositionLocalOf { DeepEyeColors() }

private val DarkColorScheme = darkColorScheme(
    primary = DeepBluePrimaryDark,
    onPrimary = Color.White,
    primaryContainer = DeepBluePrimary,
    secondary = TealCyanSecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = TealCyanSecondary,
    tertiary = PolicyPurpleDark,
    onTertiary = Color.Black,
    background = GlassDarkBackground,
    surface = GlassDarkSurface,
    surfaceVariant = GlassDarkSurfaceElevated,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = DeepBluePrimary,
    onPrimary = Color.White,
    primaryContainer = DeepBluePrimaryLight,
    secondary = TealCyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealCyanSecondaryLight,
    tertiary = PolicyPurple,
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEEEE),
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFF546E7A),
    error = StatusError,
    onError = Color.White
)

private val DarkDeepEyeColors = DeepEyeColors()

private val LightDeepEyeColors = DeepEyeColors(
    glassSurface = Color(0xE6FFFFFF),
    glassSurfaceElevated = Color(0xF2FFFFFF),
    glassOverlay = Color(0x1A000000),
    glassBorder = Color(0x1A000000),
    glassBorderActive = Color(0x4D000000),
    glassHighlight = Color(0x0D000000),
    accent = AmberAccent,
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    statusInfo = StatusInfo,
    policyPurple = PolicyPurple
)

@Composable
fun DeepEyeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val deepEyeColors = if (darkTheme) DarkDeepEyeColors else LightDeepEyeColors

    CompositionLocalProvider(LocalDeepEyeColors provides deepEyeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Convenience accessor for DeepEye extended colors.
 * Usage: DeepEyeTheme.colors.glassSurface
 */
object DeepEyeTheme {
    val colors: DeepEyeColors
        @Composable
        get() = LocalDeepEyeColors.current
}
