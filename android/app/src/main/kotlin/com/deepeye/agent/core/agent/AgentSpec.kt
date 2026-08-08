package com.deepeye.agent.core.agent

/**
 * Defines an autonomous AI Agent specification including role, system prompt, and tool capabilities.
 */
data class AgentSpec(
    val id: String,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val tools: List<String> = listOf("web_search", "dex_screener", "hermes_memory"),
    val temperature: Float = 0.7f,
    val iconEmoji: String = "🤖",
    val isCustom: Boolean = false
)

data class AgentExecutionStep(
    val stepIndex: Int,
    val phase: ExecutionPhase,
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ExecutionPhase {
    PLANNING,
    TOOL_ACTING,
    OBSERVATION,
    SYNTHESIS,
    COMPLETE
}
