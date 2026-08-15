package com.deepeye.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.core.diagnostics.PerformanceGovernor
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.theme.*

/**
 * Top Telemetry HUD Header for the DeepEyeLLM Workstation.
 * Displays hardware engine delegation (LiteRT NPU vs GPU), throughput, and thermal health.
 */
@Composable
fun TelemetryHeaderHUD(
    activeModelName: String,
    modelStatus: ModelStatus,
    modifier: Modifier = Modifier,
    tokensPerSecond: Float = 0f,
    thermalState: String = "Nominal",
    onPickerClick: () -> Unit
) {
    val isLiteRT = remember(activeModelName) {
        activeModelName.startsWith("LiteRT", ignoreCase = true)
    }
    val engineLabel = if (isLiteRT) "LiteRT NPU" else "Local GPU/CPU"
    val isEngineActive = modelStatus == ModelStatus.LOCAL_ACTIVE

    var isPerformanceDrawerOpen by remember { mutableStateOf(false) }
    val metrics by PerformanceGovernor.metrics.collectAsStateWithLifecycle()

    if (isPerformanceDrawerOpen) {
        PerformanceDebugDrawer(
            metrics = metrics,
            onDismiss = { isPerformanceDrawerOpen = false }
        )
    }

    Surface(
        color = Color(0xF2070A12),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Title & Model Selector
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPickerClick)
                        .padding(end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DeepEyeLLM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "WORKSTATION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = CyberCyan
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = activeModelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = onPickerClick,
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x3300E5FF),
                            border = BorderStroke(0.5.dp, CyberCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Switch",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Engine Badge
                NeonStatusBadge(
                    text = if (isEngineActive) engineLabel else "Offline",
                    isPulsing = isEngineActive,
                    onClick = onPickerClick,
                    color = if (isEngineActive) StatusSuccess else StatusWarning
                )
            }

            // Real-Time Hardware Telemetry Strip (Clickable for Diagnostics HUD)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x800E1322))
                    .clickable { isPerformanceDrawerOpen = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speedometer Metric
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Token Speed",
                        tint = CyberCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (tokensPerSecond > 0) "${"%.1f".format(tokensPerSecond)} t/s" else "Ready",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = if (tokensPerSecond > 0) CyberCyan else ThinkingMutedSlate
                    )
                }

                // Accelerator Engine
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Engine Delegate",
                        tint = StatusSuccess,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isLiteRT) "NNAPI / QNN" else "Vulkan / GPU",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFFB0BEC5)
                    )
                }

                // Thermal State
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Thermal State",
                        tint = AmberAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = thermalState,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFFB0BEC5)
                    )
                }
            }
        }
    }
}
