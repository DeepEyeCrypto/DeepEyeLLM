package com.deepeye.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*

enum class ToolExecutionStatus {
    RUNNING,
    SUCCESS,
    ERROR,
    AWAITING_APPROVAL
}

/**
 * Tactical Tool Execution Card for displaying on-device or cloud tool invocation traces.
 */
@Composable
fun ToolExecutionCard(
    toolName: String,
    status: ToolExecutionStatus,
    modifier: Modifier = Modifier,
    targetResource: String? = null,
    durationMs: Long? = null,
    outputSummary: String? = null,
    onApprove: (() -> Unit)? = null,
    onDeny: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when (status) {
        ToolExecutionStatus.RUNNING -> CyberCyan
        ToolExecutionStatus.SUCCESS -> StatusSuccess
        ToolExecutionStatus.ERROR -> StatusError
        ToolExecutionStatus.AWAITING_APPROVAL -> AmberAccent
    }

    val statusIcon: ImageVector = when (status) {
        ToolExecutionStatus.RUNNING -> Icons.Default.Build
        ToolExecutionStatus.SUCCESS -> Icons.Default.CheckCircle
        ToolExecutionStatus.ERROR -> Icons.Default.Warning
        ToolExecutionStatus.AWAITING_APPROVAL -> Icons.Default.Security
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC0E1322),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = "Tool Status",
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tool: $toolName",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (durationMs != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${durationMs}ms)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ThinkingMutedSlate
                                )
                            }
                        }
                        if (targetResource != null) {
                            Text(
                                text = "Target: $targetResource",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = ThinkingMutedSlate,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeonStatusBadge(
                        text = when (status) {
                            ToolExecutionStatus.RUNNING -> "Executing"
                            ToolExecutionStatus.SUCCESS -> "Verified"
                            ToolExecutionStatus.ERROR -> "Failed"
                            ToolExecutionStatus.AWAITING_APPROVAL -> "Approval Gate"
                        },
                        color = statusColor,
                        isPulsing = status == ToolExecutionStatus.RUNNING,
                        modifier = Modifier.height(22.dp)
                    )
                }
            }

            // Human-in-the-loop approval actions
            if (status == ToolExecutionStatus.AWAITING_APPROVAL) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onApprove?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusSuccess.copy(alpha = 0.2f),
                            contentColor = StatusSuccess
                        ),
                        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow Action", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { onDeny?.invoke() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusError
                        ),
                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deny", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Expandable Output Summary
            AnimatedVisibility(visible = isExpanded && outputSummary != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF070A12))
                        .padding(10.dp)
                ) {
                    Text(
                        text = outputSummary ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = Color(0xFFB0BEC5)
                    )
                }
            }
        }
    }
}
