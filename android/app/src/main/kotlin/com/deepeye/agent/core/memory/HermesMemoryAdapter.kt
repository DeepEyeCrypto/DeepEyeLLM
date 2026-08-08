package com.deepeye.agent.core.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesMemoryAdapter @Inject constructor(
    private val memoryDao: MemoryDao
) {
    suspend fun saveMemory(content: String, tags: String = "") {
        memoryDao.insertMemory(MemoryEntity(content = content, tags = tags))
    }

    suspend fun getContext(query: String, maxContexts: Int = 3): String {
        val memories = memoryDao.searchMemories(query, maxContexts)
        if (memories.isEmpty()) {
            return ""
        }
        return "Relevant past context:\n" + memories.joinToString("\n") { "- ${it.content}" }
    }

    suspend fun getRecentContext(limit: Int = 5): String {
        val memories = memoryDao.getRecentMemories(limit)
        if (memories.isEmpty()) {
            return ""
        }
        return "Recent context:\n" + memories.joinToString("\n") { "- ${it.content}" }
    }
}
