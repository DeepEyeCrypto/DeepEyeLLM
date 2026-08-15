package com.deepeye.agent.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepeye.agent.core.diagnostics.SystemHealth
import com.deepeye.agent.core.hardware.HardwareBackendSelector
import com.deepeye.agent.core.hardware.HardwareFitEvaluator
import com.deepeye.agent.core.policy.PolicyAuditEntry
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AUDIT_LOG_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val optimalBackend = remember(context) { HardwareBackendSelector.selectOptimalBackend(context) }
    val thermalAdvice = remember(context) { HardwareFitEvaluator.getThermalAdvice(context) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberCyan)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Hardware & Diagnostics",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                "On-Device Hardware Delegate & Thermal State",
                                style = MaterialTheme.typography.bodySmall,
                                color = ThinkingMutedSlate
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.refreshDiagnostics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hardware Acceleration Backend Card
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    tintColor = Color(0xCC0E1322)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Active Hardware Delegate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(optimalBackend.name, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                                }
                            }

                            NeonStatusBadge(
                                text = "Optimal",
                                color = StatusSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x66070A12), shape = RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Thermostat, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                                Text("Thermal State: ${thermalAdvice.thermalStatus}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            }
                            Text(
                                text = "${thermalAdvice.recommendedThreadCap} Threads Max",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = if (thermalAdvice.isThrottled) StatusError else StatusSuccess
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "SYSTEM RESOURCES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CyberCyan
                )
            }
            
            item {
                uiState.systemHealth?.let { health ->
                    HealthCard(health)
                }
            }

            item {
                Text(
                    "POLICY AUDIT STREAM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ThinkingMutedSlate
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    tintColor = Color(0xCC0E1322)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        if (uiState.auditLogs.isEmpty()) {
                            Text("> No security policy violations recorded.", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = ThinkingMutedSlate)
                        } else {
                            uiState.auditLogs.forEach { entry ->
                                AuditLogItem(entry)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthCard(health: SystemHealth) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val ramColor = if (health.isLowMemory) StatusError else StatusSuccess
        val storageColor = if (health.isStorageLow) StatusWarning else StatusSuccess
        
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xCC0E1322),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RAM Available", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                Spacer(modifier = Modifier.height(6.dp))
                NeonStatusBadge(
                    text = "${String.format(Locale.US, "%.1f", health.availableRamGb)}GB",
                    color = ramColor
                )
            }
        }
        
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xCC0E1322),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Storage Free", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                Spacer(modifier = Modifier.height(6.dp))
                NeonStatusBadge(
                    text = "${String.format(Locale.US, "%.1f", health.availableStorageGb)}GB",
                    color = storageColor
                )
            }
        }
        
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xCC0E1322),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Engine Status", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                Spacer(modifier = Modifier.height(6.dp))
                NeonStatusBadge(
                    text = if (health.isLowMemory || health.isStorageLow) "DEGRADED" else "OPTIMAL",
                    color = if (health.isLowMemory || health.isStorageLow) StatusWarning else StatusSuccess
                )
            }
        }
    }
}

@Composable
fun AuditLogItem(entry: PolicyAuditEntry) {
    val time = AUDIT_LOG_DATE_FORMATTER.withZone(ZoneId.systemDefault()).format(entry.timestamp)
    val color = if (entry.allowed) StatusSuccess else StatusError
    val badgeText = if (entry.allowed) "ALLOW" else "BLOCK"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("> [$time]", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = ThinkingMutedSlate)
        NeonStatusBadge(text = badgeText, color = color)
        Column {
            Text(entry.action, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = Color.White)
            if (entry.reason.isNotBlank()) {
                Text(entry.reason, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = ThinkingMutedSlate)
            }
        }
    }
}
