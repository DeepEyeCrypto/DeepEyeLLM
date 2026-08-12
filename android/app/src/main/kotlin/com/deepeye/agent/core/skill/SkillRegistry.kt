package com.deepeye.agent.core.skill

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRegistry @Inject constructor() {

    companion object {
        val BUILTIN_SKILLS = listOf(
            Skill(
                id = "hermes-agent-loop",
                name = "Hermes Autonomous Loop",
                description = "Nous Research self-improving memory, autonomous skill creation, and dialectic user modeling.",
                author = "Nous Research",
                version = "2027.2",
                downloadUrl = "https://github.com/NousResearch/hermes-agent"
            ),
            Skill(
                id = "hermes-tool-calling",
                name = "Hermes 3 Function Calling",
                description = "JSON & XML tool invocation pipeline for structured LLM function calling with chain-of-thought.",
                author = "Nous Research",
                version = "3.1.0",
                downloadUrl = "https://github.com/NousResearch/hermes-agent"
            ),
            Skill(
                id = "agentskills-standard",
                name = "AgentSkills.io Runtime",
                description = "Open standard skill runtime for cross-platform agent capabilities and plugin interop.",
                author = "AgentSkills.io",
                version = "1.0.0",
                downloadUrl = "https://agentskills.io"
            ),
            Skill(
                id = "code-auditor",
                name = "AST Code Auditor",
                description = "DeepEye AST code analysis, linting, and automated surgical patch generator with diff output.",
                author = "DeepEye Core",
                version = "1.0.0",
                downloadUrl = ""
            ),
            Skill(
                id = "tensor-optimizer",
                name = "LiteRT / GGUF Tensor Guard",
                description = "Monitors on-device RAM usage, KV cache limits, and GPU/CPU inference speed in real-time.",
                author = "DeepEye AI Edge",
                version = "1.2.0",
                downloadUrl = ""
            ),
            Skill(
                id = "sec-scanner",
                name = "Vulnerability & Exploit Scanner",
                description = "Policy-gated static analysis for Android permissions, data leaks, CVE detection, and key security.",
                author = "AEOS Security",
                version = "2027.2",
                downloadUrl = ""
            ),
            Skill(
                id = "deep-research",
                name = "Deep Research Agent",
                description = "Multi-step autonomous web research with source verification, citation graph, and synthesis reports.",
                author = "DeepEye Research",
                version = "2.0.0",
                downloadUrl = ""
            ),
            Skill(
                id = "crypto-intel",
                name = "Crypto Intelligence Engine",
                description = "Real-time on-chain analysis, whale wallet tracking, MEV detection, and DeFi risk scoring.",
                author = "DeepEye Crypto",
                version = "1.5.0",
                downloadUrl = ""
            ),
            Skill(
                id = "multimodal-rag",
                name = "Multimodal RAG Pipeline",
                description = "Vision + text retrieval-augmented generation with local vector store and hybrid BM25/embedding search.",
                author = "DeepEye AI",
                version = "1.0.0",
                downloadUrl = ""
            ),
            Skill(
                id = "auto-coder",
                name = "Autonomous Coder Agent",
                description = "Plan → Code → Test → Deploy loop with AST-aware edits, git integration, and CI/CD hooks.",
                author = "DeepEye IDE",
                version = "0.9.0",
                downloadUrl = ""
            ),
            Skill(
                id = "network-recon",
                name = "Network Recon & OSINT",
                description = "Passive network reconnaissance, DNS enumeration, subdomain discovery, and OSINT data correlation.",
                author = "AEOS Security",
                version = "1.3.0",
                downloadUrl = ""
            ),
            Skill(
                id = "prompt-shield",
                name = "Prompt Injection Shield",
                description = "Real-time detection and neutralization of prompt injection, jailbreak, and data exfiltration attacks.",
                author = "DeepEye Security",
                version = "2.1.0",
                downloadUrl = ""
            ),
            Skill(
                id = "edge-orchestrator",
                name = "Edge Model Orchestrator",
                description = "Dynamic model routing across CPU/GPU/NPU with automatic quantization selection and fallback chains.",
                author = "DeepEye AI Edge",
                version = "1.0.0",
                downloadUrl = ""
            ),
            Skill(
                id = "memory-weaver",
                name = "Episodic Memory Weaver",
                description = "Long-term episodic memory with temporal knowledge graphs, contradiction resolution, and memory decay.",
                author = "Hermes Upstream",
                version = "2.1.0",
                downloadUrl = ""
            )
        )
    }
    
    private val installedSkillIds = mutableSetOf<String>()

    private val _communitySkills = MutableStateFlow<List<Skill>>(BUILTIN_SKILLS)
    val communitySkills: StateFlow<List<Skill>> = _communitySkills.asStateFlow()
    
    fun updateSkills(skills: List<Skill>) {
        val baseList = if (skills.isNotEmpty()) skills else BUILTIN_SKILLS
        _communitySkills.value = baseList.map { skill ->
            skill.copy(isInstalled = skill.id in installedSkillIds)
        }
    }

    fun markInstalled(skillId: String) {
        installedSkillIds.add(skillId)
        _communitySkills.value = _communitySkills.value.map { skill ->
            if (skill.id == skillId) skill.copy(isInstalled = true) else skill
        }
    }
    
    fun getSkill(id: String): Skill? {
        return _communitySkills.value.find { it.id == id }
    }
    
    fun clear() {
        installedSkillIds.clear()
        _communitySkills.value = BUILTIN_SKILLS
    }
}
