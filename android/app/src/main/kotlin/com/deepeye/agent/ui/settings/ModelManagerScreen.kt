package com.deepeye.agent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                title = { Text("Manage Models", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshCatalog) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Catalog", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
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
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = Color.White) }
                    )
                }
            }
            
            val currentList = when (selectedTabIndex) {
                0 -> installed
                1 -> available
                2 -> unsupported
                else -> failed
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (selectedTabIndex == 0) {
                    item {
                        ImportCustomModelCard(onClick = onImportModel)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                if (currentList.isEmpty() && selectedTabIndex != 0) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                when(selectedTabIndex) {
                                    1 -> "No models available to download. Tap the Sync icon to refresh the catalog."
                                    2 -> "Your device meets the RAM requirements for all listed models."
                                    3 -> "No failed downloads."
                                    else -> "No models found."
                                },
                                color = Color.LightGray,
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
                }
                items(currentList, key = { it.id }) { model ->
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = (model.engineState == EngineState.READY || model.engineState == EngineState.LOADED) && !isUnsupported,
                onClick = onSelect
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = model.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = model.publisher, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64B5F6))
                        Text(text = model.sizeString, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        if (model.isChinese) {
                            Text(text = "ZH/EN", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D))
                        }
                    }
                    if (isUnsupported) {
                        Text(
                            text = "Requires ${model.requiredRamGb}GB RAM",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE57373)
                        )
                    } else if (model.engineState == EngineState.FAILED) {
                        Text(
                            text = "Error: ${model.lastError.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE57373)
                        )
                    } else {
                        Text(
                            text = model.category.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    }
                }
                
                if (!isUnsupported) {
                    when (model.engineState) {
                        EngineState.NOT_DOWNLOADED, EngineState.FAILED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onDownload,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                ) {
                                    Text("Download", color = Color.White)
                                }
                            }
                        }
                        EngineState.DOWNLOADING, EngineState.VERIFYING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress = { model.downloadProgress },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = onPause) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color(0xFFFFB74D))
                                }
                                IconButton(onClick = onCancel) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFE57373))
                                }
                            }
                        }
                        EngineState.PAUSED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(model.downloadProgress * 100).toInt()}% Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFB74D)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = onResume) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color(0xFF00E676))
                                }
                                IconButton(onClick = onCancel) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFE57373))
                                }
                            }
                        }
                        EngineState.READY, EngineState.LOADED, EngineState.LOADING, EngineState.DOWNLOADED -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = onSelect,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                ) {
                                    Text("Activate", color = Color.Black)
                                }
                                IconButton(onClick = onDelete) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5).copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Import Custom Local Model",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    text = "Load a .bin or .gguf model directly from device storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}
