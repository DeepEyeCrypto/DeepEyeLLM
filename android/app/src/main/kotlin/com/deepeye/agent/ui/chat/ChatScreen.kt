package com.deepeye.agent.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.deepeye.agent.analysis.ParsedDocument
import com.deepeye.agent.audio.VoiceState
import com.deepeye.agent.core.model.ToolCallModel
import com.deepeye.agent.core.model.ToolCallStatus
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.components.DexTradingCard
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode
import com.deepeye.agent.ui.components.MarkdownBubble
import com.deepeye.agent.ui.components.ReasoningParser
import com.deepeye.agent.ui.components.ThinkingAccordion
import com.deepeye.agent.ui.components.ToolCallParser
import com.deepeye.agent.ui.components.ToolExecutionCard
import com.deepeye.agent.ui.models.ModelCatalogViewModel
import com.deepeye.agent.ui.navigation.AgentDestinations
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    modelCatalogViewModel: ModelCatalogViewModel = hiltViewModel(),
    onNavigateToDestination: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Responsive reading width: phones stay full-bleed; wide layouts
    // (tablet / foldable / landscape) cap the chat column so bubbles don't
    // stretch edge-to-edge across 1200dp+ screens.
    val layoutMode = currentUiLayoutMode()
    val chatWidthCap: Dp = when (layoutMode) {
        UiLayoutMode.COMPACT -> Int.MAX_VALUE.dp
        UiLayoutMode.MEDIUM, UiLayoutMode.EXPANDED -> 720.dp
    }

    LaunchedEffect(Unit) {
        modelCatalogViewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun startVoiceListeningSafely() {
        if (!viewModel.voiceManager.isRecognitionAvailable) {
            scope.launch { snackbarHostState.showSnackbar("On-device speech recognition unavailable") }
            return
        }
        viewModel.startVoiceListening()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceListeningSafely()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val uriStrings = uris.map { it.toString() }
            viewModel.onImagesSelected(state.selectedImageUris + uriStrings)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onDocumentsSelected(uris)
        }
    }

    fun onVoicePressed() {
        if (state.voiceState == VoiceState.LISTENING) {
            viewModel.stopVoiceListening()
            return
        }
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceListeningSafely()
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-Scroll Mechanics: tied to incoming token stream & new messages
    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, lastMessage?.text?.length) {
        if (state.messages.isNotEmpty()) {
            val lastIndex = state.messages.size - 1
            if (state.isGenerating) {
                listState.scrollToItem(lastIndex)
            } else {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0C0F17),
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
            ) {
                DrawerChatHistoryContent(
                    activeModelName = state.activeModelName,
                    messages = state.messages,
                    onNewChat = {
                        viewModel.newChat()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                MinimalistTopBar(
                    activeModelName = state.activeModelName,
                    tokensPerSecond = state.tokensPerSecond,
                    isGenerating = state.isGenerating,
                    isVoiceModeActive = state.isVoiceModeActive,
                    onToggleVoiceMode = { viewModel.toggleVoiceMode() },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = { viewModel.newChat() },
                    onNavigateToDestination = onNavigateToDestination
                )
            },
            bottomBar = {
                MinimalistChatInputBar(
                    prompt = state.prompt,
                    onPromptChange = viewModel::onPromptChange,
                    isGenerating = state.isGenerating,
                    onSend = { viewModel.sendStream() },
                    onCancel = viewModel::cancelGeneration,
                    onVoice = { onVoicePressed() },
                    isVoiceActive = state.voiceState == VoiceState.LISTENING,
                    selectedImageUris = state.selectedImageUris,
                    onRemoveImage = viewModel::removeImage,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    attachedDocuments = state.attachedDocuments,
                    onRemoveDocument = viewModel::removeDocument,
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf("application/pdf", "text/*", "application/json", "application/octet-stream")
                        )
                    },
                    isVoiceModeActive = state.isVoiceModeActive,
                    onToggleVoiceMode = { viewModel.toggleVoiceMode() }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (state.messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = chatWidthCap)
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DeepEye Omni Agent",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "14B Reasoning • Real-Time Voice • Local RAG • Sovereign",
                            color = Color(0xFF78909C),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = chatWidthCap)
                            .align(Alignment.Center),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id }
                        ) { message ->
                            CleanBorderlessBubble(
                                message = message,
                                isLastMessage = message.id == state.messages.lastOrNull()?.id,
                                onExecuteDexSwap = { id, intent -> viewModel.executeDexSwap(id, intent) },
                                onSpeakMessage = { text -> viewModel.voiceManager.speakSentence(text) }
                            )
                        }
                    }
                }

                // Omni Real-Time Voice Mode Overlay Visualizer
                AnimatedVisibility(
                    visible = state.isVoiceModeActive,
                    enter = fadeIn(animationSpec = tween(280)) + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    VoiceModeOverlay(
                        voiceState = state.voiceState,
                        audioAmplitude = state.audioAmplitude,
                        liveTranscript = if (state.voiceState == VoiceState.LISTENING) state.liveVoiceTranscript else (state.messages.lastOrNull { !it.isUser }?.text ?: state.liveVoiceTranscript),
                        isTtsEnabled = state.isTtsEnabled,
                        onToggleTts = { viewModel.toggleTts() },
                        onClose = { viewModel.toggleVoiceMode(false) },
                        onStartListening = { onVoicePressed() },
                        onStopListening = { viewModel.stopVoiceListening() },
                        onCancelGeneration = { viewModel.cancelGeneration() },
                        isGenerating = state.isGenerating
                    )
                }
            }
        }
    }
}

