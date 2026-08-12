package com.deepeye.agent.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepeye.agent.core.diagnostics.SystemHealth
import com.deepeye.agent.core.policy.PolicyAuditEntry
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.DeepEyeTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AUDIT_LOG_DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Health", modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDiagnostics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("System Health", style = MaterialTheme.typography.titleLarge)
            }
            
            item {
                uiState.systemHealth?.let { health ->
                    HealthCard(health)
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Upstream Sources", style = MaterialTheme.typography.titleLarge)
            }
            
            item {
                uiState.updateManifest?.let { manifest ->
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Manifest Version: ${manifest.manifestVersion}")
                            Text("Last Sync: ${manifest.lastFullSyncTimestamp ?: "Never"}")
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            manifest.upstreams.forEach { upstream ->
                                Text("${upstream.name}: ${upstream.latestVersion ?: "Unknown"}")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Policy Audit Stream", style = MaterialTheme.typography.titleLarge)
            }

            item {
                GlassCard(tintColor = androidx.compose.ui.graphics.Color(0x88000000)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        if (uiState.auditLogs.isEmpty()) {
                            Text("> No logs recorded yet.", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val ramColor = if (health.isLowMemory) DeepEyeTheme.colors.dangerAlt else DeepEyeTheme.colors.statusSuccess
        val storageColor = if (health.isStorageLow) DeepEyeTheme.colors.warningAlt else DeepEyeTheme.colors.statusSuccess
        
        GlassCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Memory", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                NeonStatusBadge(
                    text = "${String.format(Locale.US, "%.1f", health.availableRamGb)}GB",
                    color = ramColor
                )
            }
        }
        
        GlassCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Storage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                NeonStatusBadge(
                    text = "${String.format(Locale.US, "%.1f", health.availableStorageGb)}GB",
                    color = storageColor
                )
            }
        }
        
        GlassCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Engine", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                NeonStatusBadge(
                    text = if (health.isLowMemory || health.isStorageLow) "DEGRADED" else "ONLINE",
                    color = if (health.isLowMemory || health.isStorageLow) DeepEyeTheme.colors.warningAlt else DeepEyeTheme.colors.statusSuccess
                )
            }
        }
    }
}

@Composable
fun AuditLogItem(entry: PolicyAuditEntry) {
    val time = AUDIT_LOG_DATE_FORMATTER.withZone(ZoneId.systemDefault()).format(entry.timestamp)
    val color = if (entry.allowed) DeepEyeTheme.colors.statusSuccess else DeepEyeTheme.colors.dangerAlt
    val badgeText = if (entry.allowed) "ALLOW" else "DENY"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("> [$time]", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
        NeonStatusBadge(text = badgeText, color = color)
        Column {
            Text(entry.action, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
            if (entry.reason.isNotBlank()) {
                Text(entry.reason, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
