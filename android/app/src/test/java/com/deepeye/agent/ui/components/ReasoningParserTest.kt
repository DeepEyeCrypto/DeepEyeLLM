package com.deepeye.agent.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningParserTest {

    @Test
    fun parse_withCompletedThinkTags_separatesThinkingAndResponse() {
        val raw = "<think>Analyzing smart contract bytecode for reentrancy vectors</think>Contract analysis complete: Zero reentrancy flaws found."
        val result = ReasoningParser.parse(raw, isStreaming = false)

        assertEquals("Analyzing smart contract bytecode for reentrancy vectors", result.thoughtTrace)
        assertEquals("Contract analysis complete: Zero reentrancy flaws found.", result.finalResponse)
        assertFalse(result.isThinkingActive)
    }

    @Test
    fun parse_withStreamingThinkTag_identifiesActiveThinkingState() {
        val raw = "<think>Calculating on-device memory fit and KV cache bounds..."
        val result = ReasoningParser.parse(raw, isStreaming = true)

        assertEquals("Calculating on-device memory fit and KV cache bounds...", result.thoughtTrace)
        assertEquals("", result.finalResponse)
        assertTrue(result.isThinkingActive)
    }

    @Test
    fun parse_withNoThinkTags_returnsRawResponse() {
        val raw = "Hello! I am DeepEye Edge AI Workstation."
        val result = ReasoningParser.parse(raw, isStreaming = false)

        assertEquals(null, result.thoughtTrace)
        assertEquals("Hello! I am DeepEye Edge AI Workstation.", result.finalResponse)
        assertFalse(result.isThinkingActive)
    }

    @Test
    fun parse_withThoughtTag_supportsAlternativeTags() {
        val raw = "<thought>Checking LiteRT delegate availability</thought>LiteRT NPU delegate active."
        val result = ReasoningParser.parse(raw, isStreaming = false)

        assertEquals("Checking LiteRT delegate availability", result.thoughtTrace)
        assertEquals("LiteRT NPU delegate active.", result.finalResponse)
        assertFalse(result.isThinkingActive)
    }
}
