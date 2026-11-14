package com.deepeye.agent.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * SQLite Full-Text Search (FTS) virtual table entity for local sovereign RAG & knowledge retrieval.
 * Stores chunked documents and parsed files indexed for high-performance MATCH operator retrieval
 * without requiring full documents to be kept in active memory.
 */
@Entity(tableName = "knowledge_entries")
@Fts4
data class KnowledgeEntity(
    /**
     * FTS rowid. Room declares no column for it (the value is SQLite's implicit rowid),
     * so every query returning this entity selects `rowid` explicitly.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowid: Int = 0,
    /**
     * NOTE: this column is deliberately named "doc_id" and not "docId".
     * `docid` (case-insensitive) is a reserved alias of the FTS3/FTS4 rowid:
     * declaring a column with that name makes `CREATE VIRTUAL TABLE ... USING FTS4(...)`
     * fail at runtime with "vtable constructor failed". Verified against the
     * SQLite build Room's validator uses.
     */
    @ColumnInfo(name = "doc_id")
    val docId: String,
    val title: String,
    val chunkIndex: Int,
    val content: String,
    val tags: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
