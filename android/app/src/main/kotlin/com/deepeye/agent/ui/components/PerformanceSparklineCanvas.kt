package com.deepeye.agent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.CyberCyan
import com.deepeye.agent.ui.theme.ThinkingMutedSlate

/**
 * High-Performance Zero-Allocation Micro-Canvas Sparkline for Real-Time LLM Telemetry.
 */
@Composable
fun PerformanceSparklineCanvas(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = CyberCyan,
    fillColor: Color = CyberCyan.copy(alpha = 0.15f),
    unit: String = "TPS",
    title: String = "Throughput Curve",
    targetValue: Float? = null
) {
    val samples = remember(data) {
        if (data.isEmpty()) listOf(0f, 0f) else data
    }
    val minVal = remember(samples) { samples.minOrNull() ?: 0f }
    val maxVal = remember(samples) { (samples.maxOrNull() ?: 1f).coerceAtLeast(minVal + 1f) }
    val currentVal = remember(samples) { samples.lastOrNull() ?: 0f }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E1322))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ThinkingMutedSlate
                )
                Text(
                    text = "${"%.1f".format(currentVal)} $unit",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MIN: ${"%.0f".format(minVal)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    color = ThinkingMutedSlate
                )
                Text(
                    text = "MAX: ${"%.0f".format(maxVal)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    color = lineColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val width = size.width
            val height = size.height
            val n = samples.size
            if (n < 2) return@Canvas

            val stepX = width / (n - 1)
            val range = (maxVal - minVal).coerceAtLeast(0.001f)

            val linePath = Path()
            val fillPath = Path()

            val firstY = height - ((samples[0] - minVal) / range) * (height - 8.dp.toPx()) - 4.dp.toPx()
            linePath.moveTo(0f, firstY)
            fillPath.moveTo(0f, height)
            fillPath.lineTo(0f, firstY)

            for (i in 1 until n) {
                val x = i * stepX
                val y = height - ((samples[i] - minVal) / range) * (height - 8.dp.toPx()) - 4.dp.toPx()
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw area fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw target reference dashed line if present
            targetValue?.let { target ->
                val targetY = height - ((target - minVal) / range) * (height - 8.dp.toPx()) - 4.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, targetY),
                    end = Offset(width, targetY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw stroke line
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
