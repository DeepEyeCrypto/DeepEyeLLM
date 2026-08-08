package com.deepeye.agent.domain.engine

import com.deepeye.agent.core.model.ModelBackend

/**
 * Universal interface for local LLM inference engines (LiteRT, llama.cpp GGUF, ONNX).
 */
interface LLMEngine {
    val backend: ModelBackend
    val isInitialized: Boolean
    val activeModelPath: String?

    suspend fun init(): Result<Unit>
    suspend fun chat(prompt: String): String
    suspend fun chatStream(prompt: String, onChunk: (String) -> Unit)
    suspend fun close()
}
