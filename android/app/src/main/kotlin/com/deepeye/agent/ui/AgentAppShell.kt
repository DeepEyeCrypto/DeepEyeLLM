package com.deepeye.agent.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deepeye.agent.ui.navigation.AgentDestinations
import com.deepeye.agent.ui.navigation.DeepEyeNavHost
import com.deepeye.agent.ui.navigation.agentDestinationsList
import com.deepeye.agent.ui.navigation.startDestination
import com.deepeye.agent.ui.navigation.withArgs
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

@Composable
fun AgentAppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val layoutMode = currentUiLayoutMode()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    val navSuiteType = when (layoutMode) {
        UiLayoutMode.COMPACT -> NavigationSuiteType.NavigationBar
        UiLayoutMode.MEDIUM -> NavigationSuiteType.NavigationRail
        UiLayoutMode.EXPANDED -> NavigationSuiteType.NavigationDrawer
    }

    val colors = DeepEyeTheme.colors
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    NavigationSuiteScaffold(
        layoutType = navSuiteType,
        containerColor = Color.Transparent,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = Color(0xCC070A12),
            navigationRailContainerColor = Color(0xCC070A12),
            navigationDrawerContainerColor = Color(0xCC070A12)
        ),
        navigationSuiteItems = {
            agentDestinationsList.forEach { destination ->
                val isSelected = currentRoute == destination.route
                val itemTint = if (isSelected) Color(0xFF00E5FF) else unselectedColor

                item(
                    selected = isSelected,
                    onClick = {
                        when (destination) {
                            is AgentDestinations.BraveBrowser -> {
                                navController.navigate(
                                    AgentDestinations.BraveBrowser().withArgs(
                                        url = "https://example.com",
                                        dexSource = "DexScreener",
                                        securityScore = 98
                                    )
                                ) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            is AgentDestinations.SkillStore -> {
                                navController.navigate(
                                    AgentDestinations.SkillStore().withArgs(
                                        category = "AI Agents",
                                        selectedSkillId = "skill_123",
                                        installMode = "INSTALL"
                                    )
                                ) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            else -> {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.contentDescription,
                            tint = itemTint
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            color = itemTint,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        },
        modifier = modifier
    ) {
        DeepEyeNavHost(
            navController = navController,
            modifier = Modifier
        )
    }
}
