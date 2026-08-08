package com.deepeye.agent.core.model

import android.util.Log

/**
 * Utility for formatting chat conversation history using the ChatML template standard
 * (compatible with Qwen3, Llama-3, and standard instruction-tuned GGUF models)
 * and managing sliding context window limits.
 *
 * ChatML Format Spec:
 *   <|im_start|>system
 *   You are DeepEye, an intelligent AI coding and security assistant.<|im_end|>
 *   <|im_start|>user
 *   Hello!<|im_end|>
 *   <|im_start|>assistant
 *   Hi! How can I help you today?<|im_end|>
 *   <|im_start|>assistant
 */
object ChatMLFormatter {

    private const val TAG = "DeepEye-ChatML"
    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"
    
    // Heuristic: ~3.8 characters per token for English/code mixed prompts
    private const val CHARS_PER_TOKEN = 3.8

    /**
     * Formats a conversation history into a full ChatML prompt string,
     * applying sliding window truncation to avoid context window overflow.
     *
     * @param history Full list of [ChatMessage] items (system, user, assistant).
     * @param maxContextTokens Context window capacity of the loaded GGUF model (default: 2048).
     * @param maxGenerationTokens Reserved output generation budget (default: 512).
     * @return Formatted ChatML string ready for native inference.
     */
    fun formatPromptWithHistory(
        history: List<ChatMessage>,
        maxContextTokens: Int = 2048,
        maxGenerationTokens: Int = 512
    ): String {
        if (history.isEmpty()) {
            return "$IM_START${ChatMessage.ROLE_SYSTEM}\nYou are DeepEye, a helpful AI assistant.$IM_END\n$IM_START${ChatMessage.ROLE_ASSISTANT}\n"
        }

        // Budget available for the input prompt (excluding expected response tokens)
        val promptTokenBudget = (maxContextTokens - maxGenerationTokens).coerceAtLeast(256)
        
        // Truncate history to fit within the prompt token budget
        val truncatedHistory = truncateHistoryToContextWindow(history, promptTokenBudget)

        val sb = StringBuilder()
        for (msg in truncatedHistory) {
            sb.append(IM_START)
                .append(msg.role.lowercase())
                .append("\n")
                .append(msg.content.trim())
                .append(IM_END)
                .append("\n")
        }

        // Append assistant prefix to trigger generation
        sb.append(IM_START)
            .append(ChatMessage.ROLE_ASSISTANT)
            .append("\n")

        val result = sb.toString()
        runCatching { Log.d(TAG, "Formatted ChatML prompt: ${result.length} chars (~${estimateTokenCount(result)} tokens, ${truncatedHistory.size}/${history.size} msgs kept)") }
        return result
    }

    /**
     * Truncates message history to ensure the total prompt fits within [maxTokensBudget].
     *
     * Rules:
     * 1. Always preserves the `system` prompt if it is the first message.
     * 2. Removes the oldest `user` / `assistant` messages iteratively until prompt fits.
     */
    fun truncateHistoryToContextWindow(
        history: List<ChatMessage>,
        maxTokensBudget: Int
    ): List<ChatMessage> {
        if (history.isEmpty()) return emptyList()

        var currentHistory = history.toMutableList()

        // Separate system message if present at index 0
        val systemMessage = if (currentHistory.firstOrNull()?.role == ChatMessage.ROLE_SYSTEM) {
            currentHistory.removeAt(0)
        } else {
            null
        }

        // Calculate total tokens required
        while (currentHistory.isNotEmpty()) {
            val candidateList = if (systemMessage != null) listOf(systemMessage) + currentHistory else currentHistory
            val estimatedTokens = estimateTokensForList(candidateList)

            if (estimatedTokens <= maxTokensBudget) {
                return candidateList
            }

            // Drop oldest user/assistant message turn
            runCatching { Log.w(TAG, "Context window limit reached (~$estimatedTokens tokens > budget $maxTokensBudget). Dropping oldest message: [${currentHistory.first().role}]") }
            currentHistory.removeAt(0)
        }

        // Return system message if all history had to be dropped
        return if (systemMessage != null) listOf(systemMessage) else emptyList()
    }

    /**
     * Estimates token count for a raw text string using a character-ratio heuristic.
     */
    fun estimateTokenCount(text: String): Int {
        if (text.isEmpty()) return 0
        return kotlin.math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    private fun estimateTokensForList(messages: List<ChatMessage>): Int {
        var totalChars = 0
        for (msg in messages) {
            totalChars += msg.role.length + msg.content.length + 20 // Account for ChatML tags
        }
        totalChars += 20 // Account for trailing assistant tag
        return kotlin.math.ceil(totalChars / CHARS_PER_TOKEN).toInt()
    }
}
