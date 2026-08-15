package com.deepeye.agent.ui.promptlab

import androidx.compose.animation.AnimatedVisibility
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
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.*
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

enum class StructuredSchemaPreset(val title: String, val schemaDescription: String) {
    FREEFORM("Freeform", "Standard markdown & natural language generation"),
    STRICT_JSON("Strict JSON", "Forces valid JSON schema: { \"status\": string, \"data\": object }"),
    CONTRACT_AUDIT("Smart Contract Audit", "Forces { \"score\": int, \"vulnerabilities\": array, \"summary\": string }"),
    TOOL_INTENT("Tool Call Intent", "Forces { \"tool\": string, \"args\": object, \"reason\": string }")
}

data class PromptLabVariant(
    val name: String,
    val systemPrompt: String,
    val temperature: Float,
    val topP: Float,
    val outputText: String = "",
    val isStreaming: Boolean = false,
    val ttftMs: Long = 0,
    val tokensPerSec: Float = 0f
)

/**
 * Prompt Lab & A/B Experimentation Studio.
 * Allows power users to evaluate model hyperparameters, system prompts, and GBNF grammars side-by-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLabScreen(
    modifier: Modifier = Modifier
) {
    val layoutMode = currentUiLayoutMode()
    val isWideScreen = layoutMode != UiLayoutMode.COMPACT

    var sharedPrompt by remember {
        mutableStateOf("Analyze the reentrancy attack surface on standard ERC-20 transferFrom implementations.")
    }
    var selectedSchema by remember { mutableStateOf(StructuredSchemaPreset.FREEFORM) }
    var activeTab by remember { mutableIntStateOf(0) } // For compact screen

    var variantA by remember {
        mutableStateOf(
            PromptLabVariant(
                name = "Variant A (Deterministic)",
                systemPrompt = "You are a senior security researcher. Output strictly verified technical invariants.",
                temperature = 0.2f,
                topP = 0.85f,
                outputText = "Decompiling contract bytecode...\nInvariant 1: State mutation occurs after external balance checks.\nInvariant 2: Reentrancy protection modifier enforced on withdrawal pathways.",
                ttftMs = 120,
                tokensPerSec = 34.5f
            )
        )
    }

    var variantB by remember {
        mutableStateOf(
            PromptLabVariant(
                name = "Variant B (Creative / Broad)",
                systemPrompt = "You are an exploratory AI assistant. Provide comprehensive exploit scenarios and edge-case theories.",
                temperature = 0.8f,
                topP = 0.95f,
                outputText = "Exploring potential attack vectors:\n1. Cross-function reentrancy via fallback hooks.\n2. Read-only reentrancy impacting downstream price oracles in AMM pool pairs.",
                ttftMs = 140,
                tokensPerSec = 32.1f
            )
        )
    }

    var isRunningDual by remember { mutableStateOf(false) }

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
                    Column {
                        Text(
                            text = "Prompt Lab & A/B Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Dual-Pane Hyperparameter Sweeps & GBNF Grammars",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }

                    CyberButton(
                        onClick = { isRunningDual = !isRunningDual },
                        accentColor = CyberCyan
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunningDual) "Stop" else "Run A/B", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Shared User Prompt Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                tintColor = Color(0xCC0E1322)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "TEST PROMPT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = sharedPrompt,
                        onValueChange = { sharedPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color(0xFF070A12),
                            unfocusedContainerColor = Color(0xFF070A12),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // GBNF Schema Selector
                    Text(
                        text = "STRUCTURED OUTPUT (GBNF GRAMMAR)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = ThinkingMutedSlate
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StructuredSchemaPreset.values()) { preset ->
                            CyberChip(
                                label = preset.title,
                                selected = selectedSchema == preset,
                                onClick = { selectedSchema = preset }
                            )
                        }
                    }
                }
            }

            // A/B Comparison Panes
            if (isWideScreen) {
                // Wide Screen: Dual Column Side-by-Side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VariantEvaluationCard(
                        variant = variantA,
                        onUpdate = { variantA = it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    VariantEvaluationCard(
                        variant = variantB,
                        onUpdate = { variantB = it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                // Compact Screen: Tab Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CyberChip(
                        label = variantA.name,
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    CyberChip(
                        label = variantB.name,
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (activeTab == 0) {
                        VariantEvaluationCard(
                            variant = variantA,
                            onUpdate = { variantA = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VariantEvaluationCard(
                            variant = variantB,
                            onUpdate = { variantB = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VariantEvaluationCard(
    variant: PromptLabVariant,
    onUpdate: (PromptLabVariant) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                // Header & Metrics Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = variant.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${variant.tokensPerSec} t/s",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = CyberCyan
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${variant.ttftMs}ms TTFT",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = StatusSuccess
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hyperparameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temperature: ${"%.2f".format(variant.temperature)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = ThinkingMutedSlate
                        )
                        Slider(
                            value = variant.temperature,
                            onValueChange = { onUpdate(variant.copy(temperature = it)) },
                            valueRange = 0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberCyan,
                                activeTrackColor = CyberCyan,
                                inactiveTrackColor = Color(0x3300E5FF)
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Top-P: ${"%.2f".format(variant.topP)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = ThinkingMutedSlate
                        )
                        Slider(
                            value = variant.topP,
                            onValueChange = { onUpdate(variant.copy(topP = it)) },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberAccent,
                                activeTrackColor = AmberAccent,
                                inactiveTrackColor = Color(0x33FFB300)
                            )
                        )
                    }
                }

                // System Prompt
                Text(
                    text = "SYSTEM INSTRUCTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ThinkingMutedSlate
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = variant.systemPrompt,
                    onValueChange = { onUpdate(variant.copy(systemPrompt = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color(0xFF070A12),
                        unfocusedContainerColor = Color(0xFF070A12),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Output Display Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070A12))
                    .padding(10.dp)
            ) {
                Text(
                    text = "GENERATED STREAM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ThinkingMutedSlate
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = variant.outputText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color(0xFFCFD8DC)
                )
            }
        }
    }
}
