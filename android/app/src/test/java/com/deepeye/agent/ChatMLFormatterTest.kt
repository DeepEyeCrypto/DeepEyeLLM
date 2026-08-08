package com.deepeye.agent

import com.deepeye.agent.core.model.ChatMessage
import com.deepeye.agent.core.model.ChatMLFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMLFormatterTest {

    @Test
    fun `formatPromptWithHistory formats ChatML correctly`() {
        val history = listOf(
            ChatMessage.system("You are DeepEye, an AI coding assistant."),
            ChatMessage.user("What is Kotlin?"),
            ChatMessage.assistant("Kotlin is a concise, type-safe programming language.")
        )

        val formatted = ChatMLFormatter.formatPromptWithHistory(history, maxContextTokens = 2048)

        assertTrue(formatted.contains("<|im_start|>system\nYou are DeepEye, an AI coding assistant.<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nWhat is Kotlin?<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>assistant\nKotlin is a concise, type-safe programming language.<|im_end|>"))
        assertTrue(formatted.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `truncateHistoryToContextWindow preserves system prompt and drops oldest messages`() {
        val systemMsg = ChatMessage.system("System prompt")
        val history = mutableListOf(systemMsg)
        
        // Add 10 long messages
        for (i in 1..10) {
            history.add(ChatMessage.user("User message $i " + "A".repeat(200)))
            history.add(ChatMessage.assistant("Assistant response $i " + "B".repeat(200)))
        }

        // Restrict token budget so only system prompt fits and oldest user messages are dropped
        val truncated = ChatMLFormatter.truncateHistoryToContextWindow(history, maxTokensBudget = 50)

        // Verify system prompt is retained at index 0
        assertEquals(ChatMessage.ROLE_SYSTEM, truncated.first().role)
        assertTrue(truncated.size < history.size)
        // Verify oldest user messages (e.g. "User message 1") were dropped
        assertTrue(truncated.none { it.content.contains("User message 1") })
    }
}
