package com.deepeye.agent.data.local

import androidx.room.Entity
import androidx.room.Fts4

/**
 * SQLite FTS4 virtual table for high-speed full-text indexing and BM25 search over KnowledgeChunkEntity.
 */
@Fts4(contentEntity = KnowledgeChunkEntity::class)
@Entity(tableName = "knowledge_chunks_fts")
data class KnowledgeFtsEntity(
    val docTitle: String,
    val content: String,
    val tags: String
)
