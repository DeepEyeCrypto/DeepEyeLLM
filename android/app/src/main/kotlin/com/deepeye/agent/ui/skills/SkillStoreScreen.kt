package com.deepeye.agent.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyRow
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCardElevated
import com.deepeye.agent.ui.components.NeonStatusBadge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.core.skill.Skill
import com.deepeye.agent.ui.theme.DeepEyeTheme

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    // Show toast when toastMessage is set
    androidx.compose.runtime.LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    SkillStoreScreen(
        state = state,
        initialCategory = initialCategory,
        selectedSkillId = selectedSkillId,
        installMode = installMode,
        onRefresh = { viewModel.refreshSkills() },
        onDownloadSkill = { skill -> viewModel.installSkill(skill) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillStoreScreen(
    state: SkillStoreUiState,
    initialCategory: String? = null,
    selectedSkillId: String? = null,
    installMode: String? = null,
    onRefresh: () -> Unit,
    onDownloadSkill: (Skill) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory ?: "All") }

    androidx.compose.runtime.LaunchedEffect(selectedSkillId, installMode, state.skills) {
        if (!selectedSkillId.isNullOrEmpty() && !installMode.isNullOrEmpty()) {
            state.skills.find { it.id == selectedSkillId }?.let { skill ->
                if (!skill.isInstalled) {
                    onDownloadSkill(skill)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Skill Store & Extensions", fontWeight = FontWeight.Bold)
                        Text("${state.skills.count { it.isInstalled }} active skills", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf("All", "Security", "AI Agents", "Data", "Tools")
                items(categories.size) { index ->
                    val category = categories[index]
                    CyberChip(
                        label = category,
                        selected = category.equals(selectedCategory, ignoreCase = true),
                        onClick = { selectedCategory = category }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (state.isLoading && state.skills.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.secondary)
                } else if (state.error != null && state.skills.isEmpty()) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val filteredSkills = if (selectedCategory == "All") {
                        state.skills
                    } else {
                        state.skills.filter { skill ->
                            skill.name.contains(selectedCategory, ignoreCase = true) ||
                            skill.description.contains(selectedCategory, ignoreCase = true) ||
                            skill.author.contains(selectedCategory, ignoreCase = true)
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredSkills, key = { it.id }) { skill ->
                            SkillCard(
                                skill = skill,
                                isSelected = skill.id == selectedSkillId,
                                onDownload = { onDownloadSkill(skill) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SkillCard(
    skill: Skill,
    isSelected: Boolean = false,
    onDownload: () -> Unit
) {
    GlassCardElevated(
        isActive = isSelected,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = skill.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CyberChip(label = "by ${skill.author}", selected = false, onClick = {})
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "v${skill.version}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = skill.description,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                    fontSize = 14.sp,
                    maxLines = 3
                )
            }
            
            if (skill.isInstalled) {
                NeonStatusBadge(text = "Installed", color = DeepEyeTheme.colors.statusSuccess)
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepEyeTheme.colors.statusSuccess,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
