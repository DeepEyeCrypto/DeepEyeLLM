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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class StructuredSchemaPreset(val title: String, val schemaDescription: String) {
    FREEFORM("Freeform", "Standard markdown & natural language generation"),
    STRICT_JSON("Strict JSON", "Forces valid JSON schema: { \"status\": string, \"data\": object }"),
    CONTRACT_AUDIT("Smart Contract Audit", "Forces { \"score\": int, \"vulnerabilities\": array, \"summary\": string }"),
    TOOL_INTENT("Tool Call Intent", "Forces { \"tool\": string, \"args\": object, \"reason\": string }")
}

/**
 * Prompt Lab & A/B Experimentation Studio.
 * Allows power users to evaluate model hyperparameters, system prompts, and GBNF grammars side-by-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLabScreen(
    modifier: Modifier = Modifier,
    viewModel: PromptLabViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val sharedPrompt = uiState.sharedPrompt
    val selectedSchema = uiState.selectedSchema
    val activeTab = uiState.activeTab
    val variantA = uiState.variantA
    val variantB = uiState.variantB
    val isRunningDual = uiState.isRunningDual

    fun runBenchmark() {
        viewModel.runBenchmark()
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

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.saveCurrentPromptAsPreset() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save Preset",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${sharedPrompt.length} chars",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = ThinkingMutedSlate
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = sharedPrompt,
                        onValueChange = { viewModel.onSharedPromptChange(it) },
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
                        items(uiState.presetPrompts) { (label, text) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x2200E5FF))
                                    .clickable { viewModel.onSharedPromptChange(text) }
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
                        items(StructuredSchemaPreset.entries) { preset ->
                            CyberChip(
                                label = preset.title,
                                selected = selectedSchema == preset,
                                onClick = { viewModel.onSchemaChange(preset) }
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
                    onClick = { viewModel.onTabChange(0) },
                    modifier = Modifier.weight(1f)
                )
                CyberChip(
                    label = variantB.name,
                    selected = activeTab == 1,
                    onClick = { viewModel.onTabChange(1) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Variant Card Container
            if (activeTab == 0) {
                VariantEvaluationCard(
                    variant = variantA,
                    onUpdate = { 
                        viewModel.updateVariantA(
                            systemPrompt = it.systemPrompt,
                            temperature = it.temperature,
                            topK = it.topK,
                            maxTokens = it.maxTokens
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                VariantEvaluationCard(
                    variant = variantB,
                    onUpdate = { 
                        viewModel.updateVariantB(
                            systemPrompt = it.systemPrompt,
                            temperature = it.temperature,
                            topK = it.topK,
                            maxTokens = it.maxTokens
                        )
                    },
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
    variant: PromptLabVariantState,
    onUpdate: (PromptLabVariantState) -> Unit,
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
                            text = "${"%.1f".format(variant.tokensPerSec)} t/s",
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

                // Top-K Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top-K",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${variant.topK}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AmberAccent
                    )
                }
                CyberSlider(
                    value = variant.topK.toFloat(),
                    onValueChange = { onUpdate(variant.copy(topK = it.toInt())) },
                    valueRange = 1f..100f,
                    accentColor = AmberAccent
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                // Max Tokens Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Max Tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${variant.maxTokens}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = StatusWarning
                    )
                }
                CyberSlider(
                    value = variant.maxTokens.toFloat(),
                    onValueChange = { onUpdate(variant.copy(maxTokens = it.toInt())) },
                    valueRange = 64f..4096f,
                    accentColor = StatusWarning
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
