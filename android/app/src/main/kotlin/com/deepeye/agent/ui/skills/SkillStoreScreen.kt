package com.deepeye.agent.ui.skills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.core.skill.Skill
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.SkillDetailModal
import com.deepeye.agent.ui.theme.*

@Composable
fun SkillStoreScreen(
    initialCategory: String? = null,
    selectedSkillId: String? = null,
    installMode: String? = null,
    modifier: Modifier = Modifier,
    viewModel: SkillStoreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    SkillStoreContent(
        state = state,
        initialCategory = initialCategory,
        selectedSkillId = selectedSkillId,
        installMode = installMode,
        onRefresh = { viewModel.refreshSkills() },
        onInstallToggle = { skill -> viewModel.installSkill(skill) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillStoreContent(
    state: SkillStoreUiState,
    initialCategory: String? = null,
    selectedSkillId: String? = null,
    installMode: String? = null,
    onRefresh: () -> Unit,
    onInstallToggle: (Skill) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory ?: "All") }
    var searchQuery by remember { mutableStateOf("") }
    var inspectingSkill by remember { mutableStateOf<Skill?>(null) }

    LaunchedEffect(selectedSkillId, installMode, state.skills) {
        if (!selectedSkillId.isNullOrEmpty() && !installMode.isNullOrEmpty()) {
            state.skills.find { it.id == selectedSkillId }?.let { skill ->
                if (!skill.isInstalled) {
                    onInstallToggle(skill)
                }
            }
        }
    }

    // Skill detail modal
    inspectingSkill?.let { skill ->
        SkillDetailModal(
            skill = skill,
            onDismiss = { inspectingSkill = null },
            onInstallToggle = { target ->
                onInstallToggle(target)
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xF2070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Agent Skills & Capabilities",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${state.skills.count { it.isInstalled }} active • AgentSkills.io Standard",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan
                            )
                        }

                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Bar
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search skills, tools, or gates...",
                                color = ThinkingMutedSlate,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xCC0E1322),
                            unfocusedContainerColor = Color(0xCC0E1322),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF070A12),
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category Chips Row
            val categories = listOf("All", "Crypto & DeFi", "Security", "AI Agents", "Data & RAG", "Tools & Edge")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    CyberChip(
                        label = cat,
                        selected = cat.equals(selectedCategory, ignoreCase = true),
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (state.isLoading && state.skills.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = CyberCyan)
                } else if (state.error != null && state.skills.isEmpty()) {
                    Text(
                        text = state.error,
                        color = StatusError,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    val filteredSkills = remember(state.skills, selectedCategory, searchQuery) {
                        state.skills.filter { skill ->
                            val matchesCategory = selectedCategory == "All" || skill.category.equals(selectedCategory, ignoreCase = true)
                            val matchesSearch = searchQuery.isBlank() ||
                                skill.name.contains(searchQuery, ignoreCase = true) ||
                                skill.description.contains(searchQuery, ignoreCase = true) ||
                                skill.toolsProvided.any { it.contains(searchQuery, ignoreCase = true) } ||
                                skill.author.contains(searchQuery, ignoreCase = true)
                            matchesCategory && matchesSearch
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredSkills,
                            key = { it.id },
                            contentType = { "tactical_skill_card" }
                        ) { skill ->
                            TacticalSkillCard(
                                skill = skill,
                                onInspect = { inspectingSkill = skill },
                                onInstallToggle = { onInstallToggle(skill) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalSkillCard(
    skill: Skill,
    onInspect: () -> Unit,
    onInstallToggle: () -> Unit
) {
    GlassCard(
        isActive = skill.isInstalled,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
            .border(
                BorderStroke(
                    1.dp,
                    if (skill.isInstalled) StatusSuccess.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = skill.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberCyan
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NeonStatusBadge(
                            text = if (skill.isInstalled) "Active" else "v${skill.version}",
                            color = if (skill.isInstalled) StatusSuccess else ThinkingMutedSlate,
                            isPulsing = skill.isInstalled,
                            modifier = Modifier.height(20.dp)
                        )
                        Switch(
                            checked = skill.isInstalled,
                            onCheckedChange = { onInstallToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = CyberCyan,
                                uncheckedThumbColor = ThinkingMutedSlate,
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = ThinkingMutedSlate,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer with Tools & Gates Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (skill.toolsProvided.isNotEmpty()) {
                    Text(
                        text = "${skill.toolsProvided.size} tools • ${skill.verificationGates.size} gates",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFFB0BEC5)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Inspect",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Inspect Manifest",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
