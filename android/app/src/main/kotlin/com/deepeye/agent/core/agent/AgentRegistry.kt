package com.deepeye.agent.core.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRegistry @Inject constructor() {

    private val defaultAgents = listOf(
        AgentSpec(
            id = "hermes_reasoning_agent",
            name = "Nous Hermes 3 Reasoning Agent",
            role = "General Intelligence & Function Calling Agent",
            systemPrompt = "You are Hermes 3, a state-of-the-art autonomous reasoning agent built by Nous Research. Execute tasks with step-by-step logic, clear tool use, and self-reflection.",
            tools = listOf("web_search", "hermes_memory", "llama_cpp"),
            iconEmoji = "🧠",
            isCustom = false
        ),
        AgentSpec(
            id = "crypto_dex_trader",
            name = "Crypto DEX Deep Researcher",
            role = "Solana & EVM Liquidity & Smart Money Trader",
            systemPrompt = "You are a quantitative Web3 crypto trading agent. Analyze token liquidity, volume flow, holder concentration, and contract security risks.",
            tools = listOf("dex_screener", "web_search", "hermes_memory"),
            iconEmoji = "📈",
            isCustom = false
        ),
        AgentSpec(
            id = "security_auditor",
            name = "Deep Security Audit Agent",
            role = "Vulnerability & Policy Enforcement Auditor",
            systemPrompt = "You are an expert security researcher. Perform static analysis, zero-trust policy audits, and vulnerability mitigation.",
            tools = listOf("hermes_memory", "llama_cpp", "code_runner"),
            iconEmoji = "🛡️",
            isCustom = false
        ),
        AgentSpec(
            id = "code_refactor_pro",
            name = "Autonomous Code Refactor Pro",
            role = "Kotlin, C++ NDK & Architecture Optimizer",
            systemPrompt = "You are a senior Android NDK & Compose architect. Write surgical, TDD-backed code edits maintaining 0-latency hot-path performance.",
            tools = listOf("code_runner", "hermes_memory"),
            iconEmoji = "💻",
            isCustom = false
        )
    )

    private val _agents = MutableStateFlow<List<AgentSpec>>(defaultAgents)
    val agents: StateFlow<List<AgentSpec>> = _agents.asStateFlow()

    fun addCustomAgent(name: String, role: String, systemPrompt: String, tools: List<String>, iconEmoji: String = "⚡"): AgentSpec {
        val newAgent = AgentSpec(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            role = role,
            systemPrompt = systemPrompt,
            tools = tools,
            iconEmoji = iconEmoji,
            isCustom = true
        )
        _agents.value = _agents.value + newAgent
        return newAgent
    }

    fun getAgentById(id: String): AgentSpec? {
        return _agents.value.find { it.id == id }
    }
}
