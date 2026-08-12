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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepeye.agent.ui.utils.rememberIsReduceTransparencyEnabled

/**
 * Volumetric glass tokens representing refractive index, specular lighting, and blur attributes.
 */
@Immutable
data class RefractiveTokens(
    val blurRadius: Dp = 24.dp,
    val specularAlpha: Float = 0.25f,
    val borderAlpha: Float = 0.35f,
    val zDepth: Dp = 4.dp
)

/**
 * Extended color tokens for DeepEyeLLM cyber-glass aesthetics.
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
    val policyPurple: Color = PolicyPurple,
    val link: Color = LinkBlue,
    val warningAlt: Color = WarningAlt,
    val dangerAlt: Color = DangerAlt,
    val brandOrange: Color = BrandOrange,
    val refractive: RefractiveTokens = RefractiveTokens()
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
    background = Color(0xFF070A12),
    surface = Color(0xFF0E1322),
    surfaceVariant = Color(0xFF161E33),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = StatusError,
    onError = Color.White
)

private val DarkDeepEyeColors = DeepEyeColors()

private val LightDeepEyeColors = DeepEyeColors(
    glassSurface = Color(0xE6121826),
    glassSurfaceElevated = Color(0xF21A233A),
    glassOverlay = Color(0x1AFFFFFF),
    glassBorder = Color(0x33FFFFFF),
    glassBorderActive = Color(0x6600E5FF),
    glassHighlight = Color(0x1AFFFFFF),
    accent = AmberAccent,
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    statusInfo = StatusInfo,
    policyPurple = PolicyPurple,
    link = LinkBlue,
    warningAlt = WarningAlt,
    dangerAlt = DangerAlt,
    brandOrange = BrandOrange
)

@Composable
fun DeepEyeTheme(
    darkTheme: Boolean = isSystemInDarkTheme() || true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isReduceTransparency = rememberIsReduceTransparencyEnabled()

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val baseDeepEyeColors = if (darkTheme) DarkDeepEyeColors else LightDeepEyeColors

    // WCAG 2.2 Reduce Transparency Fallback: Force high opacity surfaces if OS transparency is disabled
    val deepEyeColors = if (isReduceTransparency) {
        baseDeepEyeColors.copy(
            glassSurface = Color(0xFF0E1322),
            glassSurfaceElevated = Color(0xFF161E33),
            refractive = baseDeepEyeColors.refractive.copy(blurRadius = 0.dp)
        )
    } else {
        baseDeepEyeColors
    }

    CompositionLocalProvider(
        LocalDeepEyeColors provides deepEyeColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = DeepEyeShapes,
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
