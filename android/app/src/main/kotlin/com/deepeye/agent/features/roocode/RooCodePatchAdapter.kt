package com.deepeye.agent.features.roocode

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RooCodePatchAdapter @Inject constructor() {

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
