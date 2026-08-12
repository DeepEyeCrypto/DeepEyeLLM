package com.deepeye.agent.ui.utils

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

object AccessibilityUtils {

    /**
     * Checks whether the device settings have enabled "Reduce Transparency" / High Contrast surfaces.
     */
    fun isReduceTransparencyEnabled(context: Context): Boolean {
        return try {
            val reduceTransparency = Settings.System.getInt(
                context.contentResolver,
                "reduce_transparency", 0
            )
            reduceTransparency == 1
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Calculates the WCAG 2.2 contrast ratio between two Compose colors.
     * Returns a ratio in the range 1.0..21.0.
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Double {
        return ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
    }

    /**
     * Ensures a minimum WCAG AA contrast ratio of 4.5:1 for body text or UI components.
     * If the contrast ratio is below 4.5:1, returns the fallback contrast color.
     */
    fun ensureContrastRatio(
        foreground: Color,
        background: Color,
        minRatio: Double = 4.5,
        fallback: Color = Color.White
    ): Color {
        val ratio = calculateContrastRatio(foreground, background)
        return if (ratio >= minRatio) foreground else fallback
    }
}

/**
 * Composable helper to inspect if reduced transparency is requested by OS settings.
 */
@Composable
fun rememberIsReduceTransparencyEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        AccessibilityUtils.isReduceTransparencyEnabled(context)
    }
}

/**
 * Modifier to display an accessible glowing focus ring when focused via keyboard / D-pad.
 */
fun Modifier.accessibleFocusRing(
    focusColor: Color = Color(0xFF00E5FF),
    strokeWidth: Dp = 2.dp,
    shapeRadius: Dp = 12.dp
): Modifier = composed {
    var isFocused = remember { androidx.compose.runtime.mutableStateOf(false) }
    this
        .onFocusChanged { isFocused.value = it.isFocused }
        .then(
            if (isFocused.value) {
                Modifier.border(
                    width = strokeWidth,
                    color = focusColor,
                    shape = RoundedCornerShape(shapeRadius)
                )
            } else {
                Modifier
            }
        )
}
