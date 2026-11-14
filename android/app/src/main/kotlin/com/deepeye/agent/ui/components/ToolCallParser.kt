package com.deepeye.agent.ui.components

import com.deepeye.agent.core.model.ToolCallModel
import com.deepeye.agent.core.model.ToolCallStatus
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class ParsedToolMessage(
    val cleanText: String,
    val toolCalls: List<ToolCallModel>
)

object ToolCallParser {
    private val gson = Gson()
    
    // Regex to match <tool_call> block and everything inside
    private val toolCallRegex = Regex("<tool_call>([\\s\\S]*?)(?:</tool_call>|$)", RegexOption.IGNORE_CASE)
    // Legacy Qwen function blocks
    private val functionRegex = Regex("<function=([^>]+)>([\\s\\S]*?)(?:</function>|$)", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, isStreaming: Boolean): ParsedToolMessage {
        var mutableCleanText = rawText
        val parsedToolCalls = mutableListOf<ToolCallModel>()

        // 1. Fallback to extracting <function> blocks
        var funcMatch = functionRegex.find(mutableCleanText)
        while (funcMatch != null) {
            val fullMatch = funcMatch.value
            val funcName = funcMatch.groupValues[1].trim()
            val innerContent = funcMatch.groupValues.getOrNull(2)?.trim() ?: ""
            val isClosed = fullMatch.contains("</function>", ignoreCase = true)
            
            parsedToolCalls.add(
                ToolCallModel(
                    name = funcName,
                    arguments = innerContent,
                    status = if (isClosed) ToolCallStatus.SUCCESS else if (isStreaming) ToolCallStatus.EXECUTING else ToolCallStatus.SUCCESS
                )
            )
            mutableCleanText = mutableCleanText.replace(fullMatch, "").trim()
            funcMatch = functionRegex.find(mutableCleanText)
        }

        // 2. Extract standard <tool_call> json blocks
        var toolMatch = toolCallRegex.find(mutableCleanText)
        while (toolMatch != null) {
            val fullMatch = toolMatch.value
            val innerContent = toolMatch.groupValues[1].trim()
            val isClosed = fullMatch.contains("</tool_call>", ignoreCase = true)

            var parsedName = "Unknown Tool"
            var parsedArgs = innerContent
            
            if (innerContent.startsWith("{")) {
                try {
                    val map = gson.fromJson(innerContent, Map::class.java)
                    parsedName = map["name"]?.toString() ?: "Unknown Tool"
                    val argsObj = map["arguments"]
                    parsedArgs = if (argsObj is Map<*, *>) gson.toJson(argsObj) else argsObj?.toString() ?: ""
                } catch (e: JsonSyntaxException) {
                    // Try regex fallback for partial json
                    val nameRegex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
                    val match = nameRegex.find(innerContent)
                    if (match != null) {
                        parsedName = match.groupValues[1]
                    }
                }
            }

            parsedToolCalls.add(
                ToolCallModel(
                    name = parsedName,
                    arguments = parsedArgs,
                    status = if (isClosed) ToolCallStatus.SUCCESS else if (isStreaming) ToolCallStatus.EXECUTING else ToolCallStatus.SUCCESS
                )
            )

            mutableCleanText = mutableCleanText.replace(fullMatch, "").trim()
            toolMatch = toolCallRegex.find(mutableCleanText)
        }

        return ParsedToolMessage(
            cleanText = mutableCleanText.trimStart('\n', '\r'),
            toolCalls = parsedToolCalls
        )
    }
}