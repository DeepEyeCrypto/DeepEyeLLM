package com.deepeye.agent.ui.components

import com.deepeye.agent.core.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun parse_withValidToolCallJson_extractsToolCallAndCleanText() {
        val raw = "I will search the web for the latest CVE.<tool_call>{\"name\":\"web_search\",\"arguments\":{\"query\":\"CVE-2024-38063\"}}</tool_call>Let me analyze."
        val result = ToolCallParser.parse(raw, isStreaming = false)

        assertEquals("I will search the web for the latest CVE.Let me analyze.", result.cleanText)
        assertEquals(1, result.toolCalls.size)
        assertEquals("web_search", result.toolCalls[0].name)
        assertTrue(result.toolCalls[0].arguments.contains("CVE-2024-38063"))
        assertEquals(ToolCallStatus.SUCCESS, result.toolCalls[0].status)
    }

    @Test
    fun parse_withStreamingToolCall_marksStatusExecuting() {
        val raw = "Starting device inspection...<tool_call>{\"name\":\"adb_command\",\"arguments\":{\"command\":\"getprop ro.build.version.release\"}"
        val result = ToolCallParser.parse(raw, isStreaming = true)

        assertEquals("Starting device inspection...", result.cleanText)
        assertEquals(1, result.toolCalls.size)
        assertEquals("adb_command", result.toolCalls[0].name)
        assertEquals(ToolCallStatus.EXECUTING, result.toolCalls[0].status)
    }

    @Test
    fun parse_withLegacyFunctionTag_extractsProperly() {
        val raw = "<function=fetch_device_info>{}</function>Device info retrieved successfully."
        val result = ToolCallParser.parse(raw, isStreaming = false)

        assertEquals("Device info retrieved successfully.", result.cleanText)
        assertEquals(1, result.toolCalls.size)
        assertEquals("fetch_device_info", result.toolCalls[0].name)
        assertEquals(ToolCallStatus.SUCCESS, result.toolCalls[0].status)
    }

    @Test
    fun parse_withNoToolCalls_returnsOriginalText() {
        val raw = "Everything is running smoothly."
        val result = ToolCallParser.parse(raw, isStreaming = false)

        assertEquals("Everything is running smoothly.", result.cleanText)
        assertTrue(result.toolCalls.isEmpty())
    }
}
