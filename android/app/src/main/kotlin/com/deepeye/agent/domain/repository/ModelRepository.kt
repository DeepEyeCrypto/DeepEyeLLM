package com.deepeye.agent.domain.repository

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.deepeye.agent.core.model.ModelBackend
import com.deepeye.agent.core.model.ModelCapability
import com.deepeye.agent.core.model.ModelSpec
import com.deepeye.agent.core.model.Quantization
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching dynamic HuggingFace GGUF model catalogs remotely,
 * extracting exact repo sibling GGUF filenames to prevent 404 download errors,
 * filtering models according to device RAM hardware capabilities, and caching locally.
 */
@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeepEye-ModelRepository"
        private const val HF_API_URL = "https://huggingface.co/api/models?search=gguf&sort=downloads&direction=-1&limit=50&expand[]=siblings"
        private const val CACHE_FILE_NAME = "model_catalog_cache.json"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getDeviceTotalRamGb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val ramGb = (mi.totalMem / (1024 * 1024 * 1024)).toInt()
        return if (ramGb > 0) ramGb else 8
    }

    suspend fun fetchModelCatalog(): List<ModelSpec> = withContext(Dispatchers.IO) {
        val totalRamGb = getDeviceTotalRamGb()
        Log.d(TAG, "Device total RAM: ${totalRamGb}GB. Fetching complete model catalog.")

        // 1. Fetch live HuggingFace model search API with exact siblings tree
        val hfModels = runCatching { fetchHuggingFaceCatalog() }.getOrNull()
        if (!hfModels.isNullOrEmpty()) {
            cacheCatalogLocally(hfModels)
            return@withContext hfModels
        }

        // 2. Fallback to cached catalog
        val cachedModels = loadCachedCatalog()
        if (cachedModels.isNotEmpty()) {
            return@withContext cachedModels
        }

        // 3. Fallback to default catalog
        ModelSpec.CATALOG
    }

    private fun fetchHuggingFaceCatalog(): List<ModelSpec> {
        val request = Request.Builder()
            .url(HF_API_URL)
            .header("User-Agent", "DeepEyeLLM-Android/1.0")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HuggingFace API HTTP ${response.code}")

        val jsonStr = response.body?.string() ?: return emptyList()
        return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<ModelSpec>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id") // e.g. "Qwen/Qwen2.5-3B-Instruct-GGUF"
            val parts = id.split("/")
            val repoName = if (parts.size > 1) parts[1] else id

            val paramSize = parseParamSize(repoName)
            val ramNeededBytes = estimateRamForParamSize(paramSize)

            // Extract exact case-sensitive GGUF filename from siblings array
            var realGgufFilename: String? = null
            if (obj.has("siblings") && !obj.isNull("siblings")) {
                val siblings = obj.getJSONArray("siblings")
                for (j in 0 until siblings.length()) {
                    val sib = siblings.getJSONObject(j)
                    val rfilename = sib.optString("rfilename", "")
                    if (rfilename.endsWith(".gguf", ignoreCase = true)) {
                        // Prefer Q4_K_M or Q8_0 or first available GGUF
                        if (realGgufFilename == null || rfilename.contains("Q4_K_M", ignoreCase = true)) {
                            realGgufFilename = rfilename
                        }
                    }
                }
            }

            val finalGgufFile = realGgufFilename ?: "${repoName.lowercase()}.gguf"
            // Security: use File.name to strip ALL path components, preventing traversal via nested patterns like ....//
            val sanitizedGgufFile = java.io.File(finalGgufFile).name
            val downloadUrl = "https://huggingface.co/$id/resolve/main/$sanitizedGgufFile"

            list.add(
                ModelSpec(
                    id = id.lowercase().replace("/", "-"),
                    name = repoName.replace("-GGUF", "").replace("-", " "),
                    family = parseFamily(repoName),
                    parameterCount = paramSize,
                    backend = ModelBackend.GGUF_LLAMA_CPP,
                    quantization = Quantization.Q4_K_M,
                    sizeBytes = (ramNeededBytes * 0.6).toLong(),
                    requiredRamBytes = ramNeededBytes,
                    capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE),
                    fileName = sanitizedGgufFile,
                    downloadUrl = downloadUrl
                )
            )
        }
        list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse HuggingFace catalog JSON: ${e.message}")
            emptyList()
        }
    }

    private fun parseParamSize(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("0.5B") || upper.contains("500M") -> "0.5B"
            upper.contains("1B") || upper.contains("1.5B") || upper.contains("1.7B") || upper.contains("1.8B") -> "1.5B"
            upper.contains("2B") || upper.contains("2.7B") -> "2B"
            upper.contains("3B") || upper.contains("3.2B") || upper.contains("3.8B") -> "3B"
            upper.contains("7B") || upper.contains("8B") -> "7B"
            upper.contains("14B") -> "14B"
            else -> "3B"
        }
    }

    private fun estimateRamForParamSize(paramSize: String): Long {
        return when (paramSize) {
            "0.5B" -> 1_200_000_000L
            "1.5B" -> 2_000_000_000L
            "2B"   -> 2_800_000_000L
            "3B"   -> 3_800_000_000L
            "7B"   -> 6_500_000_000L
            "14B"  -> 12_000_000_000L
            else   -> 3_800_000_000L
        }
    }

    private fun parseFamily(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("QWEN") -> "Qwen"
            upper.contains("GEMMA") -> "Gemma"
            upper.contains("LLAMA") -> "Llama"
            upper.contains("PHI") -> "Phi"
            upper.contains("HERMES") -> "Hermes"
            upper.contains("MISTRAL") -> "Mistral"
            else -> "GGUF"
        }
    }

    private fun cacheCatalogLocally(models: List<ModelSpec>) {
        runCatching {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            val array = JSONArray()
            models.forEach { spec ->
                val obj = org.json.JSONObject().apply {
                    put("id", spec.id)
                    put("name", spec.name)
                    put("family", spec.family)
                    put("parameterCount", spec.parameterCount)
                    put("backend", spec.backend.name)
                    put("quantization", spec.quantization.name)
                    put("sizeBytes", spec.sizeBytes)
                    put("requiredRamBytes", spec.requiredRamBytes)
                    put("fileName", spec.fileName)
                    put("downloadUrl", spec.downloadUrl)
                }
                array.put(obj)
            }
            cacheFile.writeText(array.toString())
        }
    }

    private fun loadCachedCatalog(): List<ModelSpec> {
        return runCatching {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (!cacheFile.exists()) return emptyList()
            // Expire cache after 24 hours
            val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
            if (ageMs > 24 * 60 * 60 * 1000L) {
                Log.d(TAG, "Cache expired (age: ${ageMs / 3600000}h), will re-fetch")
                return emptyList()
            }
            val jsonStr = cacheFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ModelSpec>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ModelSpec(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        family = obj.optString("family", "GGUF"),
                        parameterCount = obj.optString("parameterCount", "3B"),
                        backend = runCatching { ModelBackend.valueOf(obj.optString("backend", "GGUF_LLAMA_CPP")) }.getOrDefault(ModelBackend.GGUF_LLAMA_CPP),
                        quantization = runCatching { Quantization.valueOf(obj.optString("quantization", "Q4_K_M")) }.getOrDefault(Quantization.Q4_K_M),
                        sizeBytes = obj.optLong("sizeBytes", 2_000_000_000L),
                        requiredRamBytes = obj.optLong("requiredRamBytes", 3_000_000_000L),
                        capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE),
                        fileName = obj.optString("fileName", "${obj.getString("id")}.gguf"),
                        downloadUrl = if (obj.has("downloadUrl") && !obj.isNull("downloadUrl")) obj.getString("downloadUrl") else null
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun getDefaultCatalog(): List<ModelSpec> {
        val totalRamGb = getDeviceTotalRamGb()
        val maxAllowedRamBytes = ((totalRamGb - 1.5).coerceAtLeast(2.0)) * 1024 * 1024 * 1024L
        return ModelSpec.CATALOG.filter { it.requiredRamBytes <= maxAllowedRamBytes }
    }
}
