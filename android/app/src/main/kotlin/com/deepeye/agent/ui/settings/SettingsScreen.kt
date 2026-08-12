package com.deepeye.agent.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepeye.agent.ui.theme.DeepEyeTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.deepeye.agent.ui.components.GlassCard


@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToModelManager: () -> Unit = {}
) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onOfflineModeChange = viewModel::toggleOfflineMode,
        onAutoUpdateSkillsChange = viewModel::toggleAutoUpdate,
        onPolicyCheckChange = viewModel::togglePolicyChecks,
        onDiagnosticsChange = viewModel::toggleDiagnostics,
        onManageModels = onNavigateToModelManager,
        onExportDiagnostics = { },
        onViewPolicyLogs = { },
        viewModel = viewModel
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOfflineModeChange: (Boolean) -> Unit,
    onAutoUpdateSkillsChange: (Boolean) -> Unit,
    onPolicyCheckChange: (Boolean) -> Unit,
    onDiagnosticsChange: (Boolean) -> Unit,
    onManageModels: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onViewPolicyLogs: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { 
            @OptIn(ExperimentalMaterial3Api::class) 
            TopAppBar(
                title = { Text("Settings", color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            ) 
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader("Security & Policy") }
            item {
                SettingCard {
                    SettingToggleRow(
                        icon = Icons.Default.Lock,
                        title = "Offline Mode",
                        subtitle = "All analysis stays on-device.",
                        checked = state.offlineMode,
                        onCheckedChange = onOfflineModeChange
                    )
                    HorizontalDivider()
                    SettingToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "RBAC Checks",
                        subtitle = "Validate local safety rules before analysis.",
                        checked = state.policyCheckEnabled,
                        onCheckedChange = onPolicyCheckChange
                    )
                    HorizontalDivider()
                    SettingToggleRow(
                        icon = Icons.Default.Info,
                        title = "Policy Audit Logging",
                        subtitle = "Collect security events and traces.",
                        checked = state.diagnosticsEnabled,
                        onCheckedChange = onDiagnosticsChange
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onViewPolicyLogs) { Text("View Policy Logs", color = DeepEyeTheme.colors.link) }
                    }
                }
            }

            item { SectionHeader("Performance & AI Engine") }
            item {
                SettingCard {
                    SettingToggleRow(
                        icon = Icons.Default.Info,
                        title = "Auto Update Skills",
                        subtitle = "Sync local skill updates when available.",
                        checked = state.autoUpdateSkills,
                        onCheckedChange = onAutoUpdateSkillsChange
                    )
                    HorizontalDivider()

                    SettingToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "Hardware Acceleration (Vulkan GPU)",
                        subtitle = "Enable Vulkan GPU offloading for faster tokens/sec.",
                        checked = state.engineSettings.useGpu,
                        onCheckedChange = { viewModel.updateUseGpu(it) }
                    )
                    HorizontalDivider()

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("CPU Inference Threads: ${state.engineSettings.cpuThreads}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Range: 1 to ${Runtime.getRuntime().availableProcessors()} cores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = state.engineSettings.cpuThreads.toFloat(),
                            onValueChange = { viewModel.updateCpuThreads(it.toInt()) },
                            valueRange = 1f..Runtime.getRuntime().availableProcessors().toFloat(),
                            steps = Runtime.getRuntime().availableProcessors() - 1,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface, activeTrackColor = DeepEyeTheme.colors.link)
                        )
                    }
                    HorizontalDivider()

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Context Window Size: ${state.engineSettings.contextSize} tokens", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(512, 1024, 2048, 4096).forEach { size ->
                                FilterChip(
                                    selected = state.engineSettings.contextSize == size,
                                    onClick = { viewModel.updateContextSize(size) },
                                    label = { Text("$size") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DeepEyeTheme.colors.link,
                                        selectedLabelColor = MaterialTheme.colorScheme.scrim,
                                        containerColor = MaterialTheme.colorScheme.outlineVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Diagnostics Monitor (Logs: ${state.recentLogsCount})", color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = onExportDiagnostics) { Text("Export", color = DeepEyeTheme.colors.link) }
                    }
                }
            }

            item { SectionHeader("Storage & System Models") }
            item {
                SettingCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManageModels() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = "Model storage", tint = MaterialTheme.colorScheme.onSurface)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Storage Breakdown", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text("${state.modelStorageUsed} used of ${state.modelStorageTotal}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Model Management", color = DeepEyeTheme.colors.link, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            state.error?.let { err ->
                item {
                    ElevatedCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Settings error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = DeepEyeTheme.colors.dangerAlt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1AFFFFFF))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurface)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00E5FF),
                checkedTrackColor = DeepEyeTheme.colors.link,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
