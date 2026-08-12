package com.deepeye.agent.agent

import com.deepeye.agent.domain.EngineController

class Agent(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val tools: List<Tool> = emptyList(),
    private val engineController: EngineController
) {
    suspend fun generate(prompt: String, onChunk: (String) -> Unit = {}) {
        val fullPrompt = "$systemPrompt\n\nUser Request: $prompt\nAssistant Response:"
        engineController.executeChatStream(fullPrompt, onChunk)
    }
}