// ── Ultra-Minimalist Top Bar with Dropdown Navigation & Live TPS HUD ─────────

@Composable
fun MinimalistTopBar(
    activeModelName: String,
    tokensPerSecond: Float = 0f,
    isGenerating: Boolean = false,
    isVoiceModeActive: Boolean = false,
    onToggleVoiceMode: () -> Unit = {},
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToDestination: (String) -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Hamburger Menu
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open Drawer",
                tint = Color(0xFFECEFF1),
                modifier = Modifier.size(24.dp)
            )
        }

        // Center: App Title, Model Badge & Live TPS HUD
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DeepEye",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
                if (tokensPerSecond > 0f) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2200E5FF))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "%.1f tok/s".format(tokensPerSecond),
                            color = Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            if (activeModelName.isNotBlank() && activeModelName != "No Model Loaded") {
                Text(
                    text = activeModelName.substringBeforeLast('.'),
                    color = Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right Actions: Voice Mode Toggle, New Chat & More Options Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleVoiceMode,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isVoiceModeActive) Color(0x3300E5FF) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Voice Mode",
                    tint = if (isVoiceModeActive) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "New Conversation",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Workspace Options",
                        tint = Color(0xFFECEFF1),
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF161B26))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Home Workstation", color = Color(0xFFECEFF1), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = "Home",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToDestination(AgentDestinations.WorkstationHome.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Prompt Lab & Testing", color = Color(0xFFECEFF1), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "Lab",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToDestination(AgentDestinations.PromptLab.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Skills & Self-Improvement", color = Color(0xFFECEFF1), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Skills",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToDestination(AgentDestinations.SkillStore.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("RAG Knowledge Base", color = Color(0xFFECEFF1), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "RAG",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToDestination(AgentDestinations.KnowledgeBase.route)
                        }
                    )
                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)
                    DropdownMenuItem(
                        text = { Text("Settings & Hardware", color = Color(0xFFECEFF1), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFF90A4AE),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToDestination(AgentDestinations.Settings.route)
                        }
                    )
                }
            }
        }
    }
}

// ── Clean Side Drawer Dedicated Exclusively to Chat History & New Chat ────────

