package com.deepeye.agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a chunk of a document or knowledge base entry.
 * Stored persistently in Room for sovereign on-device RAG retrieval.
 */
@Entity(tableName = "knowledge_chunks")
data class KnowledgeChunkEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val docId: String,
    val docTitle: String,
    val chunkIndex: Int,
    val content: String,
    val tags: String = "",
    val tokenCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
