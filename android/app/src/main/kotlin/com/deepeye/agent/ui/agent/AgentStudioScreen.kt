package com.deepeye.agent.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import com.deepeye.agent.core.agent.AgentSpec
import com.deepeye.agent.core.agent.ExecutionPhase
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.GlassCardElevated
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.CyberCardHeader
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentStudioScreen(
    viewModel: AgentStudioViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val layoutMode = currentUiLayoutMode()

    // Custom Agent Creator Dialog at top level
    if (state.isCreatorDialogVisible) {
        var nameInput by remember { mutableStateOf("") }
        var roleInput by remember { mutableStateOf("") }
        var promptInput by remember { mutableStateOf("") }
        var emojiInput by remember { mutableStateOf("⚡") }

        AlertDialog(
            onDismissRequest = { viewModel.toggleCreatorDialog(false) },
            title = { Text("Build Custom AI Agent", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Agent Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = roleInput,
                        onValueChange = { roleInput = it },
                        label = { Text("Role Description") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        label = { Text("System Prompt / Behavior Rules") },
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = emojiInput,
                        onValueChange = { emojiInput = it },
                        label = { Text("Icon Emoji") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank() && promptInput.isNotBlank()) {
                            viewModel.createCustomAgent(
                                name = nameInput,
                                role = roleInput,
                                systemPrompt = promptInput,
                                tools = listOf("web_search", "dex_screener", "hermes_memory"),
                                emoji = if (emojiInput.isBlank()) "⚡" else emojiInput
                            )
                        }
                    }
                ) {
                    Text("Deploy Agent")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleCreatorDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ AI Agent Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (layoutMode != UiLayoutMode.COMPACT) 22.sp else 18.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Deep Research Engine",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleCreatorDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Custom Agent", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (layoutMode == UiLayoutMode.COMPACT) {
            AgentStudioCompactLayout(
                state = state,
                viewModel = viewModel,
                focusManager = focusManager,
                modifier = Modifier.padding(padding)
            )
        } else {
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                AgentStudioSidebar(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.weight(0.4f).fillMaxHeight()
                )
                AgentStudioDetailPane(
                    state = state,
                    viewModel = viewModel,
                    focusManager = focusManager,
                    modifier = Modifier.weight(0.6f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun AgentStudioCompactLayout(
    state: AgentStudioUiState,
    viewModel: AgentStudioViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        QuickStatsBar(state.agents.size)
        AgentSelectionSection(state, viewModel, true)
        Spacer(modifier = Modifier.height(16.dp))
        ResearchQuerySection(state, viewModel, focusManager)
        Spacer(modifier = Modifier.height(16.dp))
        ExecutionTraceSection(state)
    }
}

@Composable
private fun AgentStudioSidebar(
    state: AgentStudioUiState,
    viewModel: AgentStudioViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 24.dp, end = 12.dp, bottom = 16.dp)
    ) {
        QuickStatsBar(state.agents.size)
        AgentSelectionSection(state, viewModel, false)
    }
}

@Composable
private fun AgentStudioDetailPane(
    state: AgentStudioUiState,
    viewModel: AgentStudioViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 12.dp, end = 24.dp, bottom = 16.dp)
    ) {
        ResearchQuerySection(state, viewModel, focusManager)
        Spacer(modifier = Modifier.height(16.dp))
        ExecutionTraceSection(state)
    }
}

@Composable
private fun QuickStatsBar(activeAgentsCount: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ACTIVE AGENTS", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                Text("$activeAgentsCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AgentSelectionSection(
    state: AgentStudioUiState,
    viewModel: AgentStudioViewModel,
    isHorizontal: Boolean
) {
    Text(
        text = "SELECT AI AGENT",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    if (isHorizontal) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item { CreateAgentCard(viewModel, 140) }
            items(state.agents, key = { it.name }) { agent ->
                AgentCard(agent, state.selectedAgent?.id == agent.id, viewModel, 160)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item { CreateAgentCard(viewModel, -1) }
            items(state.agents, key = { it.name }) { agent ->
                AgentCard(agent, state.selectedAgent?.id == agent.id, viewModel, -1)
            }
        }
    }
}

@Composable
private fun CreateAgentCard(viewModel: AgentStudioViewModel, widthDp: Int) {
    val mod = if (widthDp > 0) Modifier.width(widthDp.dp) else Modifier.fillMaxWidth()
    Card(
        onClick = { viewModel.toggleCreatorDialog(true) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = mod
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create new agent", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "+ Custom Agent",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun AgentCard(
    agent: AgentSpec,
    isSelected: Boolean,
    viewModel: AgentStudioViewModel,
    widthDp: Int
) {
    val mod = if (widthDp > 0) Modifier.width(widthDp.dp) else Modifier.fillMaxWidth()
    GlassCard(
        modifier = mod.clickable(onClick = { viewModel.selectAgent(agent) }),
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(agent.iconEmoji, fontSize = 22.sp)
                NeonStatusBadge(
                    text = if (agent.isCustom) "CUSTOM" else "ACTIVE",
                    color = if (agent.isCustom) Color(0xFFFF5722) else Color(0xFF00E676)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                agent.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                agent.role,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(color = Color(0x3300E5FF), shape = RoundedCornerShape(4.dp)) {
                    Text("Web Search", fontSize = 8.sp, color = Color(0xFF00E5FF), modifier = Modifier.padding(4.dp))
                }
                Surface(color = Color(0x33FF00FF), shape = RoundedCornerShape(4.dp)) {
                    Text("DEX", fontSize = 8.sp, color = Color(0xFFFF00FF), modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ResearchQuerySection(
    state: AgentStudioUiState,
    viewModel: AgentStudioViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    state.selectedAgent?.let { agent ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Active Agent: ${agent.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = agent.systemPrompt,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.researchQuery,
                    onValueChange = { viewModel.updateResearchQuery(it) },
                    placeholder = { Text("Enter Deep Research goal or task query...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.executeDeepResearch()
                        focusManager.clearFocus()
                    }),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.executeDeepResearch()
                                focusManager.clearFocus()
                            },
                            enabled = !state.isExecuting && state.researchQuery.isNotBlank()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run Research", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExecutionTraceSection(state: AgentStudioUiState) {
    if (state.isExecuting) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(state.executionSteps, key = { index, _ -> "step_$index" }) { index, step ->
            GlassCardElevated(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x2200E5FF),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            step.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            step.detail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NeonStatusBadge(text = step.phase.name, color = Color(0xFF00E5FF))
                }
            }
        }
    }
}
