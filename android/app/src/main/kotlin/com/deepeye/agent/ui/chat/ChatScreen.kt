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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.domain.EngineState
import com.deepeye.agent.domain.LocalModel
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.ReasoningParser
import com.deepeye.agent.ui.components.ThinkingAccordion
import com.deepeye.agent.ui.components.ToolExecutionCard
import com.deepeye.agent.ui.components.ToolExecutionStatus
import com.deepeye.agent.ui.components.TelemetryHeaderHUD
import com.deepeye.agent.ui.components.SlashCommandPopup
import com.deepeye.agent.ui.components.DexTradingCard
import com.deepeye.agent.core.dex.DexTradeIntent
import androidx.compose.foundation.BorderStroke
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.models.ModelCatalogViewModel
import com.deepeye.agent.ui.theme.*
import com.deepeye.agent.ui.utils.PerformanceUtils
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode
import androidx.compose.ui.geometry.Offset

// OPTIMIZATION: Stable keys for message types
private object MessageContentTypes {
    const val USER = 0
    const val ASSISTANT = 1
    const val ERROR = 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    modelCatalogViewModel: ModelCatalogViewModel = hiltViewModel()
) {
    // OPTIMIZATION: Collect only what's needed
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelCatalog by modelCatalogViewModel.modelCatalog.collectAsStateWithLifecycle()
    val layoutMode = currentUiLayoutMode()
    var showModelPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        modelCatalogViewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
        containerColor = Color(0xFF070A12), // Obsidian Void
        contentWindowInsets = WindowInsets.ime,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TelemetryHeaderHUD(
                modelStatus = state.modelStatus,
                activeModelName = state.activeModelName,
                tokensPerSecond = if (state.isGenerating || state.tokensPerSecond > 0f) state.tokensPerSecond else 0f,
                thermalState = "Nominal 36°C",
                onPickerClick = { showModelPicker = true }
            )
        },
        bottomBar = {
            // OPTIMIZATION: Memoize input dock props
            val canSend = !state.isLoading && !state.isGenerating && state.prompt.isNotBlank()
            
            FuturisticChatInputDock(
                prompt = state.prompt,
                onPromptChange = viewModel::onPromptChange,
                onSend = { viewModel.sendStream() },
                onCancel = viewModel::cancelGeneration,
                onAnalyze = { filePicker.launch(arrayOf("*/*")) },
                onDebug = { debugPicker.launch(arrayOf("*/*")) },
                isLoading = state.isLoading,
                isGenerating = state.isGenerating,
                canSend = canSend
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
                // Actionable error recovery card
                val err = state.error
                if (state.modelStatus == ModelStatus.ERROR && err != null) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        tintColor = DeepEyeTheme.colors.statusError.copy(alpha = 0.12f),
                        borderColor = DeepEyeTheme.colors.statusError.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = err,
                            color = DeepEyeTheme.colors.statusError,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 14.dp, start = 14.dp, end = 14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(modifier = Modifier.padding(bottom = 14.dp, start = 14.dp, end = 14.dp)) {
                            CyberButton(
                                onClick = {
                                    val workingModel = modelCatalog.firstOrNull { 
                                        it.engineState == EngineState.READY && 
                                        it.isSupportedOnDevice &&
                                        !it.fileName.contains("dspark", ignoreCase = true) && 
                                        !it.fileName.contains("adapter", ignoreCase = true)
                                    } ?: modelCatalog.firstOrNull { it.engineState == EngineState.READY }
                                    
                                    if (workingModel != null) {
                                        viewModel.selectAndActivateModel(workingModel.id, workingModel.fileName)
                                    } else {
                                        showModelPicker = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = CyberCyan
                            ) {
                                Text("Switch to Working Model (Hermes 3 / Gemma 4)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.messages.isEmpty() && !state.isGenerating) {
                        EmptyStateOverlay()
                    } else {
                        HolographicChatTranscript(
                            messages = state.messages,
                            isGenerating = state.isGenerating,
                            onExecuteDexSwap = viewModel::executeDexSwap
                        )
                    }
                }
            }

            // Adaptive Supporting Pane
            if (layoutMode != UiLayoutMode.COMPACT) {
                GlassCard(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
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
                    AgentInspectorPane(
                        activeModelName = state.activeModelName,
                        modelStatus = state.modelStatus
                    )
                }
            }
        }

        if (showModelPicker || state.showModelPicker) {
            ModelPickerSheet(
                currentStatus = state.modelStatus,
                activeModelName = state.activeModelName,
                models = modelCatalog,
                onSelect = { model ->
                    viewModel.selectAndActivateModel(model.id, model.fileName)
                    showModelPicker = false
                    viewModel.toggleModelPicker(false)
                },
                onImportModel = {
                    showModelPicker = false
                    viewModel.toggleModelPicker(false)
                    modelPicker.launch(arrayOf("*/*"))
                },
                onDismiss = {
                    showModelPicker = false
                    viewModel.toggleModelPicker(false)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolographicTopAppBar(
    modelStatus: ModelStatus,
    activeModelName: String,
    onPickerClick: () -> Unit
) {
    // OPTIMIZATION: Memoize derived values
    val isLiteRT = remember(activeModelName) { 
        activeModelName.startsWith("LiteRT", ignoreCase = true) 
    }
    val statusText = remember(isLiteRT) { 
        if (isLiteRT) "LiteRT Active" else "DeepEye Local" 
    }

    Surface(
        color = Color(0xD9070A12),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
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
                        color = DeepEyeTheme.colors.link,
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
                onClick = onPickerClick,
                color = if (modelStatus == ModelStatus.LOCAL_ACTIVE) DeepEyeTheme.colors.statusSuccess else DeepEyeTheme.colors.link
            )
        }
    }
}

@Composable
fun HolographicChatTranscript(
    messages: List<Message>,
    isGenerating: Boolean,
    onExecuteDexSwap: (String, com.deepeye.agent.core.dex.DexTradeIntent) -> Unit = { _, _ -> }
) {
    val listState = rememberLazyListState()
    
    // OPTIMIZATION: Only scroll when new messages arrive or streaming updates
    val lastMessageId = messages.lastOrNull()?.id
    val lastMessageText = messages.lastOrNull()?.text ?: ""
    val lastMessageStreaming = messages.lastOrNull()?.isStreaming ?: false

    LaunchedEffect(lastMessageId, lastMessageText.length, lastMessageStreaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // OPTIMIZATION: Use stable keys and content types
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, 
            end = 16.dp, 
            top = 16.dp, 
            bottom = 120.dp // Floating dock breathing room
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            items = messages,
            key = { it.id }, // Stable key
            contentType = { message ->
                when {
                    message.isUser -> MessageContentTypes.USER
                    message.isError -> MessageContentTypes.ERROR
                    else -> MessageContentTypes.ASSISTANT
                }
            }
        ) { message ->
            // OPTIMIZATION: Pass only necessary props
            HolographicMessageBubble(
                message = message,
                isLastMessage = message.id == lastMessageId,
                onExecuteDexSwap = onExecuteDexSwap
            )
        }
    }
}

@Composable
fun HolographicMessageBubble(
    message: Message,
    isLastMessage: Boolean,
    onExecuteDexSwap: (String, com.deepeye.agent.core.dex.DexTradeIntent) -> Unit = { _, _ -> }
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            GlassCard(
                showSpecular = false,
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Cap width for readability
                    .animateContentSize()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                DeepEyeTheme.colors.link.copy(alpha = 0.6f),
                                DeepEyeTheme.colors.link.copy(alpha = 0.2f)
                            )
                        ),
                        shape = shape
                    ),
                shape = shape,
                tintColor = Color(0xB3121826),
                borderColor = DeepEyeTheme.colors.link.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 24.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        } else {
            val parsed = remember(message.text, message.isStreaming) {
                ReasoningParser.parse(message.text, message.isStreaming)
            }
            val cardTint = if (message.isError) Color(0xB32A1215) else Color(0xB3161E2E)
            val cardBorder = when {
                message.isError -> DeepEyeTheme.colors.statusError
                message.isStreaming -> Color(0xFF00F2FE) // Neon Cyan
                else -> DeepEyeTheme.colors.link
            }

            GlassCard(
                showSpecular = false,
                modifier = Modifier
                    .fillMaxWidth(0.92f) // Slightly wider for AI responses
                    .then(if (!message.isStreaming) Modifier.animateContentSize() else Modifier)
                    .border(
                        width = if (message.isStreaming) 1.5.dp else 1.dp,
                        color = cardBorder.copy(alpha = if (message.isStreaming) 0.6f else 0.3f),
                        shape = shape
                    ),
                shape = shape,
                tintColor = Color(0xFF151A29).copy(alpha = 0.7f), // Deep slate tint
                borderColor = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        NeonStatusBadge(
                            text = when {
                                message.isError -> "Error"
                                message.dexTradeIntent != null -> "🦁 Hermes 3 DEX"
                                message.isStreaming && parsed.isThinkingActive -> "Thinking..."
                                message.isStreaming -> "Streaming..."
                                else -> "DeepEye AI"
                            },
                            isPulsing = message.isStreaming && isLastMessage,
                            color = when {
                                message.isError -> DeepEyeTheme.colors.statusError
                                message.dexTradeIntent != null -> CyberCyan
                                message.isStreaming -> DeepEyeTheme.colors.link
                                else -> DeepEyeTheme.colors.statusSuccess
                            },
                            modifier = Modifier.height(26.dp)
                        )
                    }

                    if (!parsed.thoughtTrace.isNullOrBlank()) {
                        ThinkingAccordion(
                            thoughtTrace = parsed.thoughtTrace,
                            isThinkingActive = parsed.isThinkingActive,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (parsed.finalResponse.isNotBlank() || !parsed.isThinkingActive) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (parsed.finalResponse.isBlank() && message.isStreaming) "Synthesizing response..." else parsed.finalResponse,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (message.isError) Color(0xFFFFB3B3) else Color.White,
                                modifier = Modifier.weight(1f, fill = false),
                                lineHeight = 24.sp,
                                letterSpacing = 0.2.sp
                            )
                            if (message.isStreaming && isLastMessage && !parsed.isThinkingActive) {
                                HolographicStreamingCursor()
                            }
                        }
                    }

                    if (message.dexTradeIntent != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        DexTradingCard(
                            intent = message.dexTradeIntent,
                            onExecuteSwap = { executed ->
                                onExecuteDexSwap(message.id, executed)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HolographicStreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor_transition")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    
    // PERF: Merged into single transition to halve animation loop overhead
    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(20.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepEyeTheme.colors.link.copy(alpha = alpha * glowAlpha),
                        DeepEyeTheme.colors.link.copy(alpha = alpha * 0.3f)
                    )
                ),
                shape = RoundedCornerShape(4.dp)
            )
    )
}

