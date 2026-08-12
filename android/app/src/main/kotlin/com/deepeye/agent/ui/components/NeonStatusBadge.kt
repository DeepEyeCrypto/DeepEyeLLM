package com.deepeye.agent.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.ui.utils.DeepEyeHapticType
import com.deepeye.agent.ui.utils.PerformanceUtils

/**
 * Animated Neon Status Badge with subtle pulse, WCAG compliance, and optional touch haptics.
 */
@Composable
fun NeonStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00E5FF),
    isPulsing: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "NeonBadgePulse")
    val alphaPulse by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "alphaPulse"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(1.0f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        PerformanceUtils.triggerHaptic(context, DeepEyeHapticType.CLICK, haptic)
                        onClick()
                    }
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics {
                contentDescription = "Status badge: $text"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alphaPulse)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
