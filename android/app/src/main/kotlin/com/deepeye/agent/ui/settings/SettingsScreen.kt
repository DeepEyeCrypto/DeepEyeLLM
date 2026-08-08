package com.deepeye.agent.ui.settings

import androidx.compose.ui.graphics.Color
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
                title = { Text("Settings", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            ) 
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Privacy") }

            item {
                SettingCard {
                    SettingToggleRow(
                        icon = Icons.Default.Lock,
                        title = "Offline-only mode",
                        subtitle = "All analysis stays on-device.",
                        checked = state.offlineMode,
                        onCheckedChange = onOfflineModeChange
                    )
                    HorizontalDivider()
                    SettingToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "Policy checks",
                        subtitle = "Validate local safety rules before analysis.",
                        checked = state.policyCheckEnabled,
                        onCheckedChange = onPolicyCheckChange
                    )
                    HorizontalDivider()
                    SettingToggleRow(
                        icon = Icons.Default.Info,
                        title = "Auto-update skills",
                        subtitle = "Sync local skill updates when available.",
                        checked = state.autoUpdateSkills,
                        onCheckedChange = onAutoUpdateSkillsChange
                    )
                }
            }

            item { SectionHeader("Inference Engine Options") }

            item {
                SettingCard {
                    SettingToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "Hardware Acceleration (Vulkan GPU)",
                        subtitle = "Enable Vulkan GPU offloading for faster tokens/sec.",
                        checked = state.engineSettings.useGpu,
                        onCheckedChange = { viewModel.updateUseGpu(it) }
                    )
                    HorizontalDivider()

                    // CPU Threads Slider
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("CPU Inference Threads: ${state.engineSettings.cpuThreads}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Range: 1 to ${Runtime.getRuntime().availableProcessors()} cores (Recommended: 4 for ARM big.LITTLE)", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        Slider(
                            value = state.engineSettings.cpuThreads.toFloat(),
                            onValueChange = { viewModel.updateCpuThreads(it.toInt()) },
                            valueRange = 1f..Runtime.getRuntime().availableProcessors().toFloat(),
                            steps = Runtime.getRuntime().availableProcessors() - 1,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF64B5F6))
                        )
                    }
                    HorizontalDivider()

                    // Context Window Size Selection
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Context Window Size: ${state.engineSettings.contextSize} tokens", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(512, 1024, 2048, 4096).forEach { size ->
                                FilterChip(
                                    selected = state.engineSettings.contextSize == size,
                                    onClick = { viewModel.updateContextSize(size) },
                                    label = { Text("$size") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF64B5F6),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.DarkGray,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    HorizontalDivider()

                    // Temperature Slider
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Temperature: ${"%.2f".format(state.engineSettings.temperature)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Slider(
                            value = state.engineSettings.temperature,
                            onValueChange = { viewModel.updateTemperature(it) },
                            valueRange = 0.0f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF64B5F6))
                        )
                    }
                    HorizontalDivider()

                    // Top-P Slider
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Top-P (Nucleus Sampling): ${"%.2f".format(state.engineSettings.topP)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Slider(
                            value = state.engineSettings.topP,
                            onValueChange = { viewModel.updateTopP(it) },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF64B5F6))
                        )
                    }
                }
            }

            item { SectionHeader("Security") }

            item {
                SettingCard {
                    SettingToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "Diagnostics and logs",
                        subtitle = "Collect crash reports and local traces.",
                        checked = state.diagnosticsEnabled,
                        onCheckedChange = onDiagnosticsChange
                    )
                }
            }

            item { SectionHeader("Models") }

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
                            Icon(Icons.Default.Storage, contentDescription = null, tint = Color.White)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Downloaded model storage", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text("${state.modelStorageUsed} used of ${state.modelStorageTotal}", color = Color.LightGray)
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Manage Models", color = Color(0xFF64B5F6), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            item { SectionHeader("Diagnostics") }

            item {
                SettingCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Recent logs", color = Color.White)
                        Text("${state.recentLogsCount}", color = Color.LightGray)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = onExportDiagnostics) { Text("Export diagnostics", color = Color(0xFF64B5F6)) }
                        TextButton(onClick = onViewPolicyLogs) { Text("View policy logs", color = Color(0xFF64B5F6)) }
                    }
                }
            }

            state.error?.let { err ->
                item {
                    ElevatedCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Settings error", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = Color(0xFFE57373))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) { Column(content = content) }
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
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF64B5F6),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
