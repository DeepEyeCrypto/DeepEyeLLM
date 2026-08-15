package com.deepeye.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deepeye.agent.core.diagnostics.GovernorActionType
import com.deepeye.agent.core.diagnostics.InferencePerformanceMetrics
import com.deepeye.agent.core.diagnostics.PerformanceGovernor
import com.deepeye.agent.ui.theme.*

/**
 * High-Resolution Performance Diagnostics & Hardware Governor Modal Drawer.
 */
@Composable
fun PerformanceDebugDrawer(
    metrics: InferencePerformanceMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tpsHistory by remember { mutableStateOf(PerformanceGovernor.tpsRingBuffer.toList()) }
    val latencyHistory by remember { mutableStateOf(PerformanceGovernor.latencyRingBuffer.toList()) }
    val actions = remember(metrics) { PerformanceGovernor.getRecommendedActions() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, TelemetryBorder), RoundedCornerShape(24.dp)),
            color = Color(0xF2070A12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Performance Profiler HUD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Real-Time Telemetry & Thermal Self-Healing",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = ThinkingMutedSlate
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ThinkingMutedSlate)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Live Sparkline Charts
                    item {
                        PerformanceSparklineCanvas(
                            data = tpsHistory,
                            title = "GENERATION THROUGHPUT",
                            unit = "tok/s",
                            lineColor = CyberCyan,
                            fillColor = CyberCyan.copy(alpha = 0.15f),
                            targetValue = 30f
                        )
                    }

                    item {
                        PerformanceSparklineCanvas(
                            data = latencyHistory,
                            title = "TIME TO FIRST TOKEN (TTFT)",
                            unit = "ms",
                            lineColor = StatusSuccess,
                            fillColor = StatusSuccess.copy(alpha = 0.12f),
                            targetValue = 150f
                        )
                    }

                    // Key Metric Gauges Grid
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // JNI Latency
                            GlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                tintColor = Color(0xCC0E1322)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("JNI BRIDGE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = ThinkingMutedSlate)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${metrics.jniBridgeLatencyMicros} μs", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = CyberCyan)
                                    Text("Zero-overhead boundary", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = ThinkingMutedSlate)
                                }
                            }

                            // Thermal State
                            GlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                tintColor = Color(0xCC0E1322)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("THERMAL SENSOR", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = ThinkingMutedSlate)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${"%.1f".format(metrics.deviceTemperatureCelsius)}°C", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = if (metrics.thermalThrottlingLevel.isThrottled) StatusError else StatusSuccess)
                                    Text(metrics.thermalThrottlingLevel.label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = ThinkingMutedSlate)
                                }
                            }
                        }
                    }

                    // KV-Cache Barometer Card
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            tintColor = Color(0xCC0E1322)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "KV-CACHE RAM ALLOCATION",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = ThinkingMutedSlate
                                    )
                                    val percent = ((metrics.kvCacheUsageMb / metrics.kvCacheMaxCapacityMb) * 100).toInt()
                                    Text(
                                        text = "${metrics.kvCacheUsageMb.toInt()} / ${metrics.kvCacheMaxCapacityMb.toInt()} MB ($percent%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = if (percent > 75) StatusWarning else CyberCyan
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val ratio = (metrics.kvCacheUsageMb / metrics.kvCacheMaxCapacityMb).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (ratio > 0.75f) StatusWarning else CyberCyan,
                                    trackColor = Color(0x33FFFFFF)
                                )
                            }
                        }
                    }

                    // Self-Healing Governor Actions
                    if (actions.isNotEmpty()) {
                        item {
                            Text(
                                text = "AUTOMATED SELF-HEALING RECOMMENDATIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = StatusWarning
                            )
                        }

                        items(actions, key = { it.id }) { action ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when (action.actionType) {
                                            GovernorActionType.REDUCE_THREADS -> PerformanceGovernor.setThreadCount(2)
                                            GovernorActionType.COMPACT_KV_CACHE -> PerformanceGovernor.updateKvCacheUsage(48f)
                                            GovernorActionType.ENABLE_ZERO_BLUR -> PerformanceGovernor.setZeroBlur(true)
                                            else -> {}
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xCC151A29),
                                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(action.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(action.description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = ThinkingMutedSlate)
                                        Text(action.impact, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = CyberCyan)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CyberCyan.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "APPLY",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = CyberCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
