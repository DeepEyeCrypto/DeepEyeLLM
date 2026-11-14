package com.deepeye.agent.ui.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.core.hardware.MemoryFitLevel
import com.deepeye.agent.core.model.ModelBackend
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.CyberCyan
import com.deepeye.agent.ui.theme.DeepEyeTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(
    viewModel: ModelManagerViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToBenchmark: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF070A12),
        topBar = {
            Surface(
                color = Color(0xF2070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model Provisioning Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Background GGUF & Colibri Downloader (${uiState.totalDeviceRamGb}GB RAM)",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                    IconButton(onClick = { viewModel.refreshCatalog(forceRefresh = true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyberCyan
                        )
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Import Model",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        ModelHubScreenContent(
            uiState = uiState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            onSearchQueryChange = viewModel::setSearchQuery,
            onFamilyFilterSelected = viewModel::setSelectedFamily,
            onDownload = viewModel::downloadModel,
            onPause = viewModel::pauseDownload,
            onResume = viewModel::resumeDownload,
            onCancel = viewModel::cancelDownload,
            onDelete = viewModel::deleteModel,
            onActivate = viewModel::selectModel,
            onBenchmark = onNavigateToBenchmark,
            onImport = { filePicker.launch(arrayOf("*/*")) }
        )
    }
}
@Composable
fun ModelHubScreenContent(
    uiState: ModelHubUiState,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit,
    onFamilyFilterSelected: (String) -> Unit,
    onDownload: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onActivate: (String) -> Unit,
    onBenchmark: () -> Unit,
    onImport: () -> Unit
) {
    val filterFamilies = listOf("ALL", "INSTALLED", "LLAMA", "QWEN", "HERMES", "DEEPSEEK", "COLIBRI")

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hardware RAM summary banner
        item(key = "hardware_banner") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CyberCyan.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = "RAM",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unified Memory: ${uiState.totalDeviceRamGb} GB RAM",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Recommended single model footprint: ≤ ${(uiState.totalDeviceRamGb * 0.75).toInt()} GB",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Search bar
        item(key = "search_field") {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Search models (e.g. Llama 3.2, Qwen 2.5, 14B)...", color = Color.White.copy(alpha = 0.5f))
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0x33000000),
                    unfocusedContainerColor = Color(0x22000000),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Filter chips
        item(key = "filter_chips") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterFamilies) { family ->
                    val isSelected = uiState.selectedFamilyFilter.equals(family, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFamilyFilterSelected(family) },
                        label = { Text(family, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White.copy(alpha = 0.7f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }

        // Model Cards List
        items(
            items = uiState.models,
            key = { it.spec.id }
        ) { item ->
            ModelHubItemCard(
                item = item,
                onDownload = { onDownload(item.spec.id) },
                onPause = { onPause(item.spec.id) },
                onResume = { onResume(item.spec.id) },
                onCancel = { onCancel(item.spec.id) },
                onDelete = { onDelete(item.spec.id) },
                onActivate = { onActivate(item.spec.id) },
                onBenchmark = onBenchmark
            )
        }

        // Custom model import footer card
        item(key = "import_card") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onImport),
                borderColor = CyberCyan.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = "Import",
                        tint = CyberCyan,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Import Custom Model (.gguf / .bin / .coli)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Load custom weights directly from device storage or SD card",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelHubItemCard(
    item: ModelHubItem,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    onBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = item.downloadStatus is ModelDownloadStatus.Downloading
    val isPaused = item.downloadStatus is ModelDownloadStatus.Paused

    val borderColor = when {
        item.isActiveModel -> DeepEyeTheme.colors.statusSuccess.copy(alpha = 0.8f)
        isDownloading -> CyberCyan.copy(alpha = 0.8f)
        isPaused -> Color(0xFFFFA726).copy(alpha = 0.6f)
        item.isInstalled -> CyberCyan.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        borderColor = borderColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.spec.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.isActiveModel) {
                            NeonStatusBadge(text = "ACTIVE", color = DeepEyeTheme.colors.statusSuccess)
                        }
                    }
                    Text(
                        text = "${item.spec.family} • ${item.spec.parameterCount} Params • ${item.spec.quantization.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // RAM Hardware Fit Badge
                val (fitText, fitColor) = when (item.memoryFitLevel) {
                    MemoryFitLevel.PERFECT_FIT -> "Optimal Fit" to DeepEyeTheme.colors.statusSuccess
                    MemoryFitLevel.MODERATE_LOAD -> "Moderate Load" to Color(0xFFFFA726)
                    MemoryFitLevel.HAZARDOUS_OOM_RISK -> "Exceeds RAM" to DeepEyeTheme.colors.statusError
                }
                NeonStatusBadge(text = fitText, color = fitColor)
            }

            // Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sizeDisplay = if (item.isInstalled && item.installedSizeBytes > 0) {
                    "%.1f GB".format(Locale.US, item.installedSizeBytes / (1024.0 * 1024.0 * 1024.0))
                } else {
                    "%.1f GB".format(Locale.US, item.spec.sizeBytes / (1024.0 * 1024.0 * 1024.0))
                }
                val ramDisplay = "%.1f GB".format(Locale.US, item.spec.requiredRamBytes / (1024.0 * 1024.0 * 1024.0))

                ModelMetricPill(icon = Icons.Default.Folder, label = "Disk: $sizeDisplay")
                ModelMetricPill(icon = Icons.Default.Memory, label = "RAM: $ramDisplay")
                ModelMetricPill(
                    icon = Icons.Default.Terminal,
                    label = if (item.spec.backend == ModelBackend.COLIBRI) "Colibri MoE" else "GGUF llama.cpp"
                )
            }

            // Action / Download / Progress area
            ModelActionSection(
                item = item,
                onDownload = onDownload,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onDelete = onDelete,
                onActivate = onActivate,
                onBenchmark = onBenchmark
            )
        }
    }
}

