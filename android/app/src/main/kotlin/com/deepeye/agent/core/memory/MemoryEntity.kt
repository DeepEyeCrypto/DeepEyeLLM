package com.deepeye.agent.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val tags: String = "" // Comma separated tags
)
