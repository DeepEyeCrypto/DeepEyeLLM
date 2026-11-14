package com.deepeye.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for PromptEnhancer (src/main/kotlin/.../core/PromptEnhancer.kt).
 * Pure functions — no native dependency.
 */
class PromptEnhancerTest {

    @Test
    fun formatTaskPromptIncludesTaskAndFewShots() {
        val p = PromptEnhancer.formatTaskPrompt(
            task = "classify sentiment",
            context = "user review",
            constraints = "one word",
            fewShots = listOf("great -> positive")
        )
        assertTrue(p.contains("Task: classify sentiment"))
        assertTrue(p.contains("Context: user review"))
        assertTrue(p.contains("Constraints: one word"))
        assertTrue(p.contains("Example: great -> positive"))
    }

    @Test
    fun formatTaskPromptOmitsNullSections() {
        val p = PromptEnhancer.formatTaskPrompt(task = "summarize")
        assertTrue(p.contains("Task: summarize"))
        assertTrue(!p.contains("Context:"))
        assertTrue(!p.contains("Constraints:"))
    }

    @Test
    fun improvePromptShortInputReturnsUnchanged() {
        val short = "hi"
        assertEquals(short, PromptEnhancer.improvePrompt(short, PromptEnhancer.TaskType.CLASSIFICATION))
    }

    @Test
    fun improvePromptClassificationLongInput() {
        val p = PromptEnhancer.improvePrompt(
            "put the user text here that is long enough to pass the threshold",
            PromptEnhancer.TaskType.CLASSIFICATION
        )
        assertTrue(p.startsWith("Classify the following text into exactly one category:"))
    }

    @Test
    fun improvePromptGeneralLongInput() {
        val p = PromptEnhancer.improvePrompt(
            "a long enough prompt so that the enhancer actually does something here",
            PromptEnhancer.TaskType.GENERAL
        )
        assertTrue(p.startsWith("Please analyze and respond to the following request:"))
    }

    @Test
    fun trimPromptShortReturnsUnchanged() {
        val p = "short prompt"
        assertEquals(p, PromptEnhancer.trimPrompt(p, maxTokens = 2048))
    }

    @Test
    fun trimPromptLongTruncatesAtSentenceBoundary() {
        val sentence = "The quick brown fox jumps over the lazy dog near the river bank. "
        val longPrompt = sentence.repeat(20) // > 2048*4 chars
        val trimmed = PromptEnhancer.trimPrompt(longPrompt, maxTokens = 10)
        // 10 tokens ~ 40 chars; must end at a ". " boundary and be shorter than input
        assertTrue("trimmed should be shorter, was ${trimmed.length}", trimmed.length < longPrompt.length)
        assertTrue("expected sentence-boundary trim, got: '$trimmed'", trimmed.endsWith(". ") || trimmed.endsWith("."))
    }

    @Test
    fun addFewShotsAppendsEachExample() {
        val p = PromptEnhancer.addFewShots("base prompt", listOf("in -> out1", "in2 -> out2"))
        assertTrue(p.contains("base prompt"))
        assertTrue(p.contains("in -> out1"))
        assertTrue(p.contains("in2 -> out2"))
    }
}
