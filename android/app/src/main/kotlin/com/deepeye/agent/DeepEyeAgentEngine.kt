package com.deepeye.agent

import android.util.Log
import com.deepeye.agent.core.model.ModelSpec
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.flow.collect

import com.deepeye.agent.core.model.ModelBackend
import com.deepeye.agent.domain.engine.LLMEngine

class DeepEyeAgentEngine(
    val modelPath: String
) : LLMEngine {
    companion object {
        /**
         * Deferred native library load — called explicitly from a background thread
         * (via [init]) rather than in a companion `init` block.
         *
         * Loading native libs in companion `init` runs synchronously on the thread
         * that first touches the class. If Hilt resolves the singleton on the main
         * thread this stalls the main thread and triggers the BLAST sync ANR warning:
         * "WM sent Transaction to organized, but never received commit callback."
         */
        @Volatile
        private var nativeLibLoaded = false

        fun loadNativeLibIfNeeded() {
            if (!nativeLibLoaded) {
                synchronized(this) {
                    if (!nativeLibLoaded) {
                        runCatching { System.loadLibrary("litertlm_jni") }
                        nativeLibLoaded = true
                    }
                }
            }
        }
    }
    
    override val backend: ModelBackend = ModelBackend.LITERT
    override var isInitialized: Boolean = false
        private set
    override val activeModelPath: String get() = modelPath

    private lateinit var engine: Engine
    var activeModelId: String? = null
        private set

    override suspend fun init(): Result<Unit> = runCatching {
        // Load native lib here (called from Dispatchers.IO), not in companion init
        loadNativeLibIfNeeded()

        runCatching {
            Log.d("DeepEye-Engine", "Attempting LiteRT GPU initialization for: $modelPath")
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU()
            )
            engine = Engine(config)
            engine.initialize()
            Log.d("DeepEye-Engine", "GPU initialization successful.")
        }.getOrElse { gpuErr ->
            Log.w("DeepEye-Engine", "GPU init failed (${gpuErr.message}). Falling back to CPU backend...")
            val cpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU()
            )
            engine = Engine(cpuConfig)
            engine.initialize()
            Log.d("DeepEye-Engine", "CPU fallback initialization successful.")
        }
        isInitialized = true
    }

    override suspend fun chat(prompt: String): String {
        if (!isInitialized || !::engine.isInitialized) {
            return "Error: Engine is not initialized."
        }
        engine.createConversation().use { conversation ->
            val response = conversation.sendMessage(prompt)
            return extractText(response)
        }
    }

    override suspend fun chatStream(prompt: String, onChunk: (String) -> Unit) {
        if (!isInitialized || !::engine.isInitialized) {
            onChunk("Error: Engine is not initialized.")
            return
        }
        engine.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt).collect { chunk ->
                onChunk(extractText(chunk))
            }
        }
    }

    override suspend fun close() {
        runCatching {
            isInitialized = false
        }
    }

    suspend fun analyzeImage(imagePath: String, prompt: String): String {
        engine.createConversation().use { conversation ->
            val message = Message.of(
                Content.ImageFile(imagePath),
                Content.Text(prompt)
            )
            val response = conversation.sendMessage(message)
            return extractText(response)
        }
    }

    suspend fun analyzeAudio(audioPath: String, prompt: String): String {
        engine.createConversation().use { conversation ->
            val message = Message.of(
                Content.AudioFile(audioPath),
                Content.Text(prompt)
            )
            val response = conversation.sendMessage(message)
            return extractText(response)
        }
    }

    private fun extractText(response: Any): String {
        if (response is Message) {
            return response.contents.contents.filterIsInstance<Content.Text>().joinToString("\n") { it.text }
        }
        if (response is Map<*, *>) {
            val content = response["content"]
            val first = (content as? List<*>)?.firstOrNull()
            val firstMap = first as? Map<*, *>
            return firstMap?.get("text")?.toString().orEmpty()
        }
        return ""
    }

    /**
     * Hot-swap to a different model at runtime.
     * Re-initializes the engine with the new model path.
     */
    suspend fun switchModel(spec: ModelSpec, modelsDir: java.io.File) {
        val newPath = java.io.File(modelsDir, spec.fileName).absolutePath
        Log.d("DeepEye-Engine", "Switching model: ${activeModelId ?: "none"} -> ${spec.id} (${spec.name})")
        
        runCatching {
            val config = EngineConfig(
                modelPath = newPath,
                backend = Backend.GPU()
            )
            engine = Engine(config)
            engine.initialize()
        }.getOrElse { gpuErr ->
            Log.w("DeepEye-Engine", "GPU switch failed (${gpuErr.message}). Falling back to CPU backend...")
            val cpuConfig = EngineConfig(
                modelPath = newPath,
                backend = Backend.CPU()
            )
            engine = Engine(cpuConfig)
            engine.initialize()
        }
        activeModelId = spec.id
        
        Log.d("DeepEye-Engine", "Model switched successfully to ${spec.id}")
    }
}
