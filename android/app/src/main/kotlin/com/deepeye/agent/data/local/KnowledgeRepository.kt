package com.deepeye.agent.data.local

import android.util.Log
import com.deepeye.agent.analysis.ParsedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * Sovereign On-Device Knowledge Repository & Hybrid RAG Engine.
 * Combines SQLite FTS4 full-text matching with BM25/TF-IDF token scoring
 * to retrieve only the top-K highest relevance chunks for prompt context injection.
 */
@Singleton
class KnowledgeRepository @Inject constructor(
    private val knowledgeDao: KnowledgeDao
) {

    suspend fun indexDocument(doc: ParsedDocument): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entities = doc.chunks.map { chunk ->
                KnowledgeChunkEntity(
                    docId = doc.id,
                    docTitle = doc.fileName,
                    chunkIndex = chunk.index,
                    content = chunk.text,
                    tags = if (doc.isPdf) "pdf,document" else "text,code",
                    tokenCount = chunk.charLength / 4,
                    timestamp = System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) {
                knowledgeDao.insertChunks(entities)
                Log.d(TAG, "Indexed ${entities.size} chunks for document '${doc.fileName}'")
            }
            entities.size
        }
    }

    suspend fun searchTopChunks(query: String, topK: Int = 3): List<KnowledgeChunkEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val rawKeywords = query
            .replace(Regex("[^a-zA-Z0-9_\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
            .map { it.lowercase() }
            .distinct()

        val candidates = mutableMapOf<String, KnowledgeChunkEntity>()

        // 1. FTS4 Search
        if (rawKeywords.isNotEmpty()) {
            val ftsQuery = rawKeywords.joinToString(" OR ") { "$it*" }
            try {
                val ftsResults = knowledgeDao.searchFts(ftsQuery, limit = topK * 4)
                ftsResults.forEach { candidates[it.id] = it }
            } catch (e: Throwable) {
                Log.w(TAG, "FTS query fallback on '$ftsQuery': ${e.message}")
            }
        }

        // 2. Keyword LIKE search fallback
        for (kw in rawKeywords.take(4)) {
            val likeResults = knowledgeDao.searchLike(kw, limit = topK * 3)
            likeResults.forEach { candidates[it.id] = it }
        }

        if (candidates.isEmpty()) {
            // Return recent chunks if nothing matched
            return@withContext knowledgeDao.getRecentChunks(topK)
        }

        // 3. Compute BM25 / TF-IDF Ranking Scores
        val scoredList = candidates.values.map { chunk ->
            val score = computeBm25Score(queryKeywords = rawKeywords, chunk = chunk)
            Pair(chunk, score)
        }

        scoredList
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private fun computeBm25Score(queryKeywords: List<String>, chunk: KnowledgeChunkEntity): Double {
        if (queryKeywords.isEmpty()) return 0.0

        val chunkText = chunk.content.lowercase()
        val titleText = chunk.docTitle.lowercase()
        val words = chunkText.split("\\s+".toRegex())
        val docLen = words.size.coerceAtLeast(1)
        val avgDocLen = 150.0 // Typical ~1000 char chunk length in words

        val k1 = 1.2
        val b = 0.75

        var totalScore = 0.0

        for (kw in queryKeywords) {
            val countInContent = words.count { it.contains(kw) }
            val countInTitle = if (titleText.contains(kw)) 3 else 0
            val tf = countInContent + countInTitle

            if (tf > 0) {
                val idf = ln((100.0 + 1.0) / (1.0 + 0.5)) // Approximate IDF
                val numerator = tf * (k1 + 1.0)
                val denominator = tf + k1 * (1.0 - b + b * (docLen / avgDocLen))
                totalScore += idf * (numerator / denominator)
            }
        }

        return totalScore
    }

    fun formatRagPrompt(userPrompt: String, topChunks: List<KnowledgeChunkEntity>): String {
        if (topChunks.isEmpty()) return userPrompt

        val sb = StringBuilder()
        sb.appendLine("<retrieved_knowledge>")
        topChunks.forEachIndexed { index, chunk ->
            sb.appendLine("### Chunk [${index + 1}/${topChunks.size}] from '${chunk.docTitle}' (Chunk Index: ${chunk.chunkIndex}):")
            sb.appendLine(chunk.content.trim())
            sb.appendLine()
        }
        sb.appendLine("</retrieved_knowledge>")
        sb.appendLine()
        sb.appendLine("Context from the Knowledge Base is provided above. Answer the user query accurately using this verified information. Cite the source document when appropriate.")
        sb.appendLine()
        sb.appendLine("User: $userPrompt")

        return sb.toString()
    }

    suspend fun getChunkCount(): Int = withContext(Dispatchers.IO) {
        knowledgeDao.getChunkCount()
    }

    suspend fun clearKnowledgeBase() = withContext(Dispatchers.IO) {
        knowledgeDao.clearAllChunks()
    }

    companion object {
        private const val TAG = "DeepEye-KnowledgeRepo"
    }
}
