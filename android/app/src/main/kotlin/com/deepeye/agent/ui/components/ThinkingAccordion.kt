package com.deepeye.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*

/**
 * Data structure representing parsed message content with reasoning traces separated.
 */
data class ParsedReasoning(
    val thoughtTrace: String?,
    val isThinkingActive: Boolean,
    val finalResponse: String
)

object ReasoningParser {
    private val THINK_TAG_START = listOf("<think>", "<thought>", "<reasoning>")
    private val THINK_TAG_END = listOf("</think>", "</thought>", "</reasoning>")

    /**
     * Parses raw streamed text into distinct thought stream and final user-facing response.
     */
    fun parse(rawText: String, isStreaming: Boolean): ParsedReasoning {
        if (rawText.isBlank()) {
            return ParsedReasoning(
                thoughtTrace = if (isStreaming) "Initializing neural reasoning..." else null,
                isThinkingActive = isStreaming,
                finalResponse = ""
            )
        }

        var text = rawText
        var matchingStartTag: String? = null
        for (tag in THINK_TAG_START) {
            if (text.contains(tag, ignoreCase = true)) {
                matchingStartTag = tag
                break
            }
        }

        if (matchingStartTag == null) {
            return ParsedReasoning(
                thoughtTrace = null,
                isThinkingActive = false,
                finalResponse = rawText
            )
        }

        val startIndex = text.indexOf(matchingStartTag, ignoreCase = true)
        val afterStart = text.substring(startIndex + matchingStartTag.length)

        var matchingEndTag: String? = null
        for (tag in THINK_TAG_END) {
            if (afterStart.contains(tag, ignoreCase = true)) {
                matchingEndTag = tag
                break
            }
        }

        return if (matchingEndTag != null) {
            val endIndex = afterStart.indexOf(matchingEndTag, ignoreCase = true)
            val thought = afterStart.substring(0, endIndex).trim()
            val remaining = afterStart.substring(endIndex + matchingEndTag.length).trimStart()
            ParsedReasoning(
                thoughtTrace = thought,
                isThinkingActive = false,
                finalResponse = remaining
            )
        } else {
            // Still streaming inside reasoning block
            ParsedReasoning(
                thoughtTrace = afterStart.trim(),
                isThinkingActive = isStreaming,
                finalResponse = ""
            )
        }
    }
}

/**
 * Collapsible Thinking Accordion Component for Edge AI Workstation.
 * Formats multi-step agent reasoning with high legibility and zero layout shift.
 */
@Composable
fun ThinkingAccordion(
    thoughtTrace: String,
    isThinkingActive: Boolean,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = isThinkingActive
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    // Auto-expand while thinking is active
    LaunchedEffect(isThinkingActive) {
        if (isThinkingActive) {
            isExpanded = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "thinking_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0x33121826),
        border = BorderStroke(
            1.dp,
            if (isThinkingActive) CyberCyan.copy(alpha = pulseAlpha) else ThinkingBorderCyan
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isThinkingActive) CyberCyan.copy(alpha = 0.2f) else Color(0x3394A3B8)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Reasoning Process",
                            tint = if (isThinkingActive) CyberCyan else ThinkingMutedSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isThinkingActive) "Thinking & Planning..." else "Reasoning Trace",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isThinkingActive) CyberCyan else Color(0xFFE2E8F0)
                        )
                        val wordCount = thoughtTrace.split("\\s+".toRegex()).size
                        Text(
                            text = if (isThinkingActive) "Generating logic gates..." else "$wordCount tokens of thought",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = ThinkingMutedSlate
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isThinkingActive) {
                        NeonStatusBadge(
                            text = "Live",
                            color = CyberCyan,
                            isPulsing = true,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = ThinkingMutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x40070A12))
                        .padding(10.dp)
                ) {
                    Text(
                        text = thoughtTrace,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = ThinkingMutedSlate,
                        modifier = Modifier.semantics {
                            contentDescription = "Model reasoning process details"
                        }
                    )
                }
            }
        }
    }
}
