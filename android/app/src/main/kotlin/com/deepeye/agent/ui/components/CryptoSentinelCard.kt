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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*

/**
 * Tactical Crypto Sentinel On-Chain Audit & Verification Card.
 * Displays smart contract verification gates, honeypot analysis, and non-custodial execution intent simulation.
 */
@Composable
fun CryptoSentinelCard(
    tokenSymbol: String,
    contractAddress: String,
    securityScore: Int,
    modifier: Modifier = Modifier,
    liquidityLockDays: Int = 365,
    buyTax: Float = 0f,
    sellTax: Float = 0f,
    isHoneypotFree: Boolean = true,
    isOwnershipRenounced: Boolean = true,
    isReentrancyClean: Boolean = true,
    onSimulateIntent: () -> Unit = {}
) {
    val scoreColor = when {
        securityScore >= 90 -> StatusSuccess
        securityScore >= 70 -> AmberAccent
        else -> StatusError
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, scoreColor.copy(alpha = 0.4f)), shape = RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Crypto Sentinel",
                            tint = scoreColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = tokenSymbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${contractAddress.take(6)}...${contractAddress.takeLast(4)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = ThinkingMutedSlate
                        )
                    }
                }

                // Security Score Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scoreColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$securityScore/100",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = scoreColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Verification Checkpoint Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x66070A12))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Liquidity Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (liquidityLockDays > 90) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (liquidityLockDays > 90) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Liquidity Pool Lock", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFD8DC))
                    }
                    Text(
                        text = "$liquidityLockDays Days Locked",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (liquidityLockDays > 90) StatusSuccess else StatusWarning
                    )
                }

                // Honeypot / Tax Check
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (isHoneypotFree) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isHoneypotFree) StatusSuccess else StatusError,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Honeypot / Tax", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFD8DC))
                    }
                    Text(
                        text = "${buyTax}% Buy / ${sellTax}% Sell",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (isHoneypotFree) StatusSuccess else StatusError
                    )
                }

                // AST Reentrancy Analysis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (isReentrancyClean) Icons.Default.Code else Icons.Default.BugReport,
                            contentDescription = null,
                            tint = if (isReentrancyClean) CyberCyan else StatusError,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("AST Reentrancy Scan", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFD8DC))
                    }
                    Text(
                        text = if (isReentrancyClean) "Passed (Clean)" else "High Risk",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (isReentrancyClean) StatusSuccess else StatusError
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Non-Custodial Simulation Intent Button
            CyberButton(
                onClick = onSimulateIntent,
                modifier = Modifier.fillMaxWidth(),
                accentColor = CyberCyan
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulate Non-Custodial Intent", fontWeight = FontWeight.Bold)
            }
        }
    }
}
