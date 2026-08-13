package com.deepeye.agent.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.DeepEyeTheme

data class AuditLogUi(
    val id: String,
    val action: String,
    val time: String,
    val isAllowed: Boolean
)

@Composable
fun SecurityDashboardScreen() {
    val auditLogs = remember {
        listOf(
            AuditLogUi("1", "File Read: /documents/runbook.txt", "10:42 AM", true),
            AuditLogUi("2", "GGUF Checksum Verified: hermes-3b.gguf", "10:40 AM", true),
            AuditLogUi("3", "Unregistered Tool Call Intercepted", "10:15 AM", false)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Security & RBAC Audit",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zero-Trust Interceptor Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepEyeTheme.colors.link
                    )
                }

                NeonStatusBadge(
                    text = "Enforced",
                    isPulsing = false,
                    color = DeepEyeTheme.colors.statusSuccess
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Recent Policy Audit Logs",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(auditLogs) { log ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (log.isAllowed) DeepEyeTheme.colors.statusSuccess.copy(alpha = 0.3f) else Color(0xFFFFB3B3).copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        tintColor = Color(0xFF151A29).copy(alpha = 0.75f),
                        borderColor = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (log.isAllowed) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (log.isAllowed) DeepEyeTheme.colors.statusSuccess else Color(0xFFFFB3B3),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.action,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${log.time} • Local Sandbox",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
