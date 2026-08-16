package com.deepeye.agent.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.core.hardware.HardwareFitEvaluator
import com.deepeye.agent.core.hardware.MemoryFitLevel
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.TelemetrySpeedometer
import com.deepeye.agent.ui.navigation.AgentDestinations
import com.deepeye.agent.ui.theme.*
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

data class BentoWorkstationTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accentColor: Color,
    val badgeText: String? = null
)

val WORKSTATION_TILES = listOf(
    BentoWorkstationTile(
        title = "AI Chat & Reasoning",
        subtitle = "Streaming multi-turn chat with live thought deconstruction",
        icon = Icons.AutoMirrored.Filled.Chat,
        route = AgentDestinations.Chat.route,
        accentColor = CyberCyan,
        badgeText = "Live Engine"
    ),
    BentoWorkstationTile(
        title = "Prompt Lab A/B",
        subtitle = "Side-by-side prompt testing & GBNF structured schemas",
        icon = Icons.Default.Science,
        route = AgentDestinations.PromptLab.route,
        accentColor = PolicyPurpleDark,
        badgeText = "A/B Studio"
    ),
    BentoWorkstationTile(
        title = "Benchmark Suite",
        subtitle = "On-device LLM latency, throughput & memory testing",
        icon = Icons.Default.Speed,
        route = AgentDestinations.Benchmark.route,
        accentColor = StatusSuccess,
        badgeText = "Hardware HUD"
    ),
    BentoWorkstationTile(
        title = "Crypto Sentinel DEX",
        subtitle = "On-chain smart contract security & non-custodial intents",
        icon = Icons.Default.Language,
        route = "brave_browser",
        accentColor = BrandOrange,
        badgeText = "Web3 Shield"
    ),
    BentoWorkstationTile(
        title = "Agent Skills Store",
        subtitle = "AgentSkills.io manifests, verification gates & tools",
        icon = Icons.Default.Extension,
        route = "skill_store",
        accentColor = TealCyanSecondary,
        badgeText = "8 Verified"
    ),
    BentoWorkstationTile(
        title = "Knowledge & Context",
        subtitle = "100% On-device RAG vector index & episodic memory",
        icon = Icons.Default.Description,
        route = AgentDestinations.KnowledgeBase.route,
        accentColor = LinkBlue,
        badgeText = "Vector RAG"
    ),
    BentoWorkstationTile(
        title = "Security & Policy Guard",
        subtitle = "Zero-trust RBAC governance and audit logs",
        icon = Icons.Default.Security,
        route = AgentDestinations.Security.route,
        accentColor = AmberAccent,
        badgeText = "Air-Gapped"
    ),
    BentoWorkstationTile(
        title = "Model Manager",
        subtitle = "Dual-engine runtime & quantization fit manager",
        icon = Icons.Default.Storage,
        route = AgentDestinations.ModelManager.route,
        accentColor = CyberCyan,
        badgeText = "LiteRT + GGUF"
    )
)

/**
 * Obsidian Bento Workstation Home Dashboard.
 * Serves as the central command hub for power users, security researchers, and autonomous agent tasks.
 */
@Composable
fun WorkstationHomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutMode = currentUiLayoutMode()

    val thermalAdvice = remember { HardwareFitEvaluator.getThermalAdvice(context) }
    val fitReport = remember {
        HardwareFitEvaluator.evaluateModelFit(context, modelSizeBytes = 1200L * 1024L * 1024L)
    }

    Scaffold(
        containerColor = Color(0xFF070A12),
        topBar = {
            Surface(
                color = Color(0xF2070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DeepEyeLLM",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "WORKSTATION",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = CyberCyan,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = "Edge-Native AI Hub • 100% Offline Trust",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThinkingMutedSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    NeonStatusBadge(
                        text = "Hermes v2.2.0",
                        color = StatusSuccess,
                        isPulsing = true
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // Hardware Status & Memory Fit Card
            item {
                GlassCard(
                    onClick = { onNavigate(AgentDestinations.Benchmark.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    tintColor = Color(0xCC0E1322)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(StatusSuccess.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text(
                                        text = "Hardware Governor",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${fitReport.availableDeviceRamMb}MB available of ${fitReport.totalDeviceRamMb}MB total",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = ThinkingMutedSlate
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StatusSuccess.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Unthrottled Turbo",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = StatusSuccess
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "View Telemetry HUD",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x66070A12))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thermal: ${thermalAdvice.thermalStatus}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = if (thermalAdvice.isThrottled) StatusError else Color(0xFFCFD8DC)
                            )
                            Text(
                                text = "8T Turbo • RT Mode",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = CyberCyan
                            )
                        }
                    }
                }
            }

            // Section Title
            item {
                Text(
                    text = "WORKSTATION MODULES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CyberCyan
                )
            }

            // Bento Grid of Modules
            val gridColumns = when (layoutMode) {
                UiLayoutMode.COMPACT -> 1
                UiLayoutMode.MEDIUM -> 2
                UiLayoutMode.EXPANDED -> 3
            }

            items(WORKSTATION_TILES.chunked(gridColumns)) { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowTiles.forEach { tile ->
                        BentoTileCard(
                            tile = tile,
                            onClick = { onNavigate(tile.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space if odd count in row
                    if (rowTiles.size < gridColumns) {
                        Spacer(modifier = Modifier.weight((gridColumns - rowTiles.size).toFloat()))
                    }
                }
            }
        }
    }
}

@Composable
fun BentoTileCard(
    tile: BentoWorkstationTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tile.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tile.icon,
                        contentDescription = null,
                        tint = tile.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (tile.badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tile.badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = ThinkingMutedSlate,
                    maxLines = 2
                )
            }
        }
    }
}
