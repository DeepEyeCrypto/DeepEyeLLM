package com.deepeye.agent.core.ide

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RooCodePatchAdapter @Inject constructor(
    private val workspaceManager: WorkspaceManager
) {

    /**
     * Applies a unified diff to a file in the virtual workspace.
     * For now, this is a simplified stub that replaces the entire file if it's a full replacement,
     * or does basic string replacement based on diff markers.
     * In a full implementation, this would use a robust patch parsing library.
     */
    fun applyPatch(relativePath: String, originalText: String, newText: String): Boolean {
        val currentContent = workspaceManager.readFile(relativePath) ?: ""
        
        // Simple search and replace for vibe coding style patches
        val updatedContent = if (currentContent.contains(originalText) && originalText.isNotBlank()) {
            currentContent.replace(originalText, newText)
        } else {
            // If we can't find exact text, just append or rewrite if file was empty
            if (currentContent.isEmpty()) newText else "$currentContent\n$newText"
        }

        return workspaceManager.writeFile(relativePath, updatedContent)
    }

    /**
     * Simulates generating a basic diff format between old and new text.
     */
    fun generateDiff(originalText: String, newText: String): String {
        return """
            |<<<< ORIGINAL
            |$originalText
            |====
            |$newText
            |>>>> REPLACED
        """.trimMargin()
    }
}
