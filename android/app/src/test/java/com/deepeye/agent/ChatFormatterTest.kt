package com.deepeye.agent

import com.deepeye.agent.core.model.ChatMessage
import com.deepeye.agent.core.model.ChatFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFormatterTest {

    @Test
    fun `formatPrompt formats history with ChatML syntax correctly`() {
        val history = listOf(
            ChatMessage.system("You are DeepEye, an AI assistant."),
            ChatMessage.user("Explain JNI."),
            ChatMessage.assistant("JNI allows Java to call native C++ code.")
        )

        val formatted = ChatFormatter.formatPrompt(history, maxContextTokens = 1024)

        assertTrue(formatted.contains("<|im_start|>system\nYou are DeepEye, an AI assistant.<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nExplain JNI.<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>assistant\nJNI allows Java to call native C++ code.<|im_end|>"))
        assertTrue(formatted.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `truncateHistory enforces 1024 token safeguard and retains system prompt`() {
        val systemMsg = ChatMessage.system("System prompt")
        val history = mutableListOf(systemMsg)

        // Add 10 long messages
        for (i in 1..10) {
            history.add(ChatMessage.user("User message $i " + "A".repeat(200)))
            history.add(ChatMessage.assistant("Assistant response $i " + "B".repeat(200)))
        }

        // Restrict token limit to 50 tokens
        val truncated = ChatFormatter.truncateHistory(history, maxTokenLimit = 50)

        // Verify system prompt is preserved at index 0
        assertEquals(ChatMessage.ROLE_SYSTEM, truncated.first().role)
        assertTrue(truncated.size < history.size)
        // Verify oldest user messages were dropped
        assertTrue(truncated.none { it.content.contains("User message 1") })
    }
}
