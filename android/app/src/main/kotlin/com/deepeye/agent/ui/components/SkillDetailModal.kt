package com.deepeye.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import com.deepeye.agent.core.skill.Skill
import com.deepeye.agent.ui.theme.*

/**
 * Obsidian-styled Agent Skills Standard Manifest Modal.
 * Displays tools provided, verification gates, anti-rationalization contracts, and security permissions.
 */
@Composable
fun SkillDetailModal(
    skill: Skill,
    onDismiss: () -> Unit,
    onInstallToggle: (Skill) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF70E1322),
            border = BorderStroke(1.dp, TelemetryBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Modal Header
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = skill.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "v${skill.version} by ${skill.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ThinkingMutedSlate
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ThinkingMutedSlate)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Category & Status
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CyberChip(
                                label = skill.category,
                                selected = true,
                                onClick = {}
                            )
                            NeonStatusBadge(
                                text = if (skill.isInstalled) "Active & Verified" else "Not Installed",
                                color = if (skill.isInstalled) StatusSuccess else ThinkingMutedSlate,
                                isPulsing = skill.isInstalled
                            )
                        }
                    }

                    // Description
                    item {
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 20.sp
                        )
                    }

                    // Tools Provided
                    if (skill.toolsProvided.isNotEmpty()) {
                        item {
                            Text(
                                text = "TOOLS PROVIDED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                skill.toolsProvided.forEach { tool ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x3300E5FF))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tool,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            ),
                                            color = CyberCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Verification Gates
                    if (skill.verificationGates.isNotEmpty()) {
                        item {
                            Text(
                                text = "VERIFICATION GATES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = StatusSuccess
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                skill.verificationGates.forEach { gate ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = StatusSuccess,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = gate,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = Color(0xFFB0BEC5)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Anti-Rationalization Rules
                    if (skill.antiRationalizationRules.isNotEmpty()) {
                        item {
                            Text(
                                text = "ANTI-RATIONALIZATION CONSTRAINTS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = AmberAccent
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1AFFB300))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                skill.antiRationalizationRules.forEach { rule ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("•", color = AmberAccent, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = rule,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = Color(0xFFFFE082)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Permissions
                    if (skill.permissionsRequired.isNotEmpty()) {
                        item {
                            Text(
                                text = "SECURITY PERMISSIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = ThinkingMutedSlate
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = skill.permissionsRequired.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = ThinkingMutedSlate
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Button
                CyberButton(
                    onClick = {
                        onInstallToggle(skill)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (skill.isInstalled) StatusError else CyberCyan
                ) {
                    Icon(
                        imageVector = if (skill.isInstalled) Icons.Default.Delete else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (skill.isInstalled) "Deactivate Skill" else "Install & Activate Skill",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
