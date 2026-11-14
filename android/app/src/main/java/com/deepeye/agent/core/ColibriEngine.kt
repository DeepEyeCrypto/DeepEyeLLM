package com.deepeye.agent.core

import android.content.Context
import android.util.Log

/**
 * Kotlin wrapper for the Colibri LLM inference engine.
 * Provides a simple interface for model loading and text generation via JNI.
 * JNI function naming convention: Java_com_deepeye_agent_domain_engine_ColibriEngine_<function_name>
 *
 * This class uses System.loadLibrary("colibri_jni") to load the native bridge
 * compiled from colibri_bridge.cpp in the Android NDK build.
 *
 * All native function declarations are at the top level of this file so that
 * KSP can process them as platform declarations. The actual JNIEXPORT functions
 * are defined in colibri_bridge.cpp and exported with the correct JNI naming.
 */
class ColibriEngine(context: android.content.Context) : AutoCloseable {

    // JNI opaque handle; 0 means not initialized
    private var handle: Long = 0

    // Model family from initialization (or "auto")
    private var modelFamily: String = "auto"

    // Thread mode: "performance" or "balanced"
    private var threadMode: String = "balanced"

    /** Maximum context tokens (capped at 4096 for mobile). */
    private var maxContextTokens: Int = 2048

    /** Current temperature for sampling. */
    private var temperature: Float = 0.7f

    /** Top-p for nucleus sampling. */
    private var topP: Float = 0.9f

    // ---------------------------------------------------------------------------
    // Platform-level native function declarations (KSP-recognized)
    // ---------------------------------------------------------------------------

    /** Initialize a new Colibri engine instance.
     *  (Ljava/lang/String;Ljava/lang/String;II)J
     */
    external fun colibriInit(modelDir: String, family: String, maxCtx: Int, threads: Int): Long

    /** Load or override model family for a previously-initialized handle.
     *  (JLjava/lang/String;)I
     */
    external fun colibriLoadModel(handle: Long, family: String): Int

    /** Synchronous generation: blocks until max_tokens are generated (or EOS),
     *  then returns the full completed text as a Java String.
     *  (JLjava/lang/String;FF)Ljava/lang/String;
     */
    external fun colibriGenerateSync(handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float): String

    /** Release all native resources associated with the given handle.  (J)V */
    external fun colibriFree(handle: Long)

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /** Convenience reference to the colibriInit function. */
    companion object {
        // No declarations here — they are at the outer level for KSP processing.
    }

    /** Initialize the Colibri engine.
     *
     * @param modelDir   Path to directory containing the Colibri model files
     * @param family     Model family identifier ("qwen38", "qwen36", "glm53", "olmoe") or "auto"
     * @param maxCtx     Maximum context tokens (capped at 4096 for mobile)
     * @param threads    Desired thread count (capped at 4 for mobile)
     * @return true if initialization succeeded, false otherwise
     */
    fun init(modelDir: String, family: String = "auto", maxCtx: Int = 2048, threads: Int = 4): Boolean {
        handle = colibriInit(modelDir, family, maxCtx, threads)
        return handle != 0L
    }

    /** Load or override model family.
     *
     * @param family Optional model family override (empty = use family from init)
     * @return COLIBRI_STATUS_OK on success, or error code
     */
    fun loadModel(family: String = ""): Int {
        val fam = if (family.isNotEmpty()) family else modelFamily
        return colibriLoadModel(handle, fam)
    }

    /** Generate text synchronously from the given prompt.
     *
     * @param prompt     Input prompt string
     * @param maxTokens  Maximum tokens to generate (default 512)
     * @param temperature Sampling temperature (default 0.7f)
     * @param topP       Top-p for nucleus sampling (default 0.9f)
     * @return Generated text as String, or empty string on failure
     */
    fun generateSync(prompt: String, maxTokens: Int = 512, temperature: Float = 0.7f, topP: Float = 0.9f): String =
        colibriGenerateSync(handle, prompt, maxTokens, temperature, topP)

    /**
     * Release all native resources.
     * Should be called when the engine is no longer needed (e.g., on app shutdown).
     */
    override fun close() {
        if (handle != 0L) {
            colibriFree(handle)
            handle = 0L
        }
    }

    /** Convenience: alias for close(). */
    fun release() {
        close()
    }
}