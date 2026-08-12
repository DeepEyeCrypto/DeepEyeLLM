package com.deepeye.agent.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.domain.EngineState
import com.deepeye.agent.domain.LocalModel
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.models.ModelCatalogViewModel
import com.deepeye.agent.ui.theme.*
import com.deepeye.agent.ui.utils.PerformanceUtils
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    modelCatalogViewModel: ModelCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelCatalog by modelCatalogViewModel.modelCatalog.collectAsStateWithLifecycle()
    val layoutMode = currentUiLayoutMode()

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
            CalmChatInputDock(
                prompt = state.prompt,
                onPromptChange = viewModel::onPromptChange,
                onSend = { viewModel.sendStream() },
                onCancel = viewModel::cancelGeneration,
                onAnalyze = { filePicker.launch(arrayOf("*/*")) },
                onDebug = { debugPicker.launch(arrayOf("*/*")) },
                isLoading = state.isLoading,
                isGenerating = state.isGenerating
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Primary Chat Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (state.isLoading || state.isGenerating) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Loading indicator" },
                        color = Color(0xFF00E5FF),
                        trackColor = Color.Transparent
                    )
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

            // Adaptive Supporting Pane for Medium/Expanded Layouts (Tablet, Foldable, Desktop)
            if (layoutMode != UiLayoutMode.COMPACT) {
                Surface(
                    color = Color(0x660E1322),
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    AgentInspectorPane(
                        activeModelName = state.activeModelName,
                        modelStatus = state.modelStatus
                    )
                }
            }
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

@Composable
fun DeepEyeTopAppBar(
    modelStatus: ModelStatus,
    activeModelName: String,
    onPickerClick: () -> Unit
) {
    val isLiteRT = activeModelName.startsWith("LiteRT", ignoreCase = true)
    val statusText = if (isLiteRT) "LiteRT Active" else "DeepEye Local"

    Surface(
        color = Color(0xDD070A12),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "DeepEyeLLM",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = activeModelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00E5FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Active Engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            NeonStatusBadge(
                text = statusText,
                isPulsing = modelStatus == ModelStatus.LOCAL_ACTIVE,
                onClick = onPickerClick
            )
        }
    }
}

