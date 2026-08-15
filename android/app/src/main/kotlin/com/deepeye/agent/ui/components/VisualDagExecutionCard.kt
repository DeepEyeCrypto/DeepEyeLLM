package com.deepeye.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*

enum class DagNodeStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class DagExecutionNode(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: DagNodeStatus,
    val durationMs: Long = 0,
    val tokensUsed: Int = 0
)

/**
 * Visual Multi-Step DAG Workflow Execution Card.
 * Displays real-time agent workflow transitions, node latency, and token consumption with zero layout jitter.
 */
@Composable
fun VisualDagExecutionCard(
    nodes: List<DagExecutionNode>,
    modifier: Modifier = Modifier,
    planTitle: String = "Autonomous Execution DAG",
    onPauseToggle: () -> Unit = {},
    isPaused: Boolean = false
) {
    val completedCount = nodes.count { it.status == DagNodeStatus.COMPLETED }
    val totalCount = nodes.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // DAG Header
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = planTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$completedCount of $totalCount steps resolved",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThinkingMutedSlate
                        )
                    }
                }

                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) AmberAccent.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume DAG" else "Pause DAG",
                        tint = if (isPaused) AmberAccent else CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Overall Progress
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyberCyan,
                trackColor = Color(0x3300E5FF)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Connected DAG Nodes
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                nodes.forEachIndexed { index, node ->
                    DagNodeRow(
                        node = node,
                        isLast = index == nodes.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun DagNodeRow(
    node: DagExecutionNode,
    isLast: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Vertical Connector Line & Status Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        when (node.status) {
                            DagNodeStatus.COMPLETED -> StatusSuccess
                            DagNodeStatus.RUNNING -> CyberCyan.copy(alpha = haloAlpha)
                            DagNodeStatus.FAILED -> StatusError
                            DagNodeStatus.SKIPPED -> ThinkingMutedSlate
                            DagNodeStatus.PENDING -> Color(0x33FFFFFF)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (node.status) {
                    DagNodeStatus.COMPLETED -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    DagNodeStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    DagNodeStatus.FAILED -> Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    else -> Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(
                            if (node.status == DagNodeStatus.COMPLETED) StatusSuccess.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.1f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Node Content & Metrics
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (node.status == DagNodeStatus.RUNNING) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = when (node.status) {
                        DagNodeStatus.RUNNING -> CyberCyan
                        DagNodeStatus.COMPLETED -> Color.White
                        DagNodeStatus.FAILED -> StatusError
                        else -> ThinkingMutedSlate
                    }
                )

                if (node.durationMs > 0 || node.tokensUsed > 0) {
                    Text(
                        text = "${node.durationMs}ms • ${node.tokensUsed} tok",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        ),
                        color = ThinkingMutedSlate
                    )
                }
            }

            Text(
                text = node.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = ThinkingMutedSlate,
                maxLines = 1
            )
        }
    }
}
