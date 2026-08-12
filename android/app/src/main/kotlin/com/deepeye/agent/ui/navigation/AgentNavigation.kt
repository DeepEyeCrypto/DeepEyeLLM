package com.deepeye.agent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

import kotlinx.serialization.Serializable

@Serializable
data class BraveBrowserArgs(
    val url: String?,
    val dexSource: String?,
    val securityScore: Int?
)

@Serializable
data class SkillStoreArgs(
    val category: String?,
    val selectedSkillId: String?,
    val installMode: String?
)

data class NavigationItemModel(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
)

@Serializable
sealed class AgentDestinations(
    val route: String,
    val label: String,
    @kotlinx.serialization.Transient
    val icon: ImageVector = Icons.AutoMirrored.Filled.Chat,
    val contentDescription: String
) {
    @Serializable
    data object Chat : AgentDestinations(
        route = "chat",
        label = "Chat",
        icon = Icons.AutoMirrored.Filled.Chat,
        contentDescription = "Chat Intelligence Hub"
    )

    @Serializable
    data object AgentStudio : AgentDestinations(
        route = "agent_studio",
        label = "Studio",
        icon = Icons.Default.AutoAwesome,
        contentDescription = "AI Agent Studio"
    )

    @Serializable
    data class BraveBrowser(
        val url: String? = null,
        val dexSource: String? = null,
        val securityScore: Int = 0
    ) : AgentDestinations(
        route = "brave_browser",
        label = "DEX",
        icon = Icons.Default.Language,
        contentDescription = "Brave DEX Browser"
    )

    @Serializable
    data class SkillStore(
        val category: String? = null,
        val selectedSkillId: String? = null,
        val installMode: String? = null
    ) : AgentDestinations(
        route = "skill_store",
        label = "Skills",
        icon = Icons.Default.Extension,
        contentDescription = "Skill Store & Extensions"
    )

    @Serializable
    data object Settings : AgentDestinations(
        route = "settings",
        label = "Settings",
        icon = Icons.Default.Settings,
        contentDescription = "System Settings"
    )

    @Serializable
    data object Diagnostics : AgentDestinations(
        route = "diagnostics",
        label = "Health",
        icon = Icons.Default.CompassCalibration,
        contentDescription = "System Diagnostics"
    )

    @Serializable
    data object ModelManager : AgentDestinations(
        route = "model_manager",
        label = "Models",
        icon = Icons.Default.Storage,
        contentDescription = "Model Manager & Catalog"
    )
}

val startDestination: String = AgentDestinations.Chat.route

val agentDestinationsList: List<AgentDestinations> = listOf(
    AgentDestinations.Chat,
    AgentDestinations.AgentStudio,
    AgentDestinations.BraveBrowser(),
    AgentDestinations.SkillStore(),
    AgentDestinations.Settings,
    AgentDestinations.Diagnostics,
    AgentDestinations.ModelManager
)

fun List<AgentDestinations>.toNavigationItems(): List<NavigationItemModel> {
    return this.map { destination ->
        NavigationItemModel(
            route = destination.route,
            label = destination.label,
            icon = destination.icon,
            contentDescription = destination.contentDescription
        )
    }
}

fun AgentDestinations.BraveBrowser.withArgs(
    url: String? = null,
    dexSource: String? = null,
    securityScore: Int = 0
): AgentDestinations.BraveBrowser = copy(
    url = url,
    dexSource = dexSource,
    securityScore = securityScore
)

fun AgentDestinations.SkillStore.withArgs(
    category: String? = null,
    selectedSkillId: String? = null,
    installMode: String? = null
): AgentDestinations.SkillStore = copy(
    category = category,
    selectedSkillId = selectedSkillId,
    installMode = installMode
)
