package com.deepeye.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Knowledge RAG chunk indexing and high-speed full-text & keyword retrieval.
 */
@Dao
interface KnowledgeDao {

    // --- KnowledgeChunkEntity & KnowledgeFtsEntity methods ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: KnowledgeChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("""
        SELECT knowledge_chunks.* FROM knowledge_chunks
        JOIN knowledge_chunks_fts ON knowledge_chunks.rowid = knowledge_chunks_fts.rowid
        WHERE knowledge_chunks_fts MATCH :query
        LIMIT :limit
    """)
    suspend fun searchFts(query: String, limit: Int = 10): List<KnowledgeChunkEntity>

    @Query("""
        SELECT * FROM knowledge_chunks 
        WHERE content LIKE '%' || :query || '%' OR docTitle LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchLike(query: String, limit: Int = 10): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE docId = :docId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDoc(docId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChunks(limit: Int = 100): List<KnowledgeChunkEntity>

    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun getChunkCount(): Int

    @Query("DELETE FROM knowledge_chunks WHERE docId = :docId")
    suspend fun deleteChunksByDocId(docId: String)

    @Query("DELETE FROM knowledge_chunks")
    suspend fun clearAllChunks()

    // --- FTS Virtual Table: KnowledgeEntity methods ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledge(entry: KnowledgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeEntries(entries: List<KnowledgeEntity>)

    @Query("SELECT rowid, doc_id, title, chunkIndex, content, tags, timestamp FROM knowledge_entries WHERE knowledge_entries MATCH :query LIMIT :limit")
    suspend fun searchKnowledgeMatch(query: String, limit: Int = 3): List<KnowledgeEntity>

    @Query("SELECT rowid, doc_id, title, chunkIndex, content, tags, timestamp FROM knowledge_entries WHERE doc_id = :docId ORDER BY chunkIndex ASC")
    suspend fun getKnowledgeForDoc(docId: String): List<KnowledgeEntity>

    @Query("SELECT rowid, doc_id, title, chunkIndex, content, tags, timestamp FROM knowledge_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentKnowledge(limit: Int = 10): List<KnowledgeEntity>

    @Query("DELETE FROM knowledge_entries WHERE doc_id = :docId")
    suspend fun deleteKnowledgeByDocId(docId: String)

    @Query("DELETE FROM knowledge_entries")
    suspend fun clearKnowledgeEntries()

    @Query("SELECT COUNT(*) FROM knowledge_entries")
    suspend fun getKnowledgeCount(): Int
}

