package com.deepeye.agent.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deepeye.agent.domain.EngineState
import com.deepeye.agent.domain.DownloadError
import com.deepeye.agent.domain.LocalModel
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.ui.utils.PerformanceUtils
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

import androidx.compose.runtime.getValue
import com.deepeye.agent.ui.models.ModelCatalogViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ModelManagerScreen(
    viewModel: ModelCatalogViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val modelCatalog by viewModel.modelCatalog.collectAsStateWithLifecycle()
    ModelManagerScreen(
        availableModels = modelCatalog,
        onBack = onBack,
        onDownloadModel = viewModel::downloadModel,
        onDeleteModel = viewModel::deleteModel,
        onSelectModel = viewModel::selectModel,
        onImportModel = { },
        onRescanModels = { }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    availableModels: List<LocalModel>,
    onBack: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onImportModel: () -> Unit,
    onRescanModels: () -> Unit,
    onCancelDownload: (String) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onRefreshCatalog: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Installed", "Available", "Unsupported", "Failed")
    val layoutMode = currentUiLayoutMode()

    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceRamGb = remember(context) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val ramGb = (mi.totalMem / (1024 * 1024 * 1024)).toInt()
        if (ramGb > 0) ramGb else 8
    }

    val installed = remember(availableModels, deviceRamGb) { availableModels.filter { (it.engineState == EngineState.READY || it.engineState == EngineState.LOADED) && it.requiredRamGb <= deviceRamGb } }
    val available = remember(availableModels, deviceRamGb) { availableModels.filter { (it.engineState == EngineState.NOT_DOWNLOADED || it.engineState == EngineState.DOWNLOADING || it.engineState == EngineState.PAUSED || it.engineState == EngineState.VERIFYING) && it.requiredRamGb <= deviceRamGb } }
    val unsupported = remember(availableModels, deviceRamGb) { availableModels.filter { it.requiredRamGb > deviceRamGb || !it.isSupportedOnDevice } }
    val failed = remember(availableModels, deviceRamGb) { availableModels.filter { it.engineState == EngineState.FAILED && it.requiredRamGb <= deviceRamGb } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Manage Models", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshCatalog) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Catalog", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = MaterialTheme.colorScheme.onSurface) }
                    )
                }
            }

            // Storage Footprint Meter
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val totalUsed = 11.8f
                val totalMax = 128f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Storage Footprint", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalUsed GB / $totalMax GB Used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { totalUsed / totalMax },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            val currentList = when (selectedTabIndex) {
                0 -> installed
                1 -> available
                2 -> unsupported
                else -> failed
            }

            val gridColumns = when (layoutMode) {
                UiLayoutMode.COMPACT -> 1
                UiLayoutMode.MEDIUM -> 2
                UiLayoutMode.EXPANDED -> 3
            }

            if (gridColumns == 1) {
                // Compact: LazyColumn
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (selectedTabIndex == 0) {
                        item { ImportCustomModelCard(onClick = onImportModel) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (currentList.isEmpty() && selectedTabIndex != 0) {
                        item { EmptyStateContent(selectedTabIndex, onRefreshCatalog) }
                    }
                    items(
                        items = currentList,
                        key = { it.id },
                        contentType = { PerformanceUtils.ContentTypes.MODEL_CARD }
                    ) { model ->
                        ModelItemCard(
                            model = model,
                            onDownload = { onDownloadModel(model.id) },
                            onDelete = { onDeleteModel(model.id) },
                            onSelect = { onSelectModel(model.id) },
                            onCancel = { onCancelDownload(model.id) },
                            onPause = { onPauseDownload(model.id) },
                            onResume = { onResumeDownload(model.id) },
                            isUnsupported = selectedTabIndex == 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                // Medium/Expanded: LazyVerticalGrid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTabIndex == 0) {
                        item { ImportCustomModelCard(onClick = onImportModel) }
                    }

                    if (currentList.isEmpty() && selectedTabIndex != 0) {
                        item { EmptyStateContent(selectedTabIndex, onRefreshCatalog) }
                    }

                    items(
                        items = currentList,
                        key = { it.id },
                        contentType = { PerformanceUtils.ContentTypes.MODEL_CARD }
                    ) { model ->
                        ModelItemCard(
                            model = model,
                            onDownload = { onDownloadModel(model.id) },
                            onDelete = { onDeleteModel(model.id) },
                            onSelect = { onSelectModel(model.id) },
                            onCancel = { onCancelDownload(model.id) },
                            onPause = { onPauseDownload(model.id) },
                            onResume = { onResumeDownload(model.id) },
                            isUnsupported = selectedTabIndex == 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateContent(selectedTabIndex: Int, onRefreshCatalog: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            when (selectedTabIndex) {
                1 -> "No models available to download. Tap the Sync icon to refresh the catalog."
                2 -> "Your device meets the RAM requirements for all listed models."
                3 -> "No failed downloads."
                else -> "No models found."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (selectedTabIndex == 1) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRefreshCatalog) {
                Text("Refresh Catalog")
            }
        }
    }
}

@Composable
fun ModelItemCard(
    model: LocalModel,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    isUnsupported: Boolean
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                enabled = (model.engineState == EngineState.READY || model.engineState == EngineState.LOADED) && !isUnsupported,
                onClick = onSelect
            ),
        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = model.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = model.publisher, style = MaterialTheme.typography.bodySmall, color = DeepEyeTheme.colors.link)
                        Text(text = model.sizeString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (model.isChinese) {
                            Text(text = "ZH/EN", style = MaterialTheme.typography.bodySmall, color = DeepEyeTheme.colors.warningAlt)
                        }
                    }

                    val tierBadge = when {
                        model.name.contains("LiteRT", ignoreCase = true) -> "LiteRT On-Device"
                        model.name.contains("Q4", ignoreCase = true) -> "GGUF Q4_K_M"
                        else -> "Custom"
                    }
                    NeonStatusBadge(text = tierBadge, isPulsing = false)

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isUnsupported) {
                        Text(
                            text = "Requires ${model.requiredRamGb}GB RAM",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepEyeTheme.colors.dangerAlt
                        )
                    } else if (model.engineState == EngineState.FAILED) {
                        Text(
                            text = "Error: ${model.lastError.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepEyeTheme.colors.dangerAlt
                        )
                    } else {
                        Text(
                            text = model.category.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (!isUnsupported) {
                    when (model.engineState) {
                        EngineState.NOT_DOWNLOADED, EngineState.FAILED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onDownload,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Download", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        EngineState.DOWNLOADING, EngineState.VERIFYING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress = { model.downloadProgress },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = onPause) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = DeepEyeTheme.colors.warningAlt)
                                }
                                IconButton(onClick = onCancel) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = DeepEyeTheme.colors.dangerAlt)
                                }
                            }
                        }
                        EngineState.PAUSED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(model.downloadProgress * 100).toInt()}% Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeepEyeTheme.colors.warningAlt
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = onResume) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = DeepEyeTheme.colors.statusSuccess)
                                }
                                IconButton(onClick = onCancel) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = DeepEyeTheme.colors.dangerAlt)
                                }
                            }
                        }
                        EngineState.READY, EngineState.LOADED, EngineState.LOADING, EngineState.DOWNLOADED -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = onSelect,
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepEyeTheme.colors.statusSuccess)
                                ) {
                                    Text("Activate", color = MaterialTheme.colorScheme.scrim)
                                }
                                IconButton(onClick = onDelete) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DeepEyeTheme.colors.dangerAlt)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportCustomModelCard(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = "Download model",
                tint = DeepEyeTheme.colors.link,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Import Custom Local Model",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Load a .bin or .gguf model directly from device storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