@Composable
fun ChatTranscript(messages: List<Message>) {
    val listState = rememberLazyListState()
    val lastMessageText = messages.lastOrNull()?.text ?: ""
    val lastMessageStreaming = messages.lastOrNull()?.isStreaming ?: false

    LaunchedEffect(messages.size, lastMessageText.length, lastMessageStreaming) {
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
        items(
            items = messages,
            key = { it.id },
            contentType = {
                when {
                    it.isUser -> PerformanceUtils.ContentTypes.CHAT_USER_ROW
                    it.isError -> PerformanceUtils.ContentTypes.CHAT_ERROR_ROW
                    else -> PerformanceUtils.ContentTypes.CHAT_ASSISTANT_ROW
                }
            }
        ) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            GlassCard(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .animateContentSize(),
                shape = shape,
                tintColor = Color(0xCC121826),
                borderColor = Color(0x4D00E5FF)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            val cardTint = when {
                message.isError -> Color(0xCC2A1215)
                else -> Color(0xCC161E2E)
            }
            val cardBorder = when {
                message.isError -> Color(0xFFFF5252)
                message.isStreaming -> Color(0xFF00E5FF)
                else -> Color(0x4D00E676)
            }

            GlassCard(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .animateContentSize(),
                shape = shape,
                tintColor = cardTint,
                borderColor = cardBorder
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        NeonStatusBadge(
                            text = when {
                                message.isError -> "Error"
                                message.isStreaming -> "Streaming..."
                                else -> "DeepEye AI"
                            },
                            isPulsing = message.isStreaming,
                            color = when {
                                message.isError -> Color(0xFFFF5252)
                                message.isStreaming -> Color(0xFF00E5FF)
                                else -> Color(0xFF00E676)
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (message.text.isBlank() && message.isStreaming) "Thinking..." else message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (message.isError) Color(0xFFFF8A80) else Color.White,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (message.isStreaming) {
                            StreamingCursor()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor_transition")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    Text(
        text = " ▌",
        color = Color(0xFF00E5FF).copy(alpha = alpha),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Calm Single-Primary-Action Chat Input Dock with progressive tool disclosure.
 */
@Composable
fun CalmChatInputDock(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAnalyze: () -> Unit,
    onDebug: () -> Unit,
    isLoading: Boolean,
    isGenerating: Boolean
) {
    var isToolsExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
        ) {
            val canSend by remember(isLoading, isGenerating, prompt) {
                derivedStateOf { !isLoading && !isGenerating && prompt.isNotBlank() }
            }
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                tintColor = Color(0xDD0D1322),
                borderColor = Color(0x4D00E5FF)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .fillMaxWidth()
                ) {
                    TextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Ask DeepEye anything...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        maxLines = 4,
                        enabled = !isGenerating,
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Progressive Disclosure Tool Bar
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isToolsExpanded = !isToolsExpanded },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isToolsExpanded) Icons.Default.Info else Icons.Default.Add,
                                    contentDescription = "Toggle tools",
                                    tint = Color(0xFF00E5FF)
                                )
                            }

                            if (isToolsExpanded) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0x1A00E5FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D00E5FF)),
                                    modifier = Modifier.clickable(enabled = !isLoading && !isGenerating, onClick = onAnalyze)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AttachFile, contentDescription = "Analyze", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Analyze", color = Color(0xFF00E5FF), style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0x1A00E676),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D00E676)),
                                    modifier = Modifier.clickable(enabled = !isLoading && !isGenerating, onClick = onDebug)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.BugReport, contentDescription = "Deep Debug", tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Debug", color = Color(0xFF00E676), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        // Single Primary Action (Send / Cancel)
                        if (isGenerating) {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = Color(0xFFFF5252),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop generation",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (canSend) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send message",
                                    tint = if (canSend) Color.Black else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Inspector Pane for Large Screens.
 */
@Composable
fun AgentInspectorPane(
    activeModelName: String,
    modelStatus: ModelStatus
) {
    GlassCard(
        modifier = Modifier.fillMaxSize(),
        tintColor = Color(0xCC121826),
        borderColor = Color(0x33FFFFFF)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Agent Inspector",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x26FFFFFF))
            Spacer(modifier = Modifier.height(12.dp))

            Text("Active Model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(activeModelName, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00E5FF), fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Engine Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NeonStatusBadge(
                text = modelStatus.name,
                isPulsing = modelStatus == ModelStatus.LOCAL_ACTIVE
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Hardware Backend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Adreno Vulkan NDK (GPU)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E676))
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xE6121826)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text("Select Intelligence Layer", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onImportModel()
                        onDismiss()
                    },
                shape = RoundedCornerShape(12.dp),
                borderColor = DeepEyeTheme.colors.link.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = DeepEyeTheme.colors.link
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Import Custom Local Model (.bin)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Load LiteRT flatbuffer binary from storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(models, key = { it.id }) { model ->
                    val isReady = model.engineState == EngineState.READY ||
                            model.engineState == EngineState.LOADED
                    val isDownloadedButUnsupported = model.engineState == EngineState.DOWNLOADED && !model.isSupportedOnDevice

                    ListItem(
                        modifier = Modifier.clickable {
                            onSelect(model.id)
                            if (isReady) onDismiss()
                        },
                        headlineContent = { Text(model.name, color = MaterialTheme.colorScheme.onSurface) },
                        supportingContent = {
                            Text(text = model.publisher, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            if (isReady) {
                                NeonStatusBadge(text = "Ready on-device", isPulsing = true)
                            } else if (isDownloadedButUnsupported) {
                                NeonStatusBadge(text = "Unsupported", isPulsing = false, color = Color(0xFFFF5252))
                            } else {
                                Text("Tap to download", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