@Composable
fun FuturisticChatInputDock(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAnalyze: () -> Unit,
    onDebug: () -> Unit,
    isLoading: Boolean,
    isGenerating: Boolean,
    canSend: Boolean
) {
    var isToolsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp) // Lifted above bottom nav
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        SlashCommandPopup(
            isVisible = prompt.startsWith("/"),
            filterText = prompt,
            onSelect = { item ->
                when (item.command) {
                    "/clear" -> {
                        onPromptChange("")
                    }
                    "/audit" -> {
                        onPromptChange("/audit ")
                        onAnalyze()
                    }
                    "/debug" -> {
                        onPromptChange("/debug ")
                        onDebug()
                    }
                    else -> {
                        onPromptChange("${item.command} ")
                    }
                }
            }
        )

        if (prompt.isEmpty() && !isGenerating) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                val suggestions = listOf(
                    "🛡️ /audit" to "Audit contract",
                    "🦁 /dex" to "DEX Sentinel",
                    "🧠 /memory" to "Context Ledger",
                    "⚡ /bench" to "Benchmark",
                    "🐞 /debug" to "Deep Debug"
                )
                items(suggestions.size) { index ->
                    val (tag, label) = suggestions[index]
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC0E1322),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.clickable {
                            val cmd = tag.split(" ").last()
                            onPromptChange("$cmd ")
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = tag, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                            Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = ThinkingMutedSlate)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = Color(0x2600B4D8)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(32.dp)
                    ),
                shape = RoundedCornerShape(32.dp),
                tintColor = Color(0xFF151A29).copy(alpha = 0.75f),
                borderColor = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    TextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Ask DeepEye anything...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        maxLines = 5,
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
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isToolsExpanded = !isToolsExpanded },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isToolsExpanded) Icons.Default.Info else Icons.Default.Add,
                                    contentDescription = "Toggle tools",
                                    tint = DeepEyeTheme.colors.link,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (isToolsExpanded) {
                                FuturisticToolChip(
                                    icon = Icons.Default.Mic,
                                    label = "Voice",
                                    onClick = { onAnalyze() },
                                    enabled = !isLoading && !isGenerating,
                                    accentColor = Color(0xFF00F2FE)
                                )

                                FuturisticToolChip(
                                    icon = Icons.Default.CameraAlt,
                                    label = "Vision",
                                    onClick = { onAnalyze() },
                                    enabled = !isLoading && !isGenerating,
                                    accentColor = Color(0xFFFF007A)
                                )

                                FuturisticToolChip(
                                    icon = Icons.Default.AttachFile,
                                    label = "Analyze",
                                    onClick = onAnalyze,
                                    enabled = !isLoading && !isGenerating,
                                    accentColor = DeepEyeTheme.colors.link
                                )

                                FuturisticToolChip(
                                    icon = Icons.Default.BugReport,
                                    label = "Deep Debug",
                                    onClick = onDebug,
                                    enabled = !isLoading && !isGenerating,
                                    accentColor = DeepEyeTheme.colors.statusSuccess
                                )
                            }
                        }

                        if (isGenerating) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = DeepEyeTheme.colors.statusError.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    DeepEyeTheme.colors.statusError.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .clickable(onClick = onCancel)
                                    .size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = DeepEyeTheme.colors.statusError,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = DeepEyeTheme.colors.link,
                                    disabledContainerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = if (canSend) Color(0xFF070A12) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuturisticToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            accentColor.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                color = accentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AgentInspectorPane(
    activeModelName: String,
    modelStatus: ModelStatus
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Agent Inspector",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        
        GlassCard(
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
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activeModelName, style = MaterialTheme.typography.bodySmall, color = DeepEyeTheme.colors.link)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when (modelStatus) {
                            ModelStatus.LOCAL_ACTIVE -> "Active"
                            ModelStatus.NOT_DOWNLOADED -> "Not Downloaded"
                            ModelStatus.ERROR -> "Error"
                            else -> "Unknown"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (modelStatus) {
                            ModelStatus.LOCAL_ACTIVE -> DeepEyeTheme.colors.statusSuccess
                            ModelStatus.NOT_DOWNLOADED -> DeepEyeTheme.colors.statusWarning
                            ModelStatus.ERROR -> DeepEyeTheme.colors.statusError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "Performance",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            DeepEyeTheme.colors.link.copy(alpha = 0.3f),
                            DeepEyeTheme.colors.link.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TTFT", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("~245 ms", style = MaterialTheme.typography.bodySmall, color = DeepEyeTheme.colors.link)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tokens/sec", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("~18.4", style = MaterialTheme.typography.bodySmall, color = DeepEyeTheme.colors.accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    currentStatus: ModelStatus,
    activeModelName: String,
    models: List<LocalModel>,
    onSelect: (LocalModel) -> Unit,
    onImportModel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF2070A12),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select LLM Model",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x3300E5FF)
                ) {
                    Text(
                        text = "${models.count { it.engineState == EngineState.READY }} Local Ready",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = CyberCyan, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    val cleanActive = activeModelName.removePrefix("GGUF: ").removePrefix("LiteRT: ").trim()
                    val isActive = cleanActive.equals(model.fileName.substringBeforeLast('.'), ignoreCase = true) ||
                        cleanActive.equals(model.name, ignoreCase = true) ||
                        cleanActive.equals(model.id, ignoreCase = true) ||
                        cleanActive.equals(model.fileName, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActive) Color(0x3300E5FF) else Color(0xCC0E1322))
                            .border(
                                1.dp,
                                if (isActive) CyberCyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(model) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${model.sizeString} • ${model.publisher} • ${model.engineState.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ThinkingMutedSlate
                                )
                            }
                            if (isActive) {
                                NeonStatusBadge(
                                    text = "Active",
                                    isPulsing = false,
                                    color = StatusSuccess
                                )
                            } else if (model.engineState == EngineState.READY) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyberCyan.copy(alpha = 0.25f))
                                        .border(1.dp, CyberCyan, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Load",
                                        style = MaterialTheme.typography.labelMedium.copy(color = CyberCyan, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            CyberButton(
                onClick = onImportModel,
                modifier = Modifier.fillMaxWidth(),
                accentColor = AmberAccent
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Custom Local Model (.gguf / .bin)", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun EmptyStateOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val meshBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00F2FE).copy(alpha = 0.06f),
                        Color(0xFF0B0F19).copy(alpha = 0.0f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.35f),
                    radius = size.minDimension * 0.8f
                )
                onDrawBehind {
                    drawRect(meshBrush)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DeepEye",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White.copy(alpha = 0.08f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ask anything. Runs 100% on-device.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}
