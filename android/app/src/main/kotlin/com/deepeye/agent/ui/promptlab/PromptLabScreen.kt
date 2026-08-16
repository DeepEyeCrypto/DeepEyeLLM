package com.deepeye.agent.ui.promptlab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()

    var sharedPrompt by remember {
        mutableStateOf("Analyze the reentrancy attack surface on standard ERC-20 transferFrom implementations.")
    }
    var selectedSchema by remember { mutableStateOf(StructuredSchemaPreset.FREEFORM) }
    var activeTab by remember { mutableIntStateOf(0) }

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

    val presetPrompts = listOf(
        "ERC-20 Audit" to "Analyze the reentrancy attack surface on standard ERC-20 transferFrom implementations.",
        "Zero-Day Scan" to "Scan memory allocation patterns in C++ pointer dereferencing for buffer overflows.",
        "JSON Extractor" to "Extract DEX pool liquidity reserves, token decimals, and slippage tolerance from swap logs.",
        "Agent Plan" to "Synthesize multi-step autonomous execution plan for smart contract fuzz testing."
    )

    fun runBenchmark() {
        if (isRunningDual) {
            isRunningDual = false
            return
        }
        isRunningDual = true
        coroutineScope.launch {
            variantA = variantA.copy(outputText = "", isStreaming = true, ttftMs = 95, tokensPerSec = 36.2f)
            variantB = variantB.copy(outputText = "", isStreaming = true, ttftMs = 110, tokensPerSec = 33.8f)

            val streamChunksA = listOf(
                "Initializing deterministic engine (temp=0.2)...\n",
                "[Pass 1] AST validation verified clean control flow.\n",
                "Invariant Check: transferFrom state transitions execute atomically.\n",
                "Formal Verification: Solved Z3 invariant with 0 SAT counter-examples.\n",
                "Audit Conclusion: No reentrancy vulnerability detected on standard transfer pathway."
            )

            val streamChunksB = listOf(
                "Initializing exploratory synthesis (temp=0.8)...\n",
                "Hypothesis: Analyzing potential flash loan reentrancy vectors...\n",
                "Scenario A: Callback reentrancy if ERC-777 tokensReceived hook is invoked.\n",
                "Scenario B: Cross-contract read reentrancy during UniswapV2 price calculation.\n",
                "Recommendation: Implement Checks-Effects-Interactions and ReentrancyGuard modifier."
            )

            for (i in 0 until maxOf(streamChunksA.size, streamChunksB.size)) {
                if (!isRunningDual) break
                delay(300)
                if (i < streamChunksA.size) {
                    variantA = variantA.copy(outputText = variantA.outputText + streamChunksA[i])
                }
                if (i < streamChunksB.size) {
                    variantB = variantB.copy(outputText = variantB.outputText + streamChunksB[i])
                }
            }
            variantA = variantA.copy(isStreaming = false)
            variantB = variantB.copy(isStreaming = false)
            isRunningDual = false
        }
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
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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

                    Button(
                        onClick = { runBenchmark() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunningDual) StatusError else CyberCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunningDual) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunningDual) "Stop" else "Run A/B", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TEST PROMPT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = CyberCyan
                        )

                        Text(
                            text = "${sharedPrompt.length} chars",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = ThinkingMutedSlate
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = sharedPrompt,
                        onValueChange = { sharedPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
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

                    // Preset Prompts Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presetPrompts) { (label, text) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x2200E5FF))
                                    .clickable { sharedPrompt = text }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = CyberCyan
                                )
                            }
                        }
                    }

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

            // Variant Card Container
            if (activeTab == 0) {
                VariantEvaluationCard(
                    variant = variantA,
                    onUpdate = { variantA = it },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                VariantEvaluationCard(
                    variant = variantB,
                    onUpdate = { variantB = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CyberSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val rangeLength = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeLength > 0f) ((value - valueRange.start) / rangeLength).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * rangeLength
                        onValueChange(newValue)
                    }
                }
                .pointerInput(valueRange) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * rangeLength
                        onValueChange(newValue)
                    }
                }
        ) {
            val trackHeight = 4.dp.toPx()
            val centerY = size.height / 2
            val thumbRadius = 7.dp.toPx()
            val thumbX = (fraction * size.width).coerceIn(thumbRadius, size.width - thumbRadius)

            // Inactive Track
            drawLine(
                color = accentColor.copy(alpha = 0.2f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // Active Track
            drawLine(
                color = accentColor,
                start = Offset(0f, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // Thumb Glow
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = thumbRadius + 4.dp.toPx(),
                center = Offset(thumbX, centerY)
            )

            // Thumb Core
            drawCircle(
                color = accentColor,
                radius = thumbRadius,
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.Black,
                radius = thumbRadius * 0.45f,
                center = Offset(thumbX, centerY)
            )
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
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Metrics Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = variant.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${variant.tokensPerSec} t/s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberCyan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusSuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${variant.ttftMs}ms TTFT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = StatusSuccess
                        )
                    }
                }
            }

            // Hyperparameters Sliders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070A12))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Temperature Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Temperature",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "%.2f".format(variant.temperature),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CyberCyan
                    )
                }
                CyberSlider(
                    value = variant.temperature,
                    onValueChange = { onUpdate(variant.copy(temperature = it)) },
                    valueRange = 0.0f..1.5f,
                    accentColor = CyberCyan
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Top-P Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top-P Sampling",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "%.2f".format(variant.topP),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AmberAccent
                    )
                }
                CyberSlider(
                    value = variant.topP,
                    onValueChange = { onUpdate(variant.copy(topP = it)) },
                    valueRange = 0.1f..1.0f,
                    accentColor = AmberAccent
                )
            }

            // System Instruction
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SYSTEM INSTRUCTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ThinkingMutedSlate
                )
                OutlinedTextField(
                    value = variant.systemPrompt,
                    onValueChange = { onUpdate(variant.copy(systemPrompt = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(10.dp),
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

            // Output Display Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070A12))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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

                    if (variant.isStreaming) {
                        NeonStatusBadge(text = "Streaming...", color = CyberCyan, isPulsing = true)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (variant.outputText.isBlank()) "Tap 'Run A/B' in top-bar to execute evaluation..." else variant.outputText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = if (variant.outputText.isBlank()) ThinkingMutedSlate else Color(0xFFCFD8DC)
                )
            }
        }
    }
}
