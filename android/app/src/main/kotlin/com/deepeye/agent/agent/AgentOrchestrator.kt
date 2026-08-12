package com.deepeye.agent.agent

import com.deepeye.agent.domain.EngineController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentOrchestrator @Inject constructor(
    private val engineController: EngineController
) {
    val coderAgent = Agent(
        id = "coder",
        name = "Coder Agent",
        systemPrompt = "You are a Senior Systems and Android NDK Developer. Write clean, optimal code.",
        tools = listOf(CodeExecutionTool()),
        engineController = engineController
    )

    val analystAgent = Agent(
        id = "analyst",
        name = "Analyst Agent",
        systemPrompt = "You are a Security and System Performance Analyst. Focus on root cause analysis.",
        tools = listOf(SearchTool()),
        engineController = engineController
    )

    val summarizerAgent = Agent(
        id = "summarizer",
        name = "Summarizer Agent",
        systemPrompt = "You are an Executive Tech Writer. Summarize complex context concisely.",
        tools = emptyList(),
        engineController = engineController
    )

    fun classifyIntent(prompt: String): Agent {
        val lower = prompt.lowercase()
        return when {
            lower.contains("code") || lower.contains("bug") || lower.contains("fun") || lower.contains("class") -> coderAgent
            lower.contains("analyze") || lower.contains("perf") || lower.contains("stat") -> analystAgent
            else -> summarizerAgent
        }
    }

    suspend fun processUserPrompt(prompt: String, onChunk: (String) -> Unit) {
        val selectedAgent = classifyIntent(prompt)
        selectedAgent.generate(prompt, onChunk)
    }
}
