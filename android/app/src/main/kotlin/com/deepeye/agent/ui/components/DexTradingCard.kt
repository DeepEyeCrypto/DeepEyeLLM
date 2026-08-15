package com.deepeye.agent.ui.components

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
import com.deepeye.agent.core.dex.DexTradeIntent
import com.deepeye.agent.core.dex.TradeExecutionStatus
import com.deepeye.agent.ui.theme.*

/**
 * Volumetric Obsidian Glass DEX Trade Execution Ticket.
 */
@Composable
fun DexTradingCard(
    intent: DexTradeIntent,
    onExecuteSwap: (DexTradeIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentIntent by remember(intent) { mutableStateOf(intent) }
    val isExecuted = currentIntent.status == TradeExecutionStatus.EXECUTED
    val isBlocked = currentIntent.status == TradeExecutionStatus.BLOCKED_BY_SAFETY_GATE

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    if (isExecuted) StatusSuccess.copy(alpha = 0.5f) else if (isBlocked) StatusError.copy(alpha = 0.5f) else CyberCyan.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Router & Safety Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "NOUS HERMES 3 DEX SENTINEL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = CyberCyan
                        )
                        Text(
                            text = currentIntent.quote.dexRouter.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ThinkingMutedSlate
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (currentIntent.securityAudit.isSafeToTrade) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (currentIntent.securityAudit.isSafeToTrade) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (currentIntent.securityAudit.isSafeToTrade) StatusSuccess else StatusError,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${currentIntent.securityAudit.overallSafetyScore}/100 Safe",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (currentIntent.securityAudit.isSafeToTrade) StatusSuccess else StatusError
                        )
                    }
                }
            }

            // Trade Pair Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x80151A29),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "YOU PAY",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = ThinkingMutedSlate
                        )
                        Text(
                            text = "${"%.4f".format(currentIntent.amountIn)} ${currentIntent.tokenIn}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }

                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ESTIMATED OUTPUT",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = ThinkingMutedSlate
                        )
                        Text(
                            text = "${"%.4f".format(currentIntent.estimatedAmountOut)} ${currentIntent.tokenOut}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = StatusSuccess
                        )
                    }
                }
            }

            // Telemetry & Security Gates Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔒 LP: ${currentIntent.securityAudit.lpLockDurationDays}d Locked",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = ThinkingMutedSlate
                )
                Text(
                    text = "🛡️ Tax: ${currentIntent.securityAudit.buyTaxPct}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = ThinkingMutedSlate
                )
                Text(
                    text = "⚡ Slip: ${currentIntent.maxSlippagePct}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = ThinkingMutedSlate
                )
            }

            // Status message
            Text(
                text = currentIntent.statusMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (isExecuted) StatusSuccess else if (isBlocked) StatusError else CyberCyan
            )

            // Action Button
            if (!isExecuted && !isBlocked) {
                CyberButton(
                    onClick = {
                        val executed = currentIntent.copy(
                            status = TradeExecutionStatus.EXECUTED,
                            statusMessage = "✅ Non-Custodial Swap Executed on ${currentIntent.quote.dexRouter.displayName}"
                        )
                        currentIntent = executed
                        onExecuteSwap(executed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = CyberCyan
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXECUTE NON-CUSTODIAL SWAP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
