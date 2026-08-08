package com.deepeye.agent.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.domain.EngineState
import com.deepeye.agent.domain.LocalModel
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.models.ModelCatalogViewModel
import com.deepeye.agent.ui.theme.PrimaryLocal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    modelCatalogViewModel: ModelCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelCatalog by modelCatalogViewModel.modelCatalog.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.analyzeFile(it) }
    }
    val debugPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.deepDebugFile(it) }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { modelCatalogViewModel.importModel(it) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { 
            DeepEyeTopAppBar(
                modelStatus = state.modelStatus, 
                activeModelName = state.activeModelName, 
                onPickerClick = { viewModel.toggleModelPicker(true) }
            ) 
        },
        bottomBar = {
            ChatBottomBar(
                prompt = state.prompt,
                onPromptChange = viewModel::onPromptChange,
                onSend = viewModel::sendPrompt,
                onAnalyze = { filePicker.launch(arrayOf("*/*")) },
                onDebug = { debugPicker.launch(arrayOf("*/*")) },
                isLoading = state.isLoading
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val err = state.error
            if (state.modelStatus == ModelStatus.ERROR && err != null) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            ChatTranscript(messages = state.messages)
        }

        if (state.showModelPicker) {
            ModelPickerSheet(
                currentStatus = state.modelStatus,
                models = modelCatalog,
                onSelect = modelCatalogViewModel::selectModel,
                onImportModel = { modelPicker.launch(arrayOf("*/*")) },
                onDismiss = { viewModel.toggleModelPicker(false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepEyeTopAppBar(modelStatus: ModelStatus, activeModelName: String, onPickerClick: () -> Unit) {
    val isLiteRT = activeModelName.startsWith("LiteRT", ignoreCase = true)

    val statusColor = if (isLiteRT) Color(0xFF00E676) else PrimaryLocal
    val statusText = if (isLiteRT) "LiteRT Active" else "DeepEye Local"
    val engineState = "Active"

    TopAppBar(
        title = { 
            Column {
                Text("DeepEyeLLM", color = Color.White)
                Text("Engine: $activeModelName | State: $engineState", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
        },
        actions = {
            FilterChip(
                selected = true,
                onClick = onPickerClick,
                label = { Text(statusText, color = Color.White) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = statusColor.copy(alpha = 0.35f))
            )
        }
    )
}

@Composable
fun ChatTranscript(messages: List<Message>) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )
    val indicatorColor = when (message.modelStatus) {
        ModelStatus.LOCAL_ACTIVE -> PrimaryLocal
        ModelStatus.ERROR -> MaterialTheme.colorScheme.error
        ModelStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.tertiary
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        ElevatedCard(
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = bgColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(indicatorColor, RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (message.modelStatus == ModelStatus.LOCAL_ACTIVE) "Local AI" else "Error",
                            style = MaterialTheme.typography.labelSmall,
                            color = indicatorColor
                        )
                    }
                }
                Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ChatBottomBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onAnalyze: () -> Unit,
    onDebug: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1B1B26).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    val canSend = !isLoading && prompt.isNotBlank()
                    TextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ask DeepEye anything...", color = Color.Gray) },
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = onAnalyze,
                                enabled = !isLoading,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Analyze", color = Color.White, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color.White.copy(alpha = 0.15f))
                            )

                            FilterChip(
                                selected = false,
                                onClick = onDebug,
                                enabled = !isLoading,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Deep Debug", color = Color.White, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color.White.copy(alpha = 0.15f))
                            )
                        }

                        val canSend = !isLoading && prompt.isNotBlank()
                        IconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (canSend) PrimaryLocal else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (canSend) Color.Black else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    currentStatus: ModelStatus,
    models: List<LocalModel>,
    onSelect: (String) -> Unit,
    onImportModel: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Select Intelligence Layer", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onImportModel()
                        onDismiss()
                    },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E88E5).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Import Custom Local Model (.bin)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Load LiteRT flatbuffer binary from storage", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(models, key = { it.id }) { model ->
                    val isReady = model.engineState == EngineState.READY || 
                                  model.engineState == EngineState.LOADED
                    val isDownloadedButUnsupported = model.engineState == EngineState.DOWNLOADED && !model.isSupportedOnDevice
                    
                    val dotColor = when {
                        isReady -> PrimaryLocal
                        isDownloadedButUnsupported -> Color(0xFFFFB74D)  // orange
                        else -> Color.Gray
                    }
                    val statusText = when {
                        isReady -> "${model.publisher} • Ready on-device"
                        isDownloadedButUnsupported -> "${model.publisher}"
                        else -> "${model.publisher} • Tap to download"
                    }
                    val statusColor = when {
                        isReady -> PrimaryLocal
                        isDownloadedButUnsupported -> Color(0xFFFFB74D)
                        else -> Color.Gray
                    }

                    ListItem(
                        modifier = Modifier.clickable {
                            onSelect(model.id)
                            if (isReady) onDismiss()
                        },
                        headlineContent = { Text(model.name, color = Color.White) },
                        supportingContent = { 
                            Text(text = statusText, color = statusColor) 
                        },
                        leadingContent = { 
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(dotColor, RoundedCornerShape(50))
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
