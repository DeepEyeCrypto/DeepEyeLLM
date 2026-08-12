package com.deepeye.agent.core.model

import java.util.UUID

/**
 * Data class representing a single message in a multi-turn chat conversation.
 *
 * @property id Unique identifier for Compose key stability.
 * @property role Standard ChatML role: "system", "user", or "assistant".
 * @property content The text message content.
 * @property isStreaming Flag indicating if the message is currently receiving streaming tokens.
 * @property isError Flag indicating if generation failed for this message.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
) {
    val isAssistant: Boolean get() = role == ROLE_ASSISTANT
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun system(content: String) = ChatMessage(role = ROLE_SYSTEM, content = content)
        fun user(content: String) = ChatMessage(role = ROLE_USER, content = content)
        fun assistant(content: String, isStreaming: Boolean = false, isError: Boolean = false) =
            ChatMessage(role = ROLE_ASSISTANT, content = content, isStreaming = isStreaming, isError = isError)
    }
}
