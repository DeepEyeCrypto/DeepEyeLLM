package com.deepeye.agent.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deepeye.agent.R
import com.deepeye.agent.ui.chat.ChatScreen
import com.deepeye.agent.ui.chat.ChatViewModel
import com.deepeye.agent.ui.file.FileAnalysisScreen
import com.deepeye.agent.ui.file.FileAnalysisViewModel
import com.deepeye.agent.ui.history.HistoryScreen
import com.deepeye.agent.ui.history.HistoryViewModel
import com.deepeye.agent.ui.models.ModelCatalogViewModel
import com.deepeye.agent.ui.navigation.AgentNavigation
import com.deepeye.agent.ui.navigation.TopLevelDestination
import com.deepeye.agent.ui.settings.ModelManagerScreen
import com.deepeye.agent.ui.settings.SettingsScreen
import com.deepeye.agent.ui.settings.SettingsViewModel

@Composable
fun AgentAppShell() {
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        val bgPainter = painterResource(id = R.drawable.vision_pro_bg)
        Image(
            painter = bgPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Cache layer compositing strategy to prevent native alloc GC churn
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        )

        AgentNavigation(navController = navController) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Models.route
            ) {
                composable(TopLevelDestination.Models.route) {
                    val chatViewModel = hiltViewModel<ChatViewModel>()
                    ChatScreen(viewModel = chatViewModel)
                }

                composable(TopLevelDestination.Skills.route) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val skillViewModel = hiltViewModel<com.deepeye.agent.ui.skills.SkillStoreViewModel>()
                    val state by skillViewModel.uiState.collectAsState()
                    com.deepeye.agent.ui.skills.SkillStoreScreen(
                        state = state,
                        onRefresh = skillViewModel::refreshSkills,
                        onDownloadSkill = { skill -> 
                            skillViewModel.installSkill(skill, context)
                        }
                    )
                }

                composable(TopLevelDestination.AgentStudio.route) {
                    val agentStudioViewModel = hiltViewModel<com.deepeye.agent.ui.agent.AgentStudioViewModel>()
                    com.deepeye.agent.ui.agent.AgentStudioScreen(viewModel = agentStudioViewModel)
                }

                composable(TopLevelDestination.Browser.route) {
                    val browserViewModel = hiltViewModel<com.deepeye.agent.ui.browser.BraveBrowserViewModel>()
                    com.deepeye.agent.ui.browser.BraveBrowserScreen(viewModel = browserViewModel)
                }

                composable(TopLevelDestination.History.route) {
                    val historyViewModel = hiltViewModel<HistoryViewModel>()
                    val state by historyViewModel.historyState.collectAsState()
                    HistoryScreen(
                        state = state,
                        onQueryChange = historyViewModel::setSearchQuery,
                        onFilterChange = historyViewModel::setFilterType,
                        onItemClick = { item -> historyViewModel.setSearchQuery(item.title) }, // Fallback since selectItem doesn't exist
                        onOpenItem = { item -> navController.navigate(TopLevelDestination.Models.route) },
                        onDeleteItem = { historyViewModel.deleteItem(it.id) },
                        onShareItem = { historyViewModel.shareItem(it.id) }
                    )
                }

                composable(TopLevelDestination.Settings.route) {
                    val settingsViewModel = hiltViewModel<SettingsViewModel>()
                    val state by settingsViewModel.settingsState.collectAsState()
                    SettingsScreen(
                        state = state,
                        onOfflineModeChange = settingsViewModel::toggleOfflineMode,
                        onAutoUpdateSkillsChange = settingsViewModel::toggleAutoUpdate,
                        onPolicyCheckChange = settingsViewModel::togglePolicyChecks,
                        onDiagnosticsChange = settingsViewModel::toggleDiagnostics,
                        onManageModels = {
                            navController.navigate("model_manager")
                        },
                        onExportDiagnostics = { navController.navigate("diagnostics") },
                        onViewPolicyLogs = { navController.navigate("diagnostics") }
                    )
                }

                composable("diagnostics") {
                    com.deepeye.agent.ui.settings.DiagnosticsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("model_manager") {
                    val modelViewModel = hiltViewModel<ModelCatalogViewModel>()
                    val catalog by modelViewModel.modelCatalog.collectAsState()
                    
                    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        uri?.let { modelViewModel.importModel(it) }
                    }

                    ModelManagerScreen(
                        availableModels = catalog,
                        onBack = { navController.popBackStack() },
                        onDownloadModel = modelViewModel::downloadModel,
                        onDeleteModel = modelViewModel::deleteModel,
                        onSelectModel = modelViewModel::selectModel,
                        onImportModel = { filePickerLauncher.launch("*/*") },
                        onRescanModels = modelViewModel::rescanLocalModels,
                        onCancelDownload = modelViewModel::cancelDownload,
                        onPauseDownload = modelViewModel::pauseDownload,
                        onResumeDownload = modelViewModel::resumeDownload,
                        onRefreshCatalog = modelViewModel::refreshCatalog
                    )
                }
            }
        }
    }
}
