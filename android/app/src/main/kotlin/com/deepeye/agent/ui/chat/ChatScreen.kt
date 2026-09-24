package com.deepeye.agent.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.components.DexTradingCard
import com.deepeye.agent.ui.components.MarkdownBubble
import com.deepeye.agent.ui.components.ReasoningParser
import com.deepeye.agent.ui.components.ThinkingAccordion
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

    LaunchedEffect(Unit) {
        modelCatalogViewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Speech to text
    val context = LocalContext.current
    var isVoiceActive by remember { mutableStateOf(false) }
    val stt = remember { OnDeviceSpeechToText(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { stt.destroy() } }

    val voiceCallbacks = object : OnDeviceSpeechToText.Callbacks {
        override fun onPartial(text: String) { viewModel.onPromptChange(text) }
        override fun onFinal(text: String) {
            isVoiceActive = false
            viewModel.onPromptChange(text)
        }
        override fun onEndOfSpeech() { isVoiceActive = false }
        override fun onError(errorCode: Int) {
            isVoiceActive = false
            scope.launch { snackbarHostState.showSnackbar("Voice input stopped (${OnDeviceSpeechToText.errorLabel(errorCode)})") }
        }
    }

    fun startVoiceInput() {
        if (!stt.isAvailable) {
            scope.launch { snackbarHostState.showSnackbar("On-device speech recognition unavailable") }
            return
        }
        isVoiceActive = true
        stt.startListening(voiceCallbacks)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput()
    }

    fun onVoicePressed() {
        if (isVoiceActive) {
            stt.stopListening()
            isVoiceActive = false
            return
        }
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceInput()
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto scroll
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
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
                    isVoiceActive = isVoiceActive
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (state.messages.isEmpty()) {
                    // Empty state welcome
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DeepEye Agent",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "On-device intelligence • Zero latency • Private",
                            color = Color(0xFF78909C),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
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
                                onExecuteDexSwap = { id, intent -> viewModel.executeDexSwap(id, intent) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Ultra-Minimalist Top Bar with Dropdown Navigation ─────────────────────────

@Composable
fun MinimalistTopBar(
    activeModelName: String,
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

        // Center: App Title & Model Badge
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DeepEye",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            )
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

        // Right Actions: New Chat & More Options Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            text = "CHAT HISTORY",
            color = Color(0xFF546E7A),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 18.dp)
        )

        Spacer(Modifier.height(8.dp))

        // 2. Middle Section: Recent Chats
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Conversations are saved locally on device.",
                color = Color(0xFF546E7A),
                fontSize = 12.sp
            )
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

// ── Clean & Borderless Message Bubbles ────────────────────────────────────────

@Composable
fun CleanBorderlessBubble(
    message: Message,
    isLastMessage: Boolean,
    onExecuteDexSwap: (String, com.deepeye.agent.core.dex.DexTradeIntent) -> Unit
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(shape)
                    .background(Color(0x2800E5FF))
                    .padding(14.dp)
            ) {
                MarkdownBubble(markdownText = message.text)
            }
        } else {
            val parsed = remember(message.text, message.isStreaming) {
                ReasoningParser.parse(message.text, message.isStreaming)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .clip(shape)
                    .background(if (message.isError) Color(0x20FF5252) else Color(0x12FFFFFF))
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

                    if (parsed.finalResponse.isNotBlank() || !parsed.isThinkingActive) {
                        val displayText = if (parsed.finalResponse.isBlank() && message.isStreaming) "Synthesizing response..." else parsed.finalResponse
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

// ── Minimalist Input Dock ────────────────────────────────────────────────────

@Composable
fun MinimalistChatInputBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onVoice: () -> Unit,
    isVoiceActive: Boolean
) {
    Surface(
        color = Color(0xFF0C0F17),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onVoice,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isVoiceActive) Color(0xFFFF5252) else Color(0xFF90A4AE),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161B26))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                TextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = {
                        Text(
                            text = "Message DeepEye...",
                            color = Color(0xFF546E7A),
                            fontSize = 14.sp
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

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (isGenerating) onCancel() else if (prompt.isNotBlank()) onSend()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGenerating) Color(0xFFFF5252) else if (prompt.isNotBlank()) Color(0xFF00E5FF) else Color(0xFF1C2433))
            ) {
                Icon(
                    imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isGenerating) "Stop" else "Send",
                    tint = if (isGenerating || prompt.isNotBlank()) Color.Black else Color(0xFF546E7A),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
