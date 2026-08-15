package com.deepeye.agent.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.*

data class AuditLogUi(
    val id: String,
    val action: String,
    val caller: String,
    val time: String,
    val isAllowed: Boolean,
    val decisionHash: String
)

data class RbacPolicyItem(
    val title: String,
    val subtitle: String,
    var isEnabled: Boolean
)

@Composable
fun SecurityDashboardScreen() {
    var isAirGappedStrict by remember { mutableStateOf(true) }
    var isCanaryIntact by remember { mutableStateOf(true) }

    val rbacPolicies = remember {
        mutableStateListOf(
            RbacPolicyItem("Filesystem Sandbox Isolation", "Restrict agent reads/writes to app-private storage only", true),
            RbacPolicyItem("Biometric Gate for Intents", "Require biometric confirmation prior to transaction broadcasting", true),
            RbacPolicyItem("AST Patch Regression Gate", "Prevent applying diffs that fail zero-regression syntax verification", true)
        )
    }

    val auditLogs = remember {
        listOf(
            AuditLogUi("1", "File Read: /documents/runbook.txt", "RAG Retriever", "10:42 AM", true, "a8f3...12c9"),
            AuditLogUi("2", "GGUF Checksum Verified: hermes-3b.gguf", "Model Manager", "10:40 AM", true, "c4b9...901e"),
            AuditLogUi("3", "Unregistered Tool Call Blocked: socket.bind", "Untrusted Agent", "10:15 AM", false, "7e2a...44f1"),
            AuditLogUi("4", "Canary Token Integrity Verified", "Security Daemon", "09:30 AM", true, "2b55...61d8")
        )
    }

    Scaffold(
        containerColor = Color(0xFF070A12),
        topBar = {
            Surface(
                color = Color(0xF2070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Zero-Trust Security & RBAC",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "On-Device Control Plane & Policy Interceptor",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }

                    NeonStatusBadge(
                        text = if (isAirGappedStrict) "Air-Gapped" else "Mesh Only",
                        isPulsing = isAirGappedStrict,
                        color = if (isAirGappedStrict) StatusSuccess else AmberAccent
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // Canary Token & Guard Status Hero
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    tintColor = Color(0xCC0E1322)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(StatusSuccess.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(20.dp))
                                }

                                Column {
                                    Text(
                                        text = "Canary Integrity: Intact",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Zero unauthorized memory exfiltration attempts",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = ThinkingMutedSlate
                                    )
                                }
                            }

                            Switch(
                                checked = isAirGappedStrict,
                                onCheckedChange = { isAirGappedStrict = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberCyan,
                                    checkedTrackColor = CyberCyan.copy(alpha = 0.3f),
                                    uncheckedThumbColor = ThinkingMutedSlate,
                                    uncheckedTrackColor = Color(0x33FFFFFF)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Strict Air-Gapping blocks all external WAN sockets. Only local-mesh and loopback RPCs are permitted.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }
            }

            // RBAC Policy Matrix Header
            item {
                Text(
                    text = "RBAC ENFORCEMENT POLICIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CyberCyan
                )
            }

            items(rbacPolicies.size) { index ->
                val policy = rbacPolicies[index]
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC0E1322),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(policy.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(policy.subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = ThinkingMutedSlate)
                        }
                        Switch(
                            checked = policy.isEnabled,
                            onCheckedChange = {
                                rbacPolicies[index] = policy.copy(isEnabled = it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StatusSuccess,
                                checkedTrackColor = StatusSuccess.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // Cryptographic Audit Log Ledger Header
            item {
                Text(
                    text = "CRYPTOGRAPHIC AUDIT LEDGER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ThinkingMutedSlate
                )
            }

            items(auditLogs, key = { it.id }) { log ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC0E1322),
                    border = BorderStroke(
                        1.dp,
                        if (log.isAllowed) StatusSuccess.copy(alpha = 0.25f) else StatusError.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (log.isAllowed) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (log.isAllowed) StatusSuccess else StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = log.action,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${log.time} • Caller: ${log.caller}",
                                    color = ThinkingMutedSlate,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                )
                            }
                        }

                        Text(
                            text = log.decisionHash,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = if (log.isAllowed) StatusSuccess else StatusError
                        )
                    }
                }
            }
        }
    }
}
