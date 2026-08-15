package com.deepeye.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*

data class SlashCommandItem(
    val command: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color = CyberCyan
)

val DEFAULT_SLASH_COMMANDS = listOf(
    SlashCommandItem(
        command = "/audit",
        description = "Run smart contract or file security audit",
        icon = Icons.Default.Security,
        accentColor = StatusWarning
    ),
    SlashCommandItem(
        command = "/memory",
        description = "Inspect and query persistent memory ledger",
        icon = Icons.Default.Psychology,
        accentColor = PolicyPurpleDark
    ),
    SlashCommandItem(
        command = "/dex",
        description = "Analyze DEX liquidity & token telemetry",
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        accentColor = StatusSuccess
    ),
    SlashCommandItem(
        command = "/bench",
        description = "Run on-device LLM benchmark suite",
        icon = Icons.Default.Speed,
        accentColor = CyberCyan
    ),
    SlashCommandItem(
        command = "/debug",
        description = "Deep debug local source file with AST tracer",
        icon = Icons.Default.BugReport,
        accentColor = CrimsonFlare
    ),
    SlashCommandItem(
        command = "/clear",
        description = "Clear current conversation & working memory",
        icon = Icons.Default.DeleteSweep,
        accentColor = ThinkingMutedSlate
    )
)

/**
 * Tactical Slash Command Autocomplete Overlay for Chat Input Dock.
 */
@Composable
fun SlashCommandPopup(
    isVisible: Boolean,
    filterText: String,
    onSelect: (SlashCommandItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredCommands = remember(filterText) {
        val query = filterText.removePrefix("/").lowercase().trim()
        if (query.isEmpty()) {
            DEFAULT_SLASH_COMMANDS
        } else {
            DEFAULT_SLASH_COMMANDS.filter {
                it.command.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible && filteredCommands.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF20E1322),
            border = BorderStroke(1.dp, TelemetryBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = "AGENT COMMANDS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ThinkingMutedSlate,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filteredCommands,
                        key = { it.command }
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.command,
                                tint = item.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.command,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = ThinkingMutedSlate,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
