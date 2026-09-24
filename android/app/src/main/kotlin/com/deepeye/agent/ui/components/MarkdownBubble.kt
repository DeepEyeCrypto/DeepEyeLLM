package com.deepeye.agent.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

sealed class ParsedSegment {
    data class FormattedText(val annotated: AnnotatedString) : ParsedSegment()
    data class CodeBlock(val language: String, val rawCode: String, val highlightedCode: AnnotatedString) : ParsedSegment()
}

// ── Static Pre-Compiled Regex Patterns (Zero per-frame regex compilation) ─────
private object MarkdownPatterns {
    val CODE_BLOCK_PATTERN: Pattern = Pattern.compile(
        "```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)```",
        Pattern.MULTILINE
    )
    val INLINE_CODE_PATTERN: Pattern = Pattern.compile("`([^`]+)`")
    val BOLD_PATTERN: Pattern = Pattern.compile("\\*\\*([^\\*]+)\\*\\*")
    val ITALIC_PATTERN: Pattern = Pattern.compile("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)")
    val HEADER_PATTERN: Pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE)
    val BULLET_PATTERN: Pattern = Pattern.compile("^[\\s]*[-*]\\s+(.+)$", Pattern.MULTILINE)

    // Syntax highlighting patterns
    val KW_PATTERN: Pattern = Pattern.compile(
        "\\b(fun|val|var|class|interface|object|import|package|return|if|else|when|for|while|is|in|try|catch|finally|throw|suspend|override|public|private|protected|internal|const|companion|data|enum|sealed|typealias|struct|fn|let|mut|impl|pub|async|await|def|from|import|export|const|function|class|return|self|this|true|false|null|nil)\\b"
    )
    val NUM_PATTERN: Pattern = Pattern.compile("\\b(\\d+(\\.\\d+)?[fFLdD]?|0x[0-9a-fA-F]+)\\b")
    val STR_PATTERN: Pattern = Pattern.compile("(\"[^\"]*\"|'[^']*')")
    val COMMENT_PATTERN: Pattern = Pattern.compile("(//.*$|#.*$)", Pattern.MULTILINE)
    val TYPE_PATTERN: Pattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\b")
}

/**
 * High-Performance Markdown Bubble with Asynchronous Background Parsing.
 * Offloads regex parsing, AST segmentation, and syntax highlighting to Dispatchers.Default,
 * ensuring the Jetpack Compose Main UI Thread executes ZERO regex work.
 */
