package com.deepeye.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.ui.utils.accessibleFocusRing
import com.deepeye.agent.ui.utils.rememberIsReduceTransparencyEnabled

/**
 * Volumetric Glassmorphism Card Component.
 * Supports dynamic specular highlights, Z-axis elevation depth, active glowing borders,
 * and automatic fallback to opaque surfaces when OS "Reduce Transparency" is enabled.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isActive: Boolean = false,
    tintColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 0.dp,
    showSpecular: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = DeepEyeTheme.colors
    val isReduceTransparency = rememberIsReduceTransparencyEnabled()

    // Opaque fallback for WCAG accessibility when transparency is reduced
    val containerColor = when {
        isReduceTransparency -> Color(0xFF0E1322)
        tintColor != null -> tintColor
        else -> colors.glassSurface
    }

    val border = borderColor ?: if (isActive) colors.glassBorderActive else colors.glassBorder
    val borderWidth = if (isActive) 1.5.dp else 1.dp

    val cardModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(borderWidth, border),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(
            modifier = Modifier.drawWithContent {
                drawContent()
                // Volumetric specular top-highlight line for glass depth
                if (showSpecular && !isReduceTransparency) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                            startY = 0f,
                            endY = 30f
                        )
                    )
                }
            }
        ) {
            Column(content = content)
        }
    }
}

/**
 * Elevated Volumetric GlassCard variant for prominent dashboard or agent cards.
 */
@Composable
fun GlassCardElevated(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = DeepEyeTheme.colors
    GlassCard(
        modifier = modifier,
        isActive = isActive,
        tintColor = colors.glassSurfaceElevated,
        shape = shape,
        elevation = 4.dp,
        content = content
    )
}
