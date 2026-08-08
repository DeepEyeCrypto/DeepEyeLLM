package com.deepeye.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepeye.agent.ui.theme.DeepEyeTheme

/**
 * A glassmorphism-styled card component.
 * Uses the AEOS design system glass tokens for consistent styling.
 *
 * @param modifier Modifier for the card
 * @param isActive Whether the card shows an active/highlighted border
 * @param tintColor Optional tint override for the surface
 * @param borderColor Optional border color override
 * @param shape Card shape, defaults to 16dp rounded corners
 * @param elevation Card elevation
 * @param content Card content
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    tintColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = DeepEyeTheme.colors
    val containerColor = tintColor ?: colors.glassSurface
    val border = borderColor ?: if (isActive) colors.glassBorderActive else colors.glassBorder

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content
    )
}

/**
 * Elevated variant with stronger glass surface for prominent UI elements.
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
        elevation = 2.dp,
        content = content
    )
}