@Composable
private fun ModelMetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ModelActionSection(
    item: ModelHubItem,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    onBenchmark: () -> Unit
) {
    when (val status = item.downloadStatus) {
        is ModelDownloadStatus.Idle -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan.copy(alpha = 0.85f),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Download", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        is ModelDownloadStatus.Queued -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = CyberCyan
                    )
                    Text("Queued in background...", fontSize = 12.sp, color = CyberCyan)
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        is ModelDownloadStatus.Downloading -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlowingCircularProgress(progress = status.progressFraction)
                        Column {
                            Text(
                                text = "${status.progressPercent}% • ${formatSpeed(status.speedKbps)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            val downloadedMb = status.downloadedBytes / (1024 * 1024)
                            val totalMb = status.totalBytes / (1024 * 1024)
                            Text(
                                text = "$downloadedMb MB / $totalMb MB • ${formatEta(status.etaSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = CyberCyan)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { status.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = CyberCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }

        is ModelDownloadStatus.Paused -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Paused (${status.progressPercent}%) • Range Resume Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFFA726)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = CyberCyan)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { (status.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFFFA726),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }

        is ModelDownloadStatus.Completed -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Installed & Verified",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepEyeTheme.colors.statusSuccess
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onBenchmark,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Benchmark", fontSize = 11.sp)
                    }
                    if (!item.isActiveModel) {
                        Button(
                            onClick = onActivate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepEyeTheme.colors.statusSuccess,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Activate", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                    }
                }
            }
        }

        is ModelDownloadStatus.Failed -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Failed: ${status.error}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepEyeTheme.colors.statusError,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = DeepEyeTheme.colors.statusError),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Retry", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun GlowingCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = CyberCyan.copy(alpha = glowAlpha),
                    spotColor = CyberCyan
                ),
            strokeWidth = 3.dp,
            color = CyberCyan,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun formatSpeed(kbps: Int): String = when {
    kbps >= 1024 -> "%.1f MiB/s".format(Locale.US, kbps / 1024.0)
    kbps >= 1 -> "$kbps KiB/s"
    else -> "— KiB/s"
}

private fun formatEta(seconds: Int): String = when {
    seconds <= 0 -> "—"
    seconds < 60 -> "${seconds}s left"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
}