@Composable
fun DrawerChatHistoryContent(
    activeModelName: String,
    messages: List<Message> = emptyList(),
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
    ) {
        // 1. Top Action: New Chat Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1800E5FF))
                    .clickable { onNewChat() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Chat",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "New Chat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "SAVED CONVERSATIONS",
            color = Color(0xFF546E7A),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 18.dp)
        )

        Spacer(Modifier.height(8.dp))

        // 2. Middle Section: Recent Chats list or empty prompt
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = if (messages.isEmpty()) Alignment.Center else Alignment.TopStart
        ) {
            if (messages.isEmpty()) {
                Text(
                    text = "Conversations are saved locally on device.",
                    color = Color(0xFF546E7A),
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val userPrompts = messages.filter { it.isUser }
                    items(userPrompts, key = { it.id }) { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x12FFFFFF))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg.text,
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom Hardware Status
        HorizontalDivider(
            color = Color(0x18FFFFFF),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (activeModelName.isNotBlank() && activeModelName != "No Model Loaded") activeModelName else "Engine Ready (Vulkan)",
                color = Color(0xFF90A4AE),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Glassmorphism Message Bubbles ──────────────────────────────────────────

@Composable
fun CleanBorderlessBubble(
    message: Message,
    isLastMessage: Boolean,
    onExecuteDexSwap: (String, com.deepeye.agent.core.dex.DexTradeIntent) -> Unit,
    onSpeakMessage: (String) -> Unit = {}
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            // Document Attachment Badges
            if (message.attachedDocuments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    modifier = Modifier.wrapContentWidth(Alignment.End)
                ) {
                    items(message.attachedDocuments, key = { it.id }) { doc ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2200E5FF))
                                .border(BorderStroke(0.5.dp, Color(0x5500E5FF)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (doc.isPdf) Color(0xFFFF5252) else Color(0xFF00E5FF),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = doc.fileName,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                    }
                }
            }

            if (message.imageUris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    modifier = Modifier.wrapContentWidth(Alignment.End)
                ) {
                    items(message.imageUris) { uri ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Attached Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    BorderStroke(0.5.dp, Color(0x6600E5FF)),
                                    RoundedCornerShape(12.dp)
                                )
                        )
                    }
                }
            }

            if (message.text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(shape)
                        .background(Color(0x2800E5FF))
                        .border(
                            BorderStroke(0.5.dp, Color(0x5500E5FF)),
                            shape = shape
                        )
                        .padding(14.dp)
                ) {
                    MarkdownBubble(markdownText = message.text)
                }
            }
        } else {
            val parsed = remember(message.text, message.isStreaming) {
                ReasoningParser.parse(message.text, message.isStreaming)
            }

            val toolParsed = remember(parsed.finalResponse, message.isStreaming) {
                ToolCallParser.parse(parsed.finalResponse, message.isStreaming)
            }

            val combinedToolCalls = remember(message.toolCalls, toolParsed.toolCalls) {
                val existing = message.toolCalls.toMutableList()
                for (incoming in toolParsed.toolCalls) {
                    // Dedupe on identity OR same tool+arguments, so streaming re-parses of the
                    // same call update one card, while distinct calls in the ReAct loop stack.
                    val idx = existing.indexOfFirst {
                        it.id == incoming.id ||
                            (it.name.equals(incoming.name, ignoreCase = true) && it.arguments == incoming.arguments)
                    }
                    if (idx >= 0) {
                        existing[idx] = incoming
                    } else {
                        existing.add(incoming)
                    }
                }
                existing
            }

            val bubbleBg = when {
                message.isError -> Color(0x22FF5252)
                else -> Color(0x2AFFFFFF)
            }
            val bubbleBorder = when {
                message.isError -> Color(0x55FF5252)
                else -> Color(0x33FFFFFF)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .clip(shape)
                    .background(bubbleBg)
                    .border(
                        BorderStroke(0.5.dp, bubbleBorder),
                        shape = shape
                    )
                    .padding(14.dp)
            ) {
                Column {
                    if (!parsed.thoughtTrace.isNullOrBlank()) {
                        ThinkingAccordion(
                            thoughtTrace = parsed.thoughtTrace,
                            isThinkingActive = parsed.isThinkingActive,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (combinedToolCalls.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            combinedToolCalls.forEach { toolCall ->
                                ToolExecutionCard(
                                    toolCall = toolCall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    val cleanResponse = toolParsed.cleanText
                    if (cleanResponse.isNotBlank() || (!parsed.isThinkingActive && combinedToolCalls.isEmpty())) {
                        val displayText = if (cleanResponse.isBlank() && message.isStreaming) "Synthesizing response..." else cleanResponse
                        Row(verticalAlignment = Alignment.Bottom) {
                            MarkdownBubble(
                                markdownText = displayText,
                                modifier = Modifier.weight(1f, fill = false)
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
                            onExecuteSwap = { executed -> onExecuteDexSwap(message.id, executed) }
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

    Box(
        modifier = Modifier
            .padding(start = 4.dp, bottom = 3.dp)
            .size(width = 8.dp, height = 15.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF00E5FF).copy(alpha = alpha))
    )
}

// ── Glassmorphic Input Dock ──────────────────────────────────────────────────

@Composable
fun AttachmentPreviewRow(
    imageUris: List<String>,
    onRemoveUri: (String) -> Unit
) {
    if (imageUris.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(imageUris) { uri ->
            Box(modifier = Modifier.size(68.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(0.5.dp, Color(0x6600E5FF)),
                            RoundedCornerShape(12.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .clickable { onRemoveUri(uri) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalistChatInputBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onVoice: () -> Unit,
    isVoiceActive: Boolean,
    selectedImageUris: List<String>,
    onRemoveImage: (String) -> Unit,
    onPickImage: () -> Unit,
    attachedDocuments: List<ParsedDocument> = emptyList(),
    onRemoveDocument: (String) -> Unit = {},
    onPickDocument: () -> Unit = {},
    isVoiceModeActive: Boolean = false,
    onToggleVoiceMode: () -> Unit = {}
) {
    Surface(
        color = Color(0xD00C0F17),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = Color(0x22FFFFFF),
                thickness = 0.5.dp
            )
            
            DocumentPreviewRow(
                documents = attachedDocuments,
                onRemoveDocument = onRemoveDocument
            )

            AttachmentPreviewRow(
                imageUris = selectedImageUris,
                onRemoveUri = onRemoveImage
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach Document Button (PDF / Code / Text)
                IconButton(
                    onClick = onPickDocument,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (attachedDocuments.isNotEmpty()) Color(0x3300E5FF) else Color(0x12FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach Document",
                        tint = if (attachedDocuments.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Photo Picker Button
                IconButton(
                    onClick = onPickImage,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (selectedImageUris.isNotEmpty()) Color(0x3300E5FF) else Color(0x12FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach Photo",
                        tint = if (selectedImageUris.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Voice Mode Action Button
                IconButton(
                    onClick = onToggleVoiceMode,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isVoiceModeActive) Color(0x3300E5FF) else Color(0x12FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Omni Voice Mode",
                        tint = if (isVoiceModeActive) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Glassmorphic Input Capsule
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x22FFFFFF))
                        .border(
                            BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    TextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                text = if (attachedDocuments.isNotEmpty()) "Ask about ${attachedDocuments.first().fileName}..." else "Message DeepEye...",
                                color = Color(0xFF78909C),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color(0xFFECEFF1),
                            unfocusedTextColor = Color(0xFFECEFF1)
                        ),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Push-to-Talk Mic Button
                IconButton(
                    onClick = onVoice,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isVoiceActive) Color(0x33FF5252) else Color(0x12FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isVoiceActive) Color(0xFFFF5252) else Color(0xFF90A4AE),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                val canSend = prompt.isNotBlank() || selectedImageUris.isNotEmpty() || attachedDocuments.isNotEmpty()

                // Send / Stop Action Button with Cyber Glow
                IconButton(
                    onClick = {
                        if (isGenerating) onCancel() else if (canSend) onSend()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGenerating) Color(0xFFFF5252)
                            else if (canSend) Color(0xFF00E5FF)
                            else Color(0x1FFFFFFF)
                        )
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "Stop" else "Send",
                        tint = if (isGenerating || canSend) Color.Black else Color(0xFF546E7A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ── Local RAG Document Preview Row ───────────────────────────────────────────

@Composable
fun DocumentPreviewRow(
    documents: List<ParsedDocument>,
    onRemoveDocument: (String) -> Unit
) {
    if (documents.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(documents, key = { it.id }) { doc ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2800E5FF))
                    .border(BorderStroke(0.5.dp, Color(0x6600E5FF)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = if (doc.isPdf) Color(0xFFFF5252) else Color(0xFF00E5FF),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = doc.fileName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp)
                    )
                    Text(
                        text = if (doc.isPdf) "${doc.pageCount} pages • ${doc.chunkCount} chunks" else "${doc.chunkCount} chunks",
                        color = Color(0xFF90A4AE),
                        fontSize = 9.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { onRemoveDocument(doc.id) },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color(0xFFB0BEC5),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// ── Glowing Animated Waveform Visualizer ──────────────────────────────────────

@Composable
fun GlowingWaveformVisualizer(
    amplitude: Float,
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        val effectiveAmp = (amplitude * (height * 0.45f)).coerceAtLeast(10f)

        val path1 = Path()
        val path2 = Path()
        val path3 = Path()

        val points = 80
        val dx = width / points

        for (i in 0..points) {
            val x = i * dx
            val normX = (i.toFloat() / points) * 2 * Math.PI.toFloat()
            val envelope = Math.sin((i.toFloat() / points) * Math.PI).toFloat()

            val y1 = midY + Math.sin(normX.toDouble() * 2.2 + phase).toFloat() * effectiveAmp * envelope
            val y2 = midY + Math.sin(normX.toDouble() * 3.1 - phase * 1.2).toFloat() * (effectiveAmp * 0.75f) * envelope
            val y3 = midY + Math.cos(normX.toDouble() * 1.6 + phase * 0.8).toFloat() * (effectiveAmp * 0.5f) * envelope

            if (i == 0) {
                path1.moveTo(x, y1)
                path2.moveTo(x, y2)
                path3.moveTo(x, y3)
            } else {
                path1.lineTo(x, y1)
                path2.lineTo(x, y2)
                path3.lineTo(x, y3)
            }
        }

        // Deep Purple/Violet Glow Wave
        drawPath(
            path = path3,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0x007C4DFF), Color(0x997C4DFF), Color(0x007C4DFF))
            ),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Secondary Harmonic Wave
        drawPath(
            path = path2,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0x0000E5FF), Color(0xCC7C4DFF), Color(0x0000E5FF))
            ),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Primary Core Wave
        drawPath(
            path = path1,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0x2200E5FF), Color(0xFF00E5FF), Color(0x2200E5FF))
            ),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Central Orb Halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = (0.22f + amplitude * 0.35f).coerceIn(0f, 0.65f)),
                    Color.Transparent
                ),
                center = Offset(width / 2f, midY),
                radius = (45.dp.toPx() + effectiveAmp * 0.8f)
            ),
            center = Offset(width / 2f, midY),
            radius = (45.dp.toPx() + effectiveAmp * 0.8f)
        )
    }
}

// ── Omni Voice Mode Fullscreen Overlay ────────────────────────────────────────

@Composable
fun VoiceModeOverlay(
    voiceState: VoiceState,
    audioAmplitude: Float,
    liveTranscript: String,
    isTtsEnabled: Boolean,
    onToggleTts: () -> Unit,
    onClose: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onCancelGeneration: () -> Unit,
    isGenerating: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF4080B12))
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (voiceState) {
                                    VoiceState.LISTENING -> Color(0xFF00E5FF)
                                    VoiceState.THINKING -> Color(0xFF7C4DFF)
                                    VoiceState.SPEAKING -> Color(0xFF00E676)
                                    VoiceState.IDLE -> Color(0xFF90A4AE)
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "DeepEye Omni Voice",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Default.PlayArrow else Icons.Default.Stop,
                            contentDescription = "Toggle Audio Output",
                            tint = if (isTtsEnabled) Color(0xFF00E5FF) else Color(0xFF78909C),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Voice Mode",
                            tint = Color(0xFFECEFF1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.4f))

            // State Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when (voiceState) {
                            VoiceState.LISTENING -> Color(0x3300E5FF)
                            VoiceState.THINKING -> Color(0x337C4DFF)
                            VoiceState.SPEAKING -> Color(0x3300E676)
                            VoiceState.IDLE -> Color(0x22FFFFFF)
                        }
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            when (voiceState) {
                                VoiceState.LISTENING -> Color(0x8800E5FF)
                                VoiceState.THINKING -> Color(0x887C4DFF)
                                VoiceState.SPEAKING -> Color(0x8800E676)
                                VoiceState.IDLE -> Color(0x44FFFFFF)
                            }
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when (voiceState) {
                        VoiceState.LISTENING -> "Listening... speak now"
                        VoiceState.THINKING -> "Synthesizing thought..."
                        VoiceState.SPEAKING -> "DeepEye is speaking..."
                        VoiceState.IDLE -> "Tap mic to speak"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))

            // Waveform Visualizer
            GlowingWaveformVisualizer(
                amplitude = audioAmplitude,
                voiceState = voiceState
            )

            Spacer(Modifier.height(24.dp))

            // Live Transcript Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x18FFFFFF))
                    .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (liveTranscript.isNotBlank()) {
                    Text(
                        text = liveTranscript,
                        color = Color(0xFFECEFF1),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Speak naturally. DeepEye processes voice locally with zero cloud telemetry.",
                        color = Color(0xFF546E7A),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bottom Voice Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGenerating) {
                    IconButton(
                        onClick = onCancelGeneration,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FF5252))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                }

                // Main Pulsing Mic Button
                val isMicActive = voiceState == VoiceState.LISTENING
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(if (isMicActive) Color(0xFFFF5252) else Color(0xFF00E5FF))
                        .clickable {
                            if (isMicActive) onStopListening() else onStartListening()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Push to Talk",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