@Composable
fun MarkdownBubble(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    // Asynchronously parse markdown text on Dispatchers.Default
    val segments by produceState<List<ParsedSegment>>(
        initialValue = listOf(ParsedSegment.FormattedText(AnnotatedString(markdownText))),
        key1 = markdownText
    ) {
        value = withContext(Dispatchers.Default) {
            parseMarkdownToSegments(markdownText)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ParsedSegment.FormattedText -> {
                    SelectionContainer {
                        Text(
                            text = segment.annotated,
                            color = Color(0xFFECEFF1),
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
                is ParsedSegment.CodeBlock -> {
                    OptimizedCodeBlockCard(
                        language = segment.language,
                        rawCode = segment.rawCode,
                        highlightedCode = segment.highlightedCode
                    )
                }
            }
        }
    }
}

/**
 * Parse raw markdown string into parsed and pre-highlighted segments on background thread.
 */
private fun parseMarkdownToSegments(text: String): List<ParsedSegment> {
    if (text.isBlank()) {
        return listOf(ParsedSegment.FormattedText(AnnotatedString("")))
    }

    val results = mutableListOf<ParsedSegment>()
    val matcher = MarkdownPatterns.CODE_BLOCK_PATTERN.matcher(text)
    var lastIndex = 0

    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()

        // Text before code block
        if (start > lastIndex) {
            val textChunk = text.substring(lastIndex, start)
            if (textChunk.isNotBlank()) {
                results.add(ParsedSegment.FormattedText(buildFormattedText(textChunk)))
            }
        }

        val lang = matcher.group(1)?.trim() ?: ""
        val code = matcher.group(2)?.trimEnd() ?: ""
        val highlighted = highlightSyntax(code)

        results.add(ParsedSegment.CodeBlock(language = lang, rawCode = code, highlightedCode = highlighted))
        lastIndex = end
    }

    // Trailing text
    if (lastIndex < text.length) {
        val trailing = text.substring(lastIndex)
        if (trailing.isNotBlank()) {
            results.add(ParsedSegment.FormattedText(buildFormattedText(trailing)))
        }
    }

    return if (results.isEmpty()) listOf(ParsedSegment.FormattedText(buildFormattedText(text))) else results
}

/**
 * Builds formatted AnnotatedString with headers, bold, italics, bullets, and inline code.
 */
private fun buildFormattedText(raw: String): AnnotatedString {
    return buildAnnotatedString {
        append(raw)

        // 1. Headers
        val headerMatcher = MarkdownPatterns.HEADER_PATTERN.matcher(raw)
        while (headerMatcher.find()) {
            val hLevel = headerMatcher.group(1)?.length ?: 1
            val content = headerMatcher.group(2) ?: ""
            val start = headerMatcher.start()
            val end = headerMatcher.end()

            val fontSize = when (hLevel) {
                1 -> 18.sp
                2 -> 16.5.sp
                3 -> 15.sp
                else -> 14.5.sp
            }

            addStyle(
                SpanStyle(
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                ),
                start, end
            )
        }

        // 2. Bold text
        val boldMatcher = MarkdownPatterns.BOLD_PATTERN.matcher(raw)
        while (boldMatcher.find()) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                boldMatcher.start(),
                boldMatcher.end()
            )
        }

        // 3. Italic text
        val italicMatcher = MarkdownPatterns.ITALIC_PATTERN.matcher(raw)
        while (italicMatcher.find()) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFCFD8DC)
                ),
                italicMatcher.start(),
                italicMatcher.end()
            )
        }

        // 4. Inline Code
        val inlineMatcher = MarkdownPatterns.INLINE_CODE_PATTERN.matcher(raw)
        while (inlineMatcher.find()) {
            addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF),
                    background = Color(0x3300E5FF),
                    fontSize = 13.sp
                ),
                inlineMatcher.start(),
                inlineMatcher.end()
            )
        }

        // 5. Bullet points
        val bulletMatcher = MarkdownPatterns.BULLET_PATTERN.matcher(raw)
        while (bulletMatcher.find()) {
            addStyle(
                SpanStyle(
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.SemiBold
                ),
                bulletMatcher.start(),
                bulletMatcher.start() + 2
            )
        }
    }
}

/**
 * Syntax Highlighting executed on Background Thread.
 */
private fun highlightSyntax(code: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        // Comments
        val commentMatcher = MarkdownPatterns.COMMENT_PATTERN.matcher(code)
        while (commentMatcher.find()) {
            addStyle(
                SpanStyle(color = Color(0xFF8B949E), fontWeight = FontWeight.Normal),
                commentMatcher.start(), commentMatcher.end()
            )
        }

        // Strings
        val strMatcher = MarkdownPatterns.STR_PATTERN.matcher(code)
        while (strMatcher.find()) {
            addStyle(
                SpanStyle(color = Color(0xFFA5D6FF)),
                strMatcher.start(), strMatcher.end()
            )
        }

        // Numbers
        val numMatcher = MarkdownPatterns.NUM_PATTERN.matcher(code)
        while (numMatcher.find()) {
            addStyle(
                SpanStyle(color = Color(0xFFFF7B72)),
                numMatcher.start(), numMatcher.end()
            )
        }

        // Types
        val typeMatcher = MarkdownPatterns.TYPE_PATTERN.matcher(code)
        while (typeMatcher.find()) {
            addStyle(
                SpanStyle(color = Color(0xFF7EE787), fontWeight = FontWeight.Medium),
                typeMatcher.start(), typeMatcher.end()
            )
        }

        // Keywords
        val kwMatcher = MarkdownPatterns.KW_PATTERN.matcher(code)
        while (kwMatcher.find()) {
            addStyle(
                SpanStyle(color = Color(0xFFFF7B72), fontWeight = FontWeight.Bold),
                kwMatcher.start(), kwMatcher.end()
            )
        }
    }
}

@Composable
fun OptimizedCodeBlockCard(
    language: String,
    rawCode: String,
    highlightedCode: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1117))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("code", rawCode)
                    clipboard.setPrimaryClip(clip)
                    isCopied = true
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Crossfade(targetState = isCopied, label = "copy_icon_crossfade") { copied ->
                    if (copied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Copied",
                            tint = Color(0xFF7EE787),
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Code Content (Pre-rendered, zero UI thread regex)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = highlightedCode,
                    color = Color(0xFFC9D1D9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
