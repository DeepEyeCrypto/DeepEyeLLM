package com.deepeye.agent.domain.engine

import android.content.Context
import android.util.Log
import com.deepeye.agent.core.model.ChatMessage
import com.deepeye.agent.core.model.ChatMLFormatter
import com.deepeye.agent.core.model.ModelBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Native inference engine powered by the Colibri MoE/Transformer runtime.
 * Supports specialized edge architectures (Qwen 3.6/3.8, GLM/GLM 5.3, Kimi K3, OLMoE, Inkling)
 * with direct JNI bindings, memory mapping, and streaming token generation.
 */
class ColibriEngine(
    val modelDir: String,
    val context: Context? = null,
    val contextTokens: Int = 2048,
    val nThreads: Int = 4,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f
) : LLMEngine {

    companion object {
        private const val TAG = "DeepEye-Colibri"
        private const val MAX_GENERATION_TOKENS = 512

        @Volatile
        private var nativeLibLoadAttempted = false

        @Volatile
        var isNativeLibLoaded = false
            private set

        fun loadNativeLibIfNeeded() {
            if (!nativeLibLoadAttempted) {
                synchronized(this) {
                    if (!nativeLibLoadAttempted) {
                        nativeLibLoadAttempted = true
                        runCatching {
                            System.loadLibrary("colibri_jni")
                            isNativeLibLoaded = true
                            Log.d(TAG, "Successfully loaded native libcolibri_jni.so")
                        }.onFailure { err ->
                            runCatching {
                                Log.w(TAG, "Native libcolibri_jni.so not present: ${err.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    override val backend: ModelBackend = ModelBackend.COLIBRI
    override var isInitialized: Boolean = false
        private set
    override val activeModelPath: String get() = modelDir

    var nativeContextHandle: Long = 0L
        private set

    val isModelLoaded: Boolean
        get() = isInitialized && nativeContextHandle != 0L

    private val lifecycleMutex = Mutex()

    override suspend fun init(): Result<Unit> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            loadNativeLibIfNeeded()

            val dir = File(modelDir)
            if (!dir.exists()) {
                Log.w(TAG, "Model path does not exist on disk: $modelDir")
            }

            if (isNativeLibLoaded) {
                try {
                    Log.i(TAG, "Initializing Colibri native model: $modelDir (ctx=$contextTokens, th=$nThreads)")
                    nativeContextHandle = nativeInitModel(modelDir, contextTokens, nThreads)
                    if (nativeContextHandle == 0L) {
                        Log.e(TAG, "nativeInitModel returned 0 pointer.")
                        return@withContext Result.failure(
                            IllegalStateException("Failed to initialize Colibri engine at $modelDir")
                        )
                    }
                    Log.i(TAG, "Colibri native model initialized (handle: $nativeContextHandle)")
                } catch (e: Throwable) {
                    Log.e(TAG, "Exception during nativeInitModel: ${e.message}", e)
                    return@withContext Result.failure(e)
                }
            } else {
                Log.w(TAG, "libcolibri_jni.so not loaded. Running in simulation mode.")
            }

            isInitialized = true
            Result.success(Unit)
        }
    }

    override suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext "Error: ColibriEngine is not initialized."
        }

        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            try {
                return@withContext nativeGenerateResponse(
                    nativeContextHandle,
                    prompt,
                    MAX_GENERATION_TOKENS,
                    temperature,
                    topP
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Native generation error: ${e.message}", e)
                return@withContext "Error during native generation: ${e.message}"
            }
        }

        "Colibri Engine simulation response for: $prompt"
    }

    override suspend fun chatStream(prompt: String, onChunk: (String) -> Unit): Unit = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            onChunk("Error: ColibriEngine is not initialized.")
            return@withContext
        }

        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            val handle = nativeContextHandle
            val job = coroutineContext[Job]
            val cancellationHandle = job?.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    Log.d(TAG, "Coroutine cancelled, aborting Colibri generation")
                    nativeAbortGeneration(handle)
                }
            }

            try {
                nativeGenerateResponseStream(
                    handle,
                    prompt,
                    MAX_GENERATION_TOKENS,
                    temperature,
                    topP,
                    object : NativeTokenCallback {
                        override fun onTokenGenerated(token: String) {
                            onChunk(token)
                        }

                        override fun onGenerationComplete() {
                            Log.d(TAG, "Colibri native streaming generation completed.")
                        }

                        override fun onGenerationError(message: String) {
                            Log.e(TAG, "Colibri native streaming generation error: $message")
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Exception during streaming inference: ${e.message}", e)
                onChunk("\n[Inference Error: ${e.message}]")
            } finally {
                cancellationHandle?.dispose()
            }
            return@withContext
        }

        val simulated = chat(prompt)
        for (char in simulated) {
            onChunk(char.toString())
        }
    }

    suspend fun chatWithHistory(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val formattedPrompt = ChatMLFormatter.formatPromptWithHistory(
            history = history,
            maxContextTokens = contextTokens,
            maxGenerationTokens = MAX_GENERATION_TOKENS
        )
        chat(formattedPrompt)
    }

    suspend fun chatStreamWithHistory(
        history: List<ChatMessage>,
        onChunk: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val formattedPrompt = ChatMLFormatter.formatPromptWithHistory(
            history = history,
            maxContextTokens = contextTokens,
            maxGenerationTokens = MAX_GENERATION_TOKENS
        )
        chatStream(formattedPrompt, onChunk)
    }

    fun abortGeneration() {
        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            nativeAbortGeneration(nativeContextHandle)
        }
    }

    fun resetSession(): Boolean {
        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            return nativeResetSession(nativeContextHandle)
        }
        return false
    }

    fun getModelInfo(): String {
        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            return nativeGetModelInfo(nativeContextHandle)
        }
        return "{}"
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val handle = nativeContextHandle
            if (isNativeLibLoaded && handle != 0L) {
                nativeContextHandle = 0L
                try {
                    nativeAbortGeneration(handle)
                    nativeReleaseModel(handle)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error releasing Colibri native model: ${e.message}", e)
                }
            }
            isInitialized = false
            Log.d(TAG, "Colibri engine closed.")
        }
    }

    // ─── Native JNI Declarations ────────────────────────────────────────────

    private external fun nativeInitModel(modelDir: String, contextTokens: Int, nThreads: Int): Long
    private external fun nativeGenerateResponse(
        handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float
    ): String
    private external fun nativeGenerateResponseStream(
        handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float, callback: NativeTokenCallback
    )
    private external fun nativeAbortGeneration(handle: Long)
    private external fun nativeResetSession(handle: Long): Boolean
    private external fun nativeReleaseModel(handle: Long)
    private external fun nativeGetModelInfo(handle: Long): String
}
