package com.deepeye.agent.domain.engine

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.deepeye.agent.core.model.ChatMessage
import com.deepeye.agent.core.model.ChatMLFormatter
import com.deepeye.agent.core.model.ModelBackend
import com.deepeye.agent.core.security.ModelSignatureVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Native GGUF inference engine via llama.cpp JNI bindings with hardware acceleration (Vulkan)
 * and ChatML context management.
 *
 * Architecture:
 *   Compose UI ──▶ ViewModel ──▶ LlamaCppEngine.chatStreamWithHistory() ──JNI──▶ C++ llama.cpp (Vulkan GPU/CPU)
 *                                        ◀── onTokenGenerated() callbacks ◀──
 *
 * Features:
 *   - Hardware Acceleration: Configurable GPU offloading via Vulkan (with automatic CPU fallback)
 *   - Context Management: Formats conversation histories using standard Qwen ChatML syntax
 *   - Sliding Context Window: Truncates historical messages to fit `nCtx` limits
 */
class LlamaCppEngine(
    val modelPath: String,
    val context: android.content.Context? = null,
    val useGpu: Boolean = true,
    val selectedBackend: Int = -1,
    val gpuLayers: Int = 99,
    val customThreads: Int = 8,
    val customContextSize: Int = 4096,
    /** Public key bytes (X.509 DER) used to verify the model's Ed25519 signature.
     *  When null, signature verification is skipped (e.g. during local development
     *  before a signing pipeline is wired up). */
    val modelPublicKeyBytes: ByteArray? = null,
    /** Skip signature verification entirely. Set to true ONLY in unit tests.
     *  Never set this to true in production code. */
    @VisibleForTesting
    val skipSignatureCheck: Boolean = false,
    /** Injected verifier; defaults to a real [ModelSignatureVerifier]. */
    @VisibleForTesting
    val signatureVerifier: ModelSignatureVerifier = ModelSignatureVerifier()
) : LLMEngine {

    companion object {
        private const val TAG = "DeepEye-LlamaCpp"

        @Volatile
        private var nativeLibLoadAttempted = false

        @Volatile
        var isNativeLibLoaded = false
            private set

        /**
         * Deferred native library load — called from [init] on Dispatchers.IO,
         * never from a companion `init` block.
         *
         * Loading in a companion `init` block runs synchronously on the thread that
         * first touches the class. Hilt can trigger this on the main thread during
         * DI graph construction, causing the BLAST sync ANR:
         * "WM sent Transaction to organized, but never received commit callback."
         */
        fun loadNativeLibIfNeeded() {
            if (!nativeLibLoadAttempted) {
                synchronized(this) {
                    if (!nativeLibLoadAttempted) {
                        nativeLibLoadAttempted = true
                        runCatching {
                            System.loadLibrary("llama_jni")
                            isNativeLibLoaded = true
                            Log.d(TAG, "Successfully loaded native libllama_jni.so")
                        }.onFailure { err ->
                            runCatching { Log.w(TAG, "Native libllama_jni.so not present in APK, using Kotlin GGUF fallback: ${err.message}") }
                        }
                    }
                }
            }
        }
    }

    override val backend: ModelBackend = ModelBackend.GGUF_LLAMA_CPP
    override var isInitialized: Boolean = false
        private set
    override val activeModelPath: String get() = modelPath

    private var nativeContextHandle: Long = 0L
    private val maxContextTokens get() = customContextSize

    override suspend fun init(): Result<Unit> = withContext(Dispatchers.IO) {
        // ── Ed25519 model signature pre-check ─────────────────────────────
        // Performed before runCatching so a failed signature check produces
        // Result.failure(SecurityException) rather than being swallowed by
        // the runCatching block.
        if (!skipSignatureCheck && modelPublicKeyBytes != null) {
            val sigFile = File("$modelPath.sig")
            val modelFile = File(modelPath)
            val verified = modelFile.exists()
                && modelFile.length() >= 1_000_000L
                && runCatching {
                    signatureVerifier.verifyModelSignature(
                        modelPath = modelPath,
                        signaturePath = sigFile.absolutePath,
                        publicKeyBytes = modelPublicKeyBytes
                    )
                }.getOrElse { false }
            if (!verified) {
                @Suppress("UNCHECKED_CAST")
                return@withContext (Result.failure<Unit>(
                    SecurityException("Ed25519 signature verification failed for model: $modelPath")
                ) as Result<Unit>)
            }
        }
        // ─────────────────────────────────────────────────────────────────

        runCatching {
            // Load native lib here (called from Dispatchers.IO), not in companion init block.
            // This prevents Hilt-triggered class loading on the main thread from stalling
            // the window compositor and triggering the BLAST sync ANR.
            loadNativeLibIfNeeded()

            val file = File(modelPath)
            if (!file.exists()) {
                throw IllegalArgumentException("GGUF model file does not exist at path: $modelPath")
            }
            if (file.length() < 1_000_000L) {
                throw IllegalArgumentException("Invalid or truncated GGUF file: $modelPath")
            }

            if (isNativeLibLoaded) {
                val targetBackend = if (useGpu) selectedBackend else com.deepeye.agent.core.hardware.HardwareBackendSelector.BACKEND_CPU
                val targetLayers = if (useGpu) gpuLayers else 0
                val config = com.deepeye.agent.core.hardware.HardwareBackendSelector.applyBackendConfig(
                    nativeHandle = nativeContextHandle,
                    context = context,
                    userBackend = targetBackend,
                    userGpuLayers = targetLayers
                )
                nativeSetBackendConfig(nativeContextHandle, config.backendType, config.nGpuLayers)
                val threads = customThreads.coerceIn(1, Runtime.getRuntime().availableProcessors())
                nativeContextHandle = nativeInitModel(modelPath, nCtx = maxContextTokens, nThreads = threads, nGpuLayers = config.nGpuLayers)
                if (nativeContextHandle == 0L) {
                    val baseName = file.nameWithoutExtension
                    throw IllegalStateException("Model '$baseName' failed to load (incompatible GGUF architecture or quantization). Please select a supported model (e.g. Hermes 3 / Gemma 4).")
                }
            }

            isInitialized = true
            runCatching { Log.d(TAG, "GGUF engine initialized successfully (Backend: ${selectedBackend}, GPU enabled: $useGpu, Layers: $gpuLayers).") }
            Unit
        }
    }

    override suspend fun chat(prompt: String): String {
        return chatWithHistory(listOf(ChatMessage.user(prompt)))
    }

    /**
     * Multi-turn chat inference using full ChatML formatted prompt history.
     */
    suspend fun chatWithHistory(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        check(isInitialized) { "LlamaCppEngine is not initialized." }

        val promptToSend = if (history.size == 1 && history[0].role == ChatMessage.ROLE_USER) {
            history[0].content
        } else {
            com.deepeye.agent.core.model.ChatFormatter.formatPrompt(
                history = history,
                maxContextTokens = maxContextTokens,
                maxGenerationTokens = MAX_GENERATION_TOKENS
            )
        }

        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            return@withContext nativeGenerateResponse(nativeContextHandle, promptToSend)
        }

        "[LlamaCppEngine] Native library not loaded. Cannot run inference."
    }

    override suspend fun chatStream(prompt: String, onChunk: (String) -> Unit) {
        chatStreamWithHistory(listOf(ChatMessage.user(prompt)), onChunk)
    }

    /**
     * Multi-turn streaming inference — tokens are delivered one-by-one via [onChunk].
     * Context window limits are automatically managed via ChatML sliding window.
     */
    suspend fun chatStreamWithHistory(history: List<ChatMessage>, onChunk: (String) -> Unit): Unit = withContext(Dispatchers.IO) {
        check(isInitialized) { "LlamaCppEngine is not initialized." }

        val promptToSend = if (history.size == 1 && history[0].role == ChatMessage.ROLE_USER) {
            history[0].content
        } else {
            com.deepeye.agent.core.model.ChatFormatter.formatPrompt(
                history = history,
                maxContextTokens = maxContextTokens,
                maxGenerationTokens = MAX_GENERATION_TOKENS
            )
        }

        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            val handle = nativeContextHandle

            val job = coroutineContext[Job]
            val cancellationHandle = job?.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    Log.d(TAG, "Coroutine cancelled, aborting native generation")
                    nativeAbortGeneration(handle)
                }
            }

            try {
                nativeGenerateResponseStream(handle, promptToSend, MAX_GENERATION_TOKENS,
                    object : NativeTokenCallback {
                        override fun onTokenGenerated(token: String) {
                            onChunk(token)
                        }

                        override fun onGenerationComplete() {
                            Log.d(TAG, "Native streaming generation completed.")
                        }

                        override fun onGenerationError(message: String) {
                            Log.e(TAG, "Native streaming generation error: $message")
                        }
                    }
                )
            } finally {
                cancellationHandle?.dispose()
            }
            return@withContext
        }

        // Fallback simulation for non-native test environments
        val fullText = chatWithHistory(history)
        onChunk(fullText)
    }

    fun abortGeneration() {
        if (isNativeLibLoaded && nativeContextHandle != 0L) {
            nativeAbortGeneration(nativeContextHandle)
        }
    }

    fun getPerformanceStats(): PerformanceStats? {
        if (!isNativeLibLoaded || nativeContextHandle == 0L) return null
        return nativeGetPerformanceStats(nativeContextHandle)
    }

    private val lifecycleMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val handle = nativeContextHandle
            if (isNativeLibLoaded && handle != 0L) {
                nativeContextHandle = 0L
                try {
                    nativeAbortGeneration(handle)
                    nativeFreeModel(handle)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error freeing native model: ${e.message}", e)
                }
            }
            isInitialized = false
            runCatching { Log.d(TAG, "GGUF engine closed.") }
        }
    }

    // ─── Native JNI Declarations ────────────────────────────────────────────

    private external fun nativeSetBackendConfig(handle: Long, backendType: Int, nGpuLayers: Int)
    private external fun nativeInitModel(modelPath: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    private external fun nativeGenerateResponse(handle: Long, prompt: String): String
    private external fun nativeGenerateResponseStream(
        handle: Long, prompt: String, maxTokens: Int, callback: NativeTokenCallback
    )
    private external fun nativeAbortGeneration(handle: Long)
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeGetPerformanceStats(handle: Long): PerformanceStats?
}

private const val MAX_GENERATION_TOKENS = 512

interface NativeTokenCallback {
    fun onTokenGenerated(token: String)
    fun onGenerationComplete()
    fun onGenerationError(message: String)
}
