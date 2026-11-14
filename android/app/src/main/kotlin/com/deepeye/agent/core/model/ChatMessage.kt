package com.deepeye.agent.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Data class and Room entity representing a single message in a multi-turn chat conversation.
 *
 * @property id Unique identifier for Room primary key and Compose key stability.
 * @property role Standard ChatML role: "system", "user", or "assistant".
 * @property content The text message content.
 * @property timestamp Unix epoch millisecond timestamp for chronological ordering.
 * @property isStreaming Flag indicating if the message is currently receiving streaming tokens.
 * @property isError Flag indicating if generation failed for this message.
 * @property imageUris Attached local image URI strings for multimodal vision analysis.
 * @property toolCalls List of executed or in-progress agentic tool calls.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val imageUris: List<String> = emptyList(),
    val toolCalls: List<ToolCallModel> = emptyList()
) {
    val isAssistant: Boolean get() = role == ROLE_ASSISTANT
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun system(
            content: String,
            id: String = UUID.randomUUID().toString(),
            timestamp: Long = System.currentTimeMillis()
        ) = ChatMessage(id = id, role = ROLE_SYSTEM, content = content, timestamp = timestamp)

        fun user(
            content: String,
            imageUris: List<String> = emptyList(),
            id: String = UUID.randomUUID().toString(),
            timestamp: Long = System.currentTimeMillis()
        ) = ChatMessage(
            id = id,
            role = ROLE_USER,
            content = content,
            timestamp = timestamp,
            imageUris = imageUris
        )

        fun assistant(
            content: String,
            id: String = UUID.randomUUID().toString(),
            timestamp: Long = System.currentTimeMillis(),
            isStreaming: Boolean = false,
            isError: Boolean = false,
            toolCalls: List<ToolCallModel> = emptyList()
        ) = ChatMessage(
            id = id,
            role = ROLE_ASSISTANT,
            content = content,
            timestamp = timestamp,
            isStreaming = isStreaming,
            isError = isError,
            toolCalls = toolCalls
        )
    }
}


