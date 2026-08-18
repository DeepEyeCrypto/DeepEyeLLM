package com.deepeye.agent.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.ui.theme.CyberCyan
import com.deepeye.agent.ui.components.GlassCard
import androidx.compose.animation.core.*
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.deepeye.agent.core.hardware.HardwareBackendSelector
import androidx.compose.foundation.BorderStroke

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
        onCheckForUpdates = viewModel::checkForUpdates,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onCheckForUpdates: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
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
                Column {
                    Text(
                        text = "System & Engine Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Hardware Delegates, Zero-Blur Mode, Policy & Telemetry",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberCyan
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security & Policy
            item { FuturisticSectionHeader("Security & Policy", Icons.Default.Security) }
            item {
                FuturisticSettingCard {
                    FuturisticToggleRow(
                        icon = Icons.Default.Lock,
                        title = "Offline Mode",
                        subtitle = "All analysis stays on-device",
                        checked = state.offlineMode,
                        onCheckedChange = onOfflineModeChange,
                        accentColor = DeepEyeTheme.colors.link
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "RBAC Checks",
                        subtitle = "Validate local safety rules",
                        checked = state.policyCheckEnabled,
                        onCheckedChange = onPolicyCheckChange,
                        accentColor = DeepEyeTheme.colors.statusSuccess
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticToggleRow(
                        icon = Icons.Default.Info,
                        title = "Policy Audit Logging",
                        subtitle = "Collect security events",
                        checked = state.diagnosticsEnabled,
                        onCheckedChange = onDiagnosticsChange,
                        accentColor = DeepEyeTheme.colors.statusWarning
                    )
                    Spacer(Modifier.height(8.dp))
                    FuturisticActionButton(
                        text = "View Policy Logs",
                        onClick = onViewPolicyLogs,
                        icon = Icons.Default.Info
                    )
                }
            }

            // Performance & AI Engine
            item { FuturisticSectionHeader("Performance & AI Engine", Icons.Default.Memory) }
            item {
                FuturisticSettingCard {
                    FuturisticToggleRow(
                        icon = Icons.Default.Speed,
                        title = "Zero-Blur & Performance Mode",
                        subtitle = "Bypass GPU shader loops for ultra-low latency",
                        checked = true,
                        onCheckedChange = { },
                        accentColor = com.deepeye.agent.ui.theme.CyberCyan
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticToggleRow(
                        icon = Icons.Default.Tune,
                        title = "Auto Update Skills",
                        subtitle = "Sync local skill updates",
                        checked = state.autoUpdateSkills,
                        onCheckedChange = onAutoUpdateSkillsChange,
                        accentColor = DeepEyeTheme.colors.link
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticHardwareBackendSection(
                        useGpu = state.engineSettings.useGpu,
                        onUseGpuChange = { viewModel.updateUseGpu(it) },
                        selectedBackend = state.engineSettings.selectedBackend,
                        onBackendSelected = { viewModel.updateSelectedBackend(it) },
                        gpuLayers = state.engineSettings.gpuLayers,
                        onGpuLayersChange = { viewModel.updateGpuLayers(it) },
                        accentColor = DeepEyeTheme.colors.accent
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticSliderControl(
                        title = "CPU Inference Threads",
                        subtitle = "Range: 1 to ${Runtime.getRuntime().availableProcessors()} cores",
                        value = state.engineSettings.cpuThreads.toFloat(),
                        onValueChange = { viewModel.updateCpuThreads(it.toInt()) },
                        valueRange = 1f..Runtime.getRuntime().availableProcessors().toFloat(),
                        steps = Runtime.getRuntime().availableProcessors() - 1,
                        accentColor = DeepEyeTheme.colors.link
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticChipSelector(
                        title = "Context Window Size",
                        options = listOf(512, 1024, 2048, 4096, 8192),
                        selectedValue = state.engineSettings.contextSize,
                        onOptionSelected = { viewModel.updateContextSize(it) },
                        accentColor = DeepEyeTheme.colors.accent
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticStringChipSelector(
                        title = "KV-Cache Quantization",
                        options = listOf("FP16", "Q8_0", "Q4_0"),
                        selectedValue = state.engineSettings.kvCacheQuant,
                        onOptionSelected = { viewModel.updateKvCacheQuant(it) },
                        accentColor = DeepEyeTheme.colors.link
                    )
                    HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                    FuturisticActionRow(
                        title = "Diagnostics Monitor",
                        subtitle = "Logs: ${state.recentLogsCount}",
                        actionText = "Export",
                        onActionClick = onExportDiagnostics,
                        icon = Icons.Default.Info
                    )
                }
            }

            // Storage & System Models
            item { FuturisticSectionHeader("Storage & System Models", Icons.Default.Storage) }
            item {
                FuturisticSettingCard(onClick = onManageModels) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = "Model storage",
                                tint = DeepEyeTheme.colors.link,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Storage Breakdown",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${state.modelStorageUsed} / ${state.modelStorageTotal}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Manage",
                                tint = DeepEyeTheme.colors.link
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Model Management",
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepEyeTheme.colors.link
                        )
                    }
                }
            }

            // System Updates
            item { FuturisticSectionHeader("System Updates", Icons.Default.SystemUpdate) }
            item {
                FuturisticSettingCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCheckForUpdates() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = "Check for Updates",
                                tint = DeepEyeTheme.colors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Check for Updates",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Current: v${com.deepeye.agent.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Check",
                            tint = DeepEyeTheme.colors.link
                        )
                    }
                }
            }

            // Error state
            state.error?.let { err ->
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.error,
                                        DeepEyeTheme.colors.dangerAlt
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Settings Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
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
private fun FuturisticSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = DeepEyeTheme.colors.link,
            modifier = Modifier.size(24.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun FuturisticSettingCard(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                ),
            content = content
        )
    }
}

@Composable
private fun FuturisticToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    glowEnabled: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (checked && glowEnabled) {
                    accentColor.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                }
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (checked) accentColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun FuturisticSliderControl(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accentColor: Color
) {
    var sliderPosition by remember(value) { androidx.compose.runtime.mutableStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "$title: ${sliderPosition.toInt()}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onValueChange(sliderPosition) },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                activeTickColor = accentColor.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                inactiveTickColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.height(40.dp)
        )
    }
}

