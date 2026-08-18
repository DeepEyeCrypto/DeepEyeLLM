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

    /**
     * Applies a simple string replacement patch to the source code.
     * This mimics Roo Code's block-based replacement without needing a full diff parser.
     * 
     * @param originalCode The full content of the file.
     * @param targetBlock The exact block of code to replace (must match exactly).
     * @param newBlock The new code block to insert.
     * @return The updated file content, or throws an exception if the block wasn't found.
     */
    fun applyBlockReplacement(originalCode: String, targetBlock: String, newBlock: String): String {
        // Strip leading/trailing whitespace for more resilient matching if needed,
        // but for exact matches we keep it strict to avoid accidental overwrites.
        val strictTarget = targetBlock.trim()
        val strictOriginal = originalCode
        
        if (!strictOriginal.contains(strictTarget)) {
            // Fallback: try removing carriage returns in case of Windows/Unix mismatch
            val normalizedTarget = strictTarget.replace("\r\n", "\n")
            val normalizedOriginal = strictOriginal.replace("\r\n", "\n")
            
            if (!normalizedOriginal.contains(normalizedTarget)) {
                throw IllegalArgumentException("Patch Failed: Target block not found in original code.")
            }
            return normalizedOriginal.replace(normalizedTarget, newBlock.trimEnd())
        }
        
        return strictOriginal.replace(strictTarget, newBlock.trimEnd())
    }
}
