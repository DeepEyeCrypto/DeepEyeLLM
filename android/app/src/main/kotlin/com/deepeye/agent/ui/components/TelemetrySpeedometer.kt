package com.deepeye.agent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, zero-reflow Hardware Throughput Speedometer for Edge LLM benchmarking.
 * Displays live tokens/sec with smooth pointer animation and speed thresholds.
 */
@Composable
fun TelemetrySpeedometer(
    tokensPerSecond: Double,
    maxTps: Double = 60.0,
    modifier: Modifier = Modifier,
    engineName: String = "LiteRT NPU"
) {
    val animatedTps by animateFloatAsState(
        targetValue = tokensPerSecond.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "tps_anim"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0E1322),
        border = androidx.compose.foundation.BorderStroke(1.dp, TelemetryBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Track Background Arc (180 degrees, from 180 to 360)
                    drawArc(
                        color = Color(0x2600E5FF),
                        startAngle = 160f,
                        sweepAngle = 220f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Progress Arc
                    val ratio = (animatedTps / maxTps.toFloat()).coerceIn(0f, 1f)
                    val activeSweep = 220f * ratio

                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(CyberCyan, StatusSuccess, AmberAccent),
                            center = Offset(size.width / 2, size.height / 2)
                        ),
                        startAngle = 160f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Center Speed Readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(animatedTps),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "TOKENS / SEC",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = CyberCyan
                    )
                }
            }

            // Engine & Target Baseline Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delegate: $engineName",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = ThinkingMutedSlate
                )
                Text(
                    text = "Target: 30+ t/s (Real-time)",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (animatedTps >= 30.0) StatusSuccess else AmberAccent
                )
            }
        }
    }
}
