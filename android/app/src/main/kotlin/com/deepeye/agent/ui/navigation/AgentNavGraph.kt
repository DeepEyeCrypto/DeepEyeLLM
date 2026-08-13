package com.deepeye.agent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlin.reflect.typeOf
import com.deepeye.agent.ui.agent.AgentStudioScreen
import com.deepeye.agent.ui.browser.BraveBrowserScreen
import com.deepeye.agent.ui.chat.ChatScreen
import com.deepeye.agent.ui.p2p.P2PShareScreen
import com.deepeye.agent.ui.rag.KnowledgeBaseScreen
import com.deepeye.agent.ui.security.SecurityDashboardScreen
import com.deepeye.agent.ui.settings.DiagnosticsScreen
import com.deepeye.agent.ui.settings.ModelManagerScreen
import com.deepeye.agent.ui.settings.SettingsScreen
import com.deepeye.agent.ui.skills.SkillStoreScreen

@Composable
fun DeepEyeNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = AgentDestinations.Chat.route) {
            ChatScreen()
        }
        composable(route = AgentDestinations.AgentStudio.route) {
            AgentStudioScreen(viewModel = hiltViewModel())
        }
        composable<AgentDestinations.BraveBrowser> { entry ->
            val args = entry.toRoute<AgentDestinations.BraveBrowser>()
            BraveBrowserScreen(
                initialUrl = args.url,
                initialDexSource = args.dexSource,
                initialSecurityScore = args.securityScore?.toIntOrNull()
            )
        }
        composable<AgentDestinations.SkillStore> { entry ->
            val args = entry.toRoute<AgentDestinations.SkillStore>()
            SkillStoreScreen(
                initialCategory = args.category,
                selectedSkillId = args.selectedSkillId,
                installMode = args.installMode
            )
        }
        composable(route = AgentDestinations.Settings.route) {
            SettingsScreen(
                onNavigateToModelManager = {
                    navController.navigate(AgentDestinations.ModelManager.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(route = AgentDestinations.Diagnostics.route) {
            DiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(route = AgentDestinations.ModelManager.route) {
            ModelManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = AgentDestinations.KnowledgeBase.route) {
            KnowledgeBaseScreen()
        }
        composable(route = AgentDestinations.P2PShare.route) {
            P2PShareScreen()
        }
        composable(route = AgentDestinations.Security.route) {
            SecurityDashboardScreen()
        }
    }
}
