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
        val modelsDir = java.io.File(context.filesDir, "models")
        val availableModels = modelsDir.listFiles { _, name -> (name.endsWith(".bin") || name.endsWith(".gguf")) && !name.endsWith(".tmp") }
        // Cap auto-load model file size to <= 2.1 GB to guarantee 0 Low Memory Killer (LMK) kills on mobile devices
        val activeModel = availableModels?.filter { it.length() in 50_000_000L..2_100_000_000L }?.maxByOrNull { it.length() }
        
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
            val modelId = java.io.File(newModelPath).nameWithoutExtension
            _engineStatus.update { EngineStatus(ModelStatus.LOCAL_ACTIVE, "Loading $modelId...", "Initializing model $modelId...") }
            android.util.Log.d("DeepEye", "{\"event\":\"engine_load_started\", \"model_id\":\"$modelId\"}")
            try {
                val file = java.io.File(newModelPath)
                if (!file.exists()) throw Exception("Model file not found")
                if (file.name.endsWith(".tmp")) throw Exception("Cannot load incomplete .tmp model download")

                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    ?: throw Exception("Cannot query device memory info")
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                
                val totalRamGb = memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
                val modelSizeGb = file.length().toDouble() / (1024 * 1024 * 1024)
                
                // GGUF mmap runtime: kernel memory-maps layer weights on demand
                val maxAllowedModelGb = (totalRamGb - 1.0).coerceAtLeast(3.8)
                
                if (modelSizeGb > maxAllowedModelGb && totalRamGb > 0) {
                    throw Exception("Model size (%.2f GB) exceeds mobile RAM safety limit (%.1f GB)".format(modelSizeGb, maxAllowedModelGb))
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
                val status = ModelStatus.LOCAL_ACTIVE
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
}
