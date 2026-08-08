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
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepeye.agent.core.diagnostics.SystemHealth
import com.deepeye.agent.core.policy.PolicyAuditEntry
import com.deepeye.agent.ui.components.GlassCard
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
                title = { Text("Diagnostics & Health") },
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
                Text("Policy Audit Log", style = MaterialTheme.typography.titleLarge)
            }

            if (uiState.auditLogs.isEmpty()) {
                item {
                    Text("No logs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(uiState.auditLogs, key = { it.hashCode() }) { entry ->
                    AuditLogItem(entry)
                }
            }
        }
    }
}

@Composable
fun HealthCard(health: SystemHealth) {
    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            val ramColor = if (health.isLowMemory) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Text(
                "RAM: ${String.format(Locale.US, "%.1f", health.availableRamGb)} GB / ${String.format(Locale.US, "%.1f", health.totalRamGb)} GB",
                color = ramColor
            )
            
            val storageColor = if (health.isStorageLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Text(
                "Storage: ${String.format(Locale.US, "%.1f", health.availableStorageGb)} GB / ${String.format(Locale.US, "%.1f", health.totalStorageGb)} GB",
                color = storageColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            if (health.isLowMemory || health.isStorageLow) {
                Text(
                    "Warning: Resources are low. Model inference may fail or degrade.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("System is healthy for local inference.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AuditLogItem(entry: PolicyAuditEntry) {
    val time = AUDIT_LOG_DATE_FORMATTER.withZone(ZoneId.systemDefault()).format(entry.timestamp)
    val color = if (entry.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    
    GlassCard(tintColor = color.copy(alpha = 0.1f), borderColor = color.copy(alpha = 0.3f)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("[$time] ${entry.action}", style = MaterialTheme.typography.labelMedium)
                Text(if (entry.allowed) "ALLOW" else "DENY", color = color, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(entry.reason, style = MaterialTheme.typography.bodySmall)
            if (entry.context.isNotEmpty()) {
                Text(
                    entry.context.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
