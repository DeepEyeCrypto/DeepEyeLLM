package com.deepeye.agent.features.skills

import android.util.Log

/**
 * A single skill entry in the marketplace catalog.
 */
data class SkillEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val capabilities: Set<String>,        // e.g., "code_review", "file_analysis", "summarization"
    val requiredModels: List<String>,      // model IDs needed to run this skill
    val downloadUrl: String?,
    val isBuiltIn: Boolean = false,
    val isInstalled: Boolean = false,
    val sizeBytes: Long = 0L,
)

/**
 * Registry of all available skills — both built-in and community-contributed.
 * In production, the community catalog would be fetched from the Cloud Gateway.
 */
class BuiltInSkillCatalog {

    private val skills = mutableListOf<SkillEntry>()

    init {
        // Seed built-in skills
        skills.addAll(BUILT_IN_SKILLS)
    }

    fun getAll(): List<SkillEntry> = skills.toList()

    fun getInstalled(): List<SkillEntry> = skills.filter { it.isInstalled || it.isBuiltIn }

    fun getByCapability(capability: String): List<SkillEntry> =
        skills.filter { capability in it.capabilities }

    fun getById(id: String): SkillEntry? = skills.find { it.id == id }

    /**
     * Simulate installing a community skill.
     */
    fun install(skillId: String): Boolean {
        val index = skills.indexOfFirst { it.id == skillId }
        if (index == -1) return false
        skills[index] = skills[index].copy(isInstalled = true)
        Log.d("DeepEye-Skills", "Installed skill: $skillId")
        return true
    }

    /**
     * Merge remote catalog entries (from Cloud Gateway sync).
     */
    fun mergeRemoteCatalog(remote: List<SkillEntry>) {
        for (entry in remote) {
            val existing = skills.indexOfFirst { it.id == entry.id }
            if (existing != -1) {
                // Update metadata but preserve install state
                skills[existing] = entry.copy(isInstalled = skills[existing].isInstalled)
            } else {
                skills.add(entry)
            }
        }
        Log.d("DeepEye-Skills", "Merged ${remote.size} remote skills. Total: ${skills.size}")
    }

    companion object {
        val BUILT_IN_SKILLS = listOf(
            SkillEntry(
                id = "chat",
                name = "Chat",
                version = "1.0.0",
                description = "General-purpose conversational AI with context memory.",
                author = "DeepEye",
                capabilities = setOf("conversation", "summarization", "translation"),
                requiredModels = listOf("gemma-4-2b-q4km"),
                downloadUrl = null,
                isBuiltIn = true,
                isInstalled = true,
            ),
            SkillEntry(
                id = "code_review",
                name = "Code Review",
                version = "1.0.0",
                description = "Analyze code for bugs, security issues, and style improvements.",
                author = "DeepEye",
                capabilities = setOf("code_review", "file_analysis"),
                requiredModels = listOf("gemma-4-4b-q4km"),
                downloadUrl = null,
                isBuiltIn = true,
                isInstalled = true,
            ),
            SkillEntry(
                id = "ask_image",
                name = "Ask Image",
                version = "1.0.0",
                description = "Analyze images and answer questions about visual content.",
                author = "DeepEye",
                capabilities = setOf("vision", "image_analysis"),
                requiredModels = listOf("gemma-4-2b-q4km"),
                downloadUrl = null,
                isBuiltIn = true,
                isInstalled = true,
            ),
            SkillEntry(
                id = "audio_scribe",
                name = "Audio Scribe",
                version = "1.0.0",
                description = "Transcribe and analyze audio files.",
                author = "DeepEye",
                capabilities = setOf("audio", "transcription"),
                requiredModels = listOf("gemma-4-4b-q4km"),
                downloadUrl = null,
                isBuiltIn = true,
                isInstalled = true,
            ),
            SkillEntry(
                id = "function_router",
                name = "Function Router",
                version = "1.0.0",
                description = "Route user intents to Android Mobile Actions via function calling.",
                author = "DeepEye",
                capabilities = setOf("function_calling", "mobile_actions"),
                requiredModels = listOf("function-gemma-270m"),
                downloadUrl = null,
                isBuiltIn = true,
                isInstalled = true,
            ),
            SkillEntry(
                id = "deep_debug",
                name = "Deep Debug (Cloud)",
                version = "1.0.0",
                description = "Upload files to the Cloud Gateway for in-depth debugging and analysis.",
                author = "DeepEye",
                capabilities = setOf("file_analysis", "cloud_debug"),
                requiredModels = emptyList(), // runs on cloud
                downloadUrl = null,
                isBuiltIn = true,
                isInstalled = true,
            ),
        )
    }
}
