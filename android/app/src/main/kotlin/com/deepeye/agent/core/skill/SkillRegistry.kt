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
                name = "Hermes Autonomous Learning Loop",
                description = "Nous Research self-improving memory, autonomous skill creation, and dialetic user modeling.",
                author = "Nous Research",
                version = "2027.2",
                downloadUrl = "https://github.com/NousResearch/hermes-agent"
            ),
            Skill(
                id = "hermes-tool-calling",
                name = "Hermes 3 Function Calling Agent",
                description = "JSON & XML tool invocation pipeline for structured LLM function calling.",
                author = "Nous Research",
                version = "3.1.0",
                downloadUrl = "https://github.com/NousResearch/hermes-agent"
            ),
            Skill(
                id = "agentskills-standard",
                name = "AgentSkills.io Standard Runner",
                description = "Open standard skill runtime for cross-platform agent capabilities.",
                author = "AgentSkills.io",
                version = "1.0.0",
                downloadUrl = "https://agentskills.io"
            ),
            Skill(
                id = "code-auditor",
                name = "AST Code Auditor",
                description = "DeepEye AST code analysis, linting, and automated surgical patch generator.",
                author = "DeepEye Core",
                version = "1.0.0",
                downloadUrl = ""
            ),
            Skill(
                id = "tensor-optimizer",
                name = "LiteRT / GGUF Tensor Guard",
                description = "Monitors on-device RAM usage, KV cache limits, and GPU/CPU inference speed.",
                author = "DeepEye AI Edge",
                version = "1.2.0",
                downloadUrl = ""
            ),
            Skill(
                id = "sec-scanner",
                name = "Security & Vulnerability Audit",
                description = "Policy-gated static analysis for Android permissions, data leaks, and key security.",
                author = "AEOS Security",
                version = "2027.2",
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