@Composable
private fun FuturisticChipSelector(
    title: String,
    options: List<Int>,
    selectedValue: Int,
    onOptionSelected: (Int) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "$title: $selectedValue tokens",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { size ->
                FilterChip(
                    selected = selectedValue == size,
                    onClick = { onOptionSelected(size) },
                    label = { Text("$size") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = MaterialTheme.colorScheme.scrim,
                        containerColor = MaterialTheme.colorScheme.outlineVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
private fun FuturisticStringChipSelector(
    title: String,
    options: List<String>,
    selectedValue: String,
    onOptionSelected: (String) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "$title: $selectedValue",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = selectedValue == opt,
                    onClick = { onOptionSelected(opt) },
                    label = { Text(opt) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = MaterialTheme.colorScheme.scrim,
                        containerColor = MaterialTheme.colorScheme.outlineVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
private fun FuturisticActionRow(
    title: String,
    subtitle: String,
    actionText: String,
    onActionClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = DeepEyeTheme.colors.link,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onActionClick) {
            Text(
                actionText,
                color = DeepEyeTheme.colors.link,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun FuturisticActionButton(
    text: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = DeepEyeTheme.colors.link,
                containerColor = Color.Transparent
            ),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        DeepEyeTheme.colors.link,
                        DeepEyeTheme.colors.accent
                    )
                )
            )
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = DeepEyeTheme.colors.link
            )
            Spacer(Modifier.width(8.dp))
            Text(text)
        }
    }
}

@Composable
private fun FuturisticHardwareBackendSection(
    useGpu: Boolean,
    onUseGpuChange: (Boolean) -> Unit,
    selectedBackend: Int,
    onBackendSelected: (Int) -> Unit,
    gpuLayers: Int,
    onGpuLayersChange: (Int) -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    val options = remember(context) { HardwareBackendSelector.getAvailableBackends(context) }

    Column(modifier = Modifier.fillMaxWidth()) {
        FuturisticToggleRow(
            icon = Icons.Default.Tune,
            title = "Hardware Acceleration",
            subtitle = "Offload LLM layers to NPU / GPU / Vector hardware",
            checked = useGpu,
            onCheckedChange = onUseGpuChange,
            accentColor = accentColor,
            glowEnabled = true
        )

        if (useGpu) {
            HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Hardware Compute Backend",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Select target acceleration architecture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                options.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { option ->
                            val isSelected = selectedBackend == option.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onBackendSelected(option.id) },
                                label = {
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = option.isAvailable,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black,
                                    containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = Color.Transparent,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            )
                        }
                        if (rowOptions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            HorizontalDivider(color = DeepEyeTheme.colors.glassBorder)

            FuturisticSliderControl(
                title = "Hardware Offloaded Layers",
                subtitle = "Model layers pushed to accelerator (0 - 99)",
                value = gpuLayers.toFloat(),
                onValueChange = { onGpuLayersChange(it.toInt()) },
                valueRange = 0f..99f,
                steps = 98,
                accentColor = accentColor
            )
        }
    }
}
