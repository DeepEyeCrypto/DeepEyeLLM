package com.deepeye.agent.core.model

import android.util.Log

/**
 * ChatML Formatter and Sliding-Window Context Manager for 1024 Token Safeguard.
 *
 * Formats conversation histories using ChatML standard tags:
 *   <|im_start|>role
 *   content<|im_end|>
 *
 * And appends `<|im_start|>assistant\n` at the end.
 *
 * Smart Truncation (The 1024 Token Safeguard):
 *   Estimates token count (~4 characters per token).
 *   If history exceeds 768 tokens (leaving 256 tokens for new response generation),
 *   drops oldest user/assistant message turns while ALWAYS keeping the system prompt intact.
 */
object ChatFormatter {

    private const val TAG = "DeepEye-ChatFormatter"
    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"
    private const val CHARS_PER_TOKEN = 4.0

    const val DEFAULT_MAX_CONTEXT = 1024
    const val DEFAULT_MAX_GENERATION = 256
    const val MAX_PROMPT_TOKEN_BUDGET = 768

    /**
     * Formats a conversation history into a full ChatML prompt string,
     * applying sliding window truncation to satisfy context window limits.
     *
     * @param history List of [ChatMessage] objects (system, user, assistant).
     * @param maxContextTokens Max context window budget (default: 1024).
     * @param maxGenerationTokens Max tokens reserved for generation (default: 256).
     * @return Formatted ChatML string ending with `<|im_start|>assistant\n`.
     */
    fun formatPrompt(
        history: List<ChatMessage>,
        maxContextTokens: Int = DEFAULT_MAX_CONTEXT,
        maxGenerationTokens: Int = DEFAULT_MAX_GENERATION
    ): String {
        if (history.isEmpty()) {
            return "$IM_START${ChatMessage.ROLE_SYSTEM}\nYou are DeepEye, an intelligent AI assistant.$IM_END\n$IM_START${ChatMessage.ROLE_ASSISTANT}\n"
        }

        val promptBudget = (maxContextTokens - maxGenerationTokens).coerceAtLeast(256)
        val truncatedHistory = truncateHistory(history, promptBudget)

        val sb = StringBuilder()
        for (msg in truncatedHistory) {
            sb.append(IM_START)
                .append(msg.role.lowercase())
                .append("\n")
                .append(msg.content.trim())
                .append(IM_END)
                .append("\n")
        }

        sb.append(IM_START)
            .append(ChatMessage.ROLE_ASSISTANT)
            .append("\n")

        val result = sb.toString()
        runCatching {
            Log.d(TAG, "ChatML formatted prompt length: ${result.length} chars (~${estimateTokens(result)} tokens, ${truncatedHistory.size}/${history.size} msgs kept)")
        }
        return result
    }

    /**
     * Truncates message history so prompt token count stays under [maxTokenLimit].
     *
     * Safeguard Rules:
     * 1. Always preserves the `system` prompt if it is the first message.
     * 2. Iteratively drops the oldest `user` / `assistant` turn when over budget.
     */
    fun truncateHistory(
        history: List<ChatMessage>,
        maxTokenLimit: Int = MAX_PROMPT_TOKEN_BUDGET
    ): List<ChatMessage> {
        if (history.isEmpty()) return emptyList()

        val mutableHistory = history.toMutableList()
        val systemMessage = if (mutableHistory.firstOrNull()?.role == ChatMessage.ROLE_SYSTEM) {
            mutableHistory.removeAt(0)
        } else {
            null
        }

        while (mutableHistory.isNotEmpty()) {
            val candidate = if (systemMessage != null) listOf(systemMessage) + mutableHistory else mutableHistory
            val tokens = estimateHistoryTokens(candidate)

            if (tokens <= maxTokenLimit) {
                return candidate
            }

            runCatching {
                Log.w(TAG, "Context safeguard hit (~$tokens tokens > $maxTokenLimit limit). Dropping oldest turn: [${mutableHistory.first().role}]")
            }
            mutableHistory.removeAt(0)
        }

        return if (systemMessage != null) listOf(systemMessage) else emptyList()
    }

    /**
     * Estimates token count (~4 chars / token).
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return kotlin.math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    private fun estimateHistoryTokens(messages: List<ChatMessage>): Int {
        var totalChars = 0
        for (msg in messages) {
            totalChars += msg.role.length + msg.content.length + 20
        }
        totalChars += 20
        return kotlin.math.ceil(totalChars / CHARS_PER_TOKEN).toInt()
    }
}
