package com.deepeye.agent.features.hermes

import com.deepeye.agent.core.memory.MemoryDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesMemoryAdapter @Inject constructor(
    private val memoryDao: MemoryDao
) {
    /**
     * Given a user prompt, retrieves relevant past memories and formats them
     * into a RAG (Retrieval-Augmented Generation) context block.
     */
    suspend fun buildContext(query: String, maxItems: Int = 3): String {
        // Simple search query generation by grabbing words over length 4
        val keywords = query.split(Regex("\\W+"))
            .filter { it.length > 4 }
            .take(3)
            
        val results = mutableSetOf<String>()
        
        for (word in keywords) {
            val matches = memoryDao.searchMemories(word, 2)
            results.addAll(matches.map { it.content })
        }
        
        // Fallback to most recent if search yields nothing
        if (results.isEmpty()) {
            val recent = memoryDao.getRecentMemories(maxItems)
            results.addAll(recent.map { it.content })
        }
        
        if (results.isEmpty()) return ""
        
        return buildString {
            appendLine("=== RELEVANT CONTEXT (from your memory database) ===")
            results.take(maxItems).forEachIndexed { index, memory ->
                appendLine("[Memory ${index + 1}]: $memory")
            }
            appendLine("====================================================")
        }
    }
}
