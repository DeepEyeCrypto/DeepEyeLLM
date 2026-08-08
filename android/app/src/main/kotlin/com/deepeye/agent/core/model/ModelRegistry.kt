package com.deepeye.agent.core.model

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Central registry for all known models.
 * Tracks which models are on-device and provides intelligent selection.
 */
class ModelRegistry(private val modelsDir: File) {

    private val specs = mutableListOf<ModelSpec>()

    init {
        // Seed with the built-in catalog
        specs.addAll(ModelSpec.CATALOG)
        // Scan for downloaded models
        rescan()
    }

    /**
     * Scans the models directory and marks downloaded models.
     */
    fun rescan() {
        val onDisk = modelsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val updated = specs.map { spec ->
            spec.copy(isDownloaded = spec.fileName in onDisk)
        }
        specs.clear()
        specs.addAll(updated)
        Log.d("DeepEye-ModelRegistry", "Rescan complete. ${getAvailable().size}/${specs.size} models on device.")
    }

    /**
     * All models in the catalog.
     */
    fun getAll(): List<ModelSpec> = specs.toList()

    /**
     * Models that are currently on device and ready to load.
     */
    fun getAvailable(): List<ModelSpec> = specs.filter { it.isDownloaded }

    /**
     * Filter models by a specific capability.
     */
    fun getByCapability(capability: ModelCapability): List<ModelSpec> =
        specs.filter { capability in it.capabilities }

    /**
     * Filter models by family name (e.g., "Gemma", "Qwen").
     */
    fun getByFamily(family: String): List<ModelSpec> =
        specs.filter { it.family.equals(family, ignoreCase = true) }

    /**
     * Filter models by backend runtime.
     */
    fun getByBackend(backend: ModelBackend): List<ModelSpec> =
        specs.filter { it.backend == backend }

    /**
     * Picks the best model that:
     * 1. Is downloaded on device.
     * 2. Fits within available RAM.
     * 3. Has the required capability.
     * 4. Is the largest (most capable) that fits.
     */
    fun getBestFit(availableRamBytes: Long, capability: ModelCapability): ModelSpec? {
        return getAvailable()
            .filter { capability in it.capabilities }
            .filter { it.requiredRamBytes <= availableRamBytes }
            .maxByOrNull { it.requiredRamBytes } // biggest that fits = most capable
    }

    /**
     * Lookup a specific model by ID.
     */
    fun getById(id: String): ModelSpec? = specs.find { it.id == id }

    /**
     * Register a custom / user-added model spec.
     */
    fun register(spec: ModelSpec) {
        specs.removeAll { it.id == spec.id }
        specs.add(spec)
        Log.d("DeepEye-ModelRegistry", "Registered model: ${spec.id} (${spec.name})")
    }

    companion object {
        fun create(context: Context): ModelRegistry {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            return ModelRegistry(modelsDir)
        }
    }
}
