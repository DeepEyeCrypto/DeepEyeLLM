package com.deepeye.agent.data.local

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KnowledgeRetriever utility for Full-Text Search (FTS) retrieval.
 * Queries the local SQLite FTS database using the MATCH operator to locate top-3 relevant chunks,
 * and invisibly prepends verified context to the LLM system prompt.
 */
@Singleton
class KnowledgeRetriever @Inject constructor(
    private val knowledgeDao: KnowledgeDao
) {

    /**
     * Queries the local FTS table using SQLite MATCH operator to retrieve the top-K relevant chunks.
     */
    suspend fun retrieveRelevantChunks(query: String, topK: Int = 3): List<KnowledgeEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val ftsQuery = sanitizeFtsQuery(query)
        if (ftsQuery.isBlank()) return@withContext emptyList()

        try {
            val matchResults = knowledgeDao.searchKnowledgeMatch(ftsQuery, limit = topK)
            if (matchResults.isNotEmpty()) {
                Log.d(TAG, "FTS MATCH retrieved ${matchResults.size} chunks for '$ftsQuery'")
                return@withContext matchResults
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Knowledge MATCH search failed for '$ftsQuery': ${e.message}")
        }

        // Fallback: Individual token prefix matching
        try {
            val terms = query
                .replace(Regex("[^a-zA-Z0-9_\\s]"), " ")
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() && it.length > 2 }

            if (terms.isNotEmpty()) {
                val orQuery = terms.joinToString(" OR ") { "$it*" }
                val fallbackResults = knowledgeDao.searchKnowledgeMatch(orQuery, limit = topK)
                if (fallbackResults.isNotEmpty()) {
                    Log.d(TAG, "FTS fallback search retrieved ${fallbackResults.size} chunks for '$orQuery'")
                    return@withContext fallbackResults
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Knowledge fallback search failed: ${e.message}")
        }

        emptyList()
    }

    /**
     * Chunks and saves parsed document segments directly into the FTS knowledge table.
     */
    suspend fun insertDocumentChunks(
        docId: String,
        docTitle: String,
        chunks: List<String>,
        tags: String = "document"
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entities = chunks.mapIndexed { index, chunkText ->
                KnowledgeEntity(
                    docId = docId,
                    title = docTitle,
                    chunkIndex = index,
                    content = chunkText,
                    tags = tags,
                    timestamp = System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) {
                knowledgeDao.insertKnowledgeEntries(entities)
                Log.d(TAG, "Inserted ${entities.size} FTS chunks for '$docTitle'")
            }
            entities.size
        }
    }

    /**
     * Invisibly enriches the system prompt with verified retrieved knowledge chunks.
     */
    fun enrichSystemPrompt(baseSystemPrompt: String, chunks: List<KnowledgeEntity>): String {
        if (chunks.isEmpty()) return baseSystemPrompt

        val contextBuilder = StringBuilder()
        contextBuilder.appendLine("<retrieved_knowledge>")
        chunks.forEachIndexed { index, chunk ->
            contextBuilder.appendLine("### Chunk [${index + 1}/${chunks.size}] from '${chunk.title}' (Index: ${chunk.chunkIndex}):")
            contextBuilder.appendLine(chunk.content.trim())
            contextBuilder.appendLine()
        }
        contextBuilder.appendLine("</retrieved_knowledge>")
        contextBuilder.appendLine("Use the verified local knowledge base context above to answer accurately.")

        return if (baseSystemPrompt.isBlank()) {
            contextBuilder.toString()
        } else {
            "${contextBuilder.toString().trim()}\n\n$baseSystemPrompt"
        }
    }

    private fun sanitizeFtsQuery(query: String): String {
        val cleanTerms = query
            .replace(Regex("[^a-zA-Z0-9_\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it.length > 1 }

        if (cleanTerms.isEmpty()) return ""
        return cleanTerms.joinToString(" OR ") { "$it*" }
    }

    companion object {
        private const val TAG = "DeepEye-KnowledgeRetriever"
    }
}
