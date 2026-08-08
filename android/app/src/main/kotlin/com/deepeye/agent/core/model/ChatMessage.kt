package com.deepeye.agent.core.model

/**
 * Data class representing a single message in a multi-turn chat conversation.
 *
 * @property role Standard ChatML role: "system", "user", or "assistant".
 * @property content The text message content.
 */
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)
        fun user(content: String) = ChatMessage(ROLE_USER, content)
        fun assistant(content: String) = ChatMessage(ROLE_ASSISTANT, content)
    }
}
