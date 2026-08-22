package com.deepeye.agent.domain

import android.content.Context
import com.deepeye.agent.DeepEyeAgentEngine
import com.deepeye.agent.domain.engine.LLMEngine
import com.deepeye.agent.domain.engine.LlamaCppEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.deepeye.agent.core.datastore.SettingsDataStore

/**
 * Shared engine status — observed by multiple ViewModels.
 * Enforces strict LMK safe memory limits (max 2.1GB binary cap on <=7GB RAM mobile devices).
 */
data class EngineStatus(
    val modelStatus: ModelStatus = ModelStatus.ERROR,
    val activeModelName: String = "No Model Loaded",
    val statusMessage: String = ""
)

class EngineController(
    private var engine: LLMEngine,
    val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private var isEngineReady = false
    private val initMutex = kotlinx.coroutines.sync.Mutex()

    private val _engineStatus = MutableStateFlow(EngineStatus())
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    suspend fun initialize(): Pair<ModelStatus, String> = withContext(Dispatchers.IO) {
        if (isEngineReady && engine.isInitialized) {
            val modelName = engine.activeModelPath?.let { java.io.File(it).nameWithoutExtension } ?: "Active Model"
            val status = ModelStatus.LOCAL_ACTIVE
            val msg = "Engine active with $modelName."
            _engineStatus.update { EngineStatus(status, "GGUF: $modelName", msg) }
            return@withContext status to msg
        }

        val modelsDir = java.io.File(context.filesDir, "models")
        val availableModels = modelsDir.listFiles { _, name -> (name.endsWith(".bin") || name.endsWith(".gguf")) && !name.endsWith(".tmp") }
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        // Uncapped: utilize full 6GB RAM capacity for high-parameter models & mmap paging
        val safeMaxBytes = if (memoryInfo.totalMem > 0) (memoryInfo.totalMem * 0.95).toLong() else 6_000_000_000L

        // Prioritize high-speed mobile models (<= 2.2 GB) to achieve maximum tokens/sec throughput
        val activeModel = availableModels?.filter { 
            it.length() in 50_000_000L..safeMaxBytes &&
            !it.name.contains("dspark", ignoreCase = true) &&
            !it.name.contains("adapter", ignoreCase = true) &&
            it.length() <= 2_200_000_000L
        }?.maxByOrNull { it.length() }
            ?: availableModels?.filter { 
                it.length() in 50_000_000L..safeMaxBytes &&
                !it.name.contains("dspark", ignoreCase = true) &&
                !it.name.contains("adapter", ignoreCase = true)
            }?.minByOrNull { it.length() }
        
        if (activeModel != null && activeModel.exists()) {
            android.util.Log.d("DeepEye", "{\"event\":\"engine_auto_loading\", \"model\":\"${activeModel.name}\"}")
            return@withContext reinitializeWithModel(activeModel.absolutePath)
        }

        android.util.Log.d("DeepEye", "{\"event\":\"engine_no_model_binary\", \"model_id\":\"none\"}")
        val status = ModelStatus.LOCAL_ACTIVE
        val msg = "No model binary loaded. Download or import a model file (.bin or .gguf) in Settings → Manage Models."
        _engineStatus.update { EngineStatus(status, "No Model Loaded", msg) }
        return@withContext status to msg
    }

    suspend fun reinitializeWithModel(newModelPath: String): Pair<ModelStatus, String> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            val file = java.io.File(newModelPath)
            val modelId = file.nameWithoutExtension

            if (isEngineReady && engine.isInitialized && engine.activeModelPath == file.absolutePath) {
                val status = ModelStatus.LOCAL_ACTIVE
                val msg = "Model $modelId is already active."
                _engineStatus.update { EngineStatus(status, "GGUF: $modelId", msg) }
                return@withContext status to msg
            }

            _engineStatus.update { EngineStatus(ModelStatus.LOCAL_ACTIVE, "Loading $modelId...", "Initializing model $modelId...") }
            android.util.Log.d("DeepEye", "{\"event\":\"engine_load_started\", \"model_id\":\"$modelId\"}")
            try {
                val file = java.io.File(newModelPath)
                if (!file.exists()) throw Exception("Model file not found")
                if (file.name.endsWith(".tmp")) throw Exception("Cannot load incomplete .tmp model download")
                if (file.name.contains("dspark", ignoreCase = true) || file.name.contains("adapter", ignoreCase = true)) {
                    throw Exception("'$modelId' is a DSpark speculative draft adapter (not a standalone base model). Please load a full base model (Hermes 3, Gemma 4, etc.).")
                }

                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memoryInfo)
                
                val totalRamGb = if (memoryInfo.totalMem > 0) memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024) else 8.0
                val modelSizeGb = file.length().toDouble() / (1024 * 1024 * 1024)
                
                // GGUF mmap runtime: check for excessive model sizes on mobile
                if (file.name.endsWith(".gguf")) {
                    if (modelSizeGb > 12.0 && totalRamGb < 8.0) {
                        throw Exception("Model size (%.1f GB) exceeds mobile device memory capacity. Please select a model <= 4 GB.".format(modelSizeGb))
                    } else if (modelSizeGb > totalRamGb) {
                        android.util.Log.w("DeepEye", "Model size (%.2f GB) exceeds physical RAM (%.1f GB); relying on Linux mmap paging".format(modelSizeGb, totalRamGb))
                    }
                } else {
                    val maxAllowedModelGb = (totalRamGb - 1.0).coerceAtLeast(3.8)
                    if (modelSizeGb > maxAllowedModelGb && totalRamGb > 0) {
                        throw Exception("Model size (%.2f GB) exceeds mobile RAM safety limit (%.1f GB)".format(modelSizeGb, maxAllowedModelGb))
                    }
                }

                // Close existing engine and release address space before allocating next model
                engine.close()
                System.gc()

                val settings = settingsDataStore.engineSettingsFlow.first()

                // Instantiate appropriate LLMEngine
                engine = if (file.name.endsWith(".gguf")) {
                    LlamaCppEngine(
                        modelPath = newModelPath,
                        context = context,
                        useGpu = settings.useGpu,
                        selectedBackend = settings.selectedBackend,
                        gpuLayers = settings.gpuLayers,
                        customThreads = settings.cpuThreads,
                        customContextSize = settings.contextSize
                    )
                } else if (file.name.endsWith(".bin") || file.name.endsWith(".tflite")) {
                    DeepEyeAgentEngine(newModelPath)
                } else {
                    throw Exception("Unsupported model file format: ${file.name}")
                }

                engine.init().getOrThrow()
                isEngineReady = true

                val engineType = if (file.name.endsWith(".gguf")) "GGUF" else "LiteRT"
                android.util.Log.d("DeepEye", "{\"event\":\"engine_load_succeeded\", \"model_id\":\"$modelId\", \"type\":\"$engineType\"}")
                val status = ModelStatus.LOCAL_ACTIVE
                val msg = "$engineType Engine active with model $modelId."
                _engineStatus.update { EngineStatus(status, "$engineType: $modelId", msg) }
                status to msg
            } catch (e: Throwable) {
                isEngineReady = false
                android.util.Log.e("DeepEye", "{\"event\":\"engine_load_failed\", \"model_id\":\"$modelId\", \"error\":\"${e.message}\"}", e)
                val status = ModelStatus.ERROR
                val msg = "Model initialization failed: ${e.message}"
                _engineStatus.update { EngineStatus(status, "Load Failed", msg) }
                status to msg
            }
        }
    }

    suspend fun switchToNativeLocalMode() = withContext(Dispatchers.IO) {
        val modelsDir = java.io.File(context.filesDir, "models")
        val availableModels = modelsDir.listFiles { _, name -> (name.endsWith(".bin") || name.endsWith(".gguf")) && !name.endsWith(".tmp") }
        val activeModel = availableModels?.filter { it.length() in 50_000_000L..5_000_000_000L }?.maxByOrNull { it.length() }
        
        if (activeModel != null && activeModel.exists()) {
            reinitializeWithModel(activeModel.absolutePath)
        } else {
            isEngineReady = false
            val status = ModelStatus.LOCAL_ACTIVE
            _engineStatus.update { EngineStatus(status, "No Model Loaded", "Download or import a model file in Settings → Manage Models.") }
        }
    }

    suspend fun executeChat(prompt: String): Pair<ModelStatus, String> {
        if (!isEngineReady) {
            val status = ModelStatus.LOCAL_ACTIVE
            val msg = "Engine not initialized. Please load or download a model file in Settings."
            _engineStatus.update { EngineStatus(status, "Engine Not Ready", msg) }
            return status to msg
        }
        val response = engine.chat(prompt)
        val status = ModelStatus.LOCAL_ACTIVE
        val modelName = engine.activeModelPath?.let { java.io.File(it).nameWithoutExtension } ?: "Local Engine"
        _engineStatus.update { EngineStatus(status, modelName, "Generation completed.") }
        return status to response
    }

    suspend fun executeChatStream(prompt: String, onChunk: (String) -> Unit): ModelStatus {
        if (!isEngineReady) {
            val status = ModelStatus.LOCAL_ACTIVE
            val msg = "Engine not initialized. Please load or download a model file in Settings."
            _engineStatus.update { EngineStatus(status, "Engine Not Ready", msg) }
            onChunk(msg)
            return status
        }
        engine.chatStream(prompt, onChunk)
        val status = ModelStatus.LOCAL_ACTIVE
        val modelName = engine.activeModelPath?.let { java.io.File(it).nameWithoutExtension } ?: "Local Engine"
        _engineStatus.update { EngineStatus(status, modelName, "Generation completed.") }
        return status
    }

    fun getActiveEngineName(): String = engine.activeModelPath?.let { java.io.File(it).nameWithoutExtension } ?: "No Model Loaded"

    fun getEngineStatusMessage(): String = _engineStatus.value.statusMessage

    fun getPerformanceStats(): com.deepeye.agent.domain.engine.PerformanceStats? =
        (engine as? LlamaCppEngine)?.getPerformanceStats()

    /**
     * Analyze an image with an optional text prompt using the active vision-capable engine.
     * Falls back to text-only description if the engine doesn't support multimodal input.
     */
    suspend fun executeImageAnalysis(
        imagePath: String,
        prompt: String,
        onChunk: (String) -> Unit
    ): ModelStatus = withContext(Dispatchers.IO) {
        if (!isEngineReady) {
            onChunk("Engine not initialized. Please load or download a model in Settings.")
            return@withContext ModelStatus.ERROR
        }
        try {
            val deepEyeEngine = engine as? DeepEyeAgentEngine
            if (deepEyeEngine != null) {
                // Use native multimodal vision analysis
                val result = deepEyeEngine.analyzeImage(imagePath, prompt)
                onChunk(result)
            } else {
                // Fallback: describe request via text-only chat
                engine.chatStream(
                    "The user has shared an image and asks: $prompt\n\nPlease provide a helpful response.",
                    onChunk
                )
            }
            ModelStatus.LOCAL_ACTIVE
        } catch (e: Throwable) {
            onChunk("Image analysis failed: ${e.message}")
            ModelStatus.ERROR
        }
    }

    /**
     * Transcribe audio using the active audio-capable engine.
     * Falls back to text-only response if the engine doesn't support audio input.
     */
    suspend fun executeAudioTranscription(
        audioPath: String,
        prompt: String,
        onChunk: (String) -> Unit
    ): ModelStatus = withContext(Dispatchers.IO) {
        if (!isEngineReady) {
            onChunk("Engine not initialized. Please load or download a model in Settings.")
            return@withContext ModelStatus.ERROR
        }
        try {
            val deepEyeEngine = engine as? DeepEyeAgentEngine
            if (deepEyeEngine != null) {
                // Use native audio analysis
                val result = deepEyeEngine.analyzeAudio(audioPath, prompt)
                onChunk(result)
            } else {
                // Fallback: notify user that audio requires a vision/audio capable model
                onChunk("Audio transcription requires a multimodal model (Gemma 4). Current engine does not support audio input.")
            }
            ModelStatus.LOCAL_ACTIVE
        } catch (e: Throwable) {
            onChunk("Audio transcription failed: ${e.message}")
            ModelStatus.ERROR
        }
    }
}
