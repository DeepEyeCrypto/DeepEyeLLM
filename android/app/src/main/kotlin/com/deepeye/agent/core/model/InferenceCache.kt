package com.deepeye.agent.core.model

import android.util.Log
import java.security.MessageDigest

/**
 * Simple LRU cache for inference results.
 * Avoids re-running identical prompts on the same model — useful for
 * repeated tool calls, function routing, and embedding lookups.
 *
 * Thread-safe via synchronized LinkedHashMap.
 */
class InferenceCache(private val maxEntries: Int = 128) {

    private val cache = object : LinkedHashMap<String, CachedResult>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResult>?): Boolean {
            return size > maxEntries
        }
    }

    data class CachedResult(
        val response: String,
        val modelId: String,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    /**
     * Creates a cache key from model ID and prompt content.
     * Uses SHA-256 hash of the prompt to keep keys compact.
     */
    private fun makeKey(modelId: String, prompt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(prompt.toByteArray(Charsets.UTF_8))
        val hexHash = hash.joinToString("") { "%02x".format(it) }.take(16)
        return "$modelId:$hexHash"
    }

    /**
     * Retrieves a cached result if available.
     */
    @Synchronized
    fun get(modelId: String, prompt: String): CachedResult? {
        val key = makeKey(modelId, prompt)
        return cache[key]?.also {
            Log.d("DeepEye-Cache", "HIT: $key (age: ${System.currentTimeMillis() - it.timestampMs}ms)")
        }
    }

    /**
     * Stores an inference result in the cache.
     */
    @Synchronized
    fun put(modelId: String, prompt: String, response: String) {
        val key = makeKey(modelId, prompt)
        cache[key] = CachedResult(response = response, modelId = modelId)
        Log.d("DeepEye-Cache", "STORE: $key (cache size: ${cache.size}/$maxEntries)")
    }

    /**
     * Invalidate all entries for a specific model (useful after model swap).
     */
    @Synchronized
    fun invalidateModel(modelId: String) {
        val keysToRemove = cache.keys.filter { it.startsWith("$modelId:") }
        keysToRemove.forEach { cache.remove(it) }
        Log.d("DeepEye-Cache", "Invalidated ${keysToRemove.size} entries for model $modelId")
    }

    @Synchronized
    fun clear() {
        cache.clear()
        Log.d("DeepEye-Cache", "Cache cleared")
    }

    val size: Int @Synchronized get() = cache.size
    val hitRate: String get() = "${size}/$maxEntries entries"
}
