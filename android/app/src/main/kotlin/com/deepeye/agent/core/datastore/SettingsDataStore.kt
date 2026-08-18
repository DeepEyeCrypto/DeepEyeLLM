package com.deepeye.agent.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepeye_settings")

/**
 * Advanced Engine Options DataStore manager for persistent hardware acceleration and context configuration.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val KEY_USE_GPU = booleanPreferencesKey("use_gpu")
        val KEY_SELECTED_BACKEND = intPreferencesKey("selected_backend")
        val KEY_GPU_LAYERS = intPreferencesKey("gpu_layers")
        val KEY_CPU_THREADS = intPreferencesKey("cpu_threads")
        val KEY_CONTEXT_SIZE = intPreferencesKey("context_size")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_TOP_P = floatPreferencesKey("top_p")
        val KEY_KV_CACHE_QUANT = androidx.datastore.preferences.core.stringPreferencesKey("kv_cache_quant")
    }

    val engineSettingsFlow: Flow<EngineSettings> = context.dataStore.data.map { prefs ->
        val defaultThreads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
        EngineSettings(
            useGpu = prefs[KEY_USE_GPU] ?: true,
            selectedBackend = prefs[KEY_SELECTED_BACKEND] ?: -1,
            gpuLayers = prefs[KEY_GPU_LAYERS] ?: 99,
            cpuThreads = prefs[KEY_CPU_THREADS] ?: defaultThreads,
            contextSize = prefs[KEY_CONTEXT_SIZE] ?: 4096,
            kvCacheQuant = prefs[KEY_KV_CACHE_QUANT] ?: "FP16",
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f,
            topP = prefs[KEY_TOP_P] ?: 0.9f
        )
    }

    suspend fun updateUseGpu(useGpu: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_USE_GPU] = useGpu }
    }

    suspend fun updateSelectedBackend(selectedBackend: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_SELECTED_BACKEND] = selectedBackend }
    }

    suspend fun updateGpuLayers(gpuLayers: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_GPU_LAYERS] = gpuLayers }
    }

    suspend fun updateCpuThreads(cpuThreads: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CPU_THREADS] = cpuThreads }
    }

    suspend fun updateContextSize(contextSize: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CONTEXT_SIZE] = contextSize }
    }

    suspend fun updateKvCacheQuant(kvCacheQuant: String) {
        context.dataStore.edit { prefs -> prefs[KEY_KV_CACHE_QUANT] = kvCacheQuant }
    }

    suspend fun updateTemperature(temperature: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_TEMPERATURE] = temperature }
    }

    suspend fun updateTopP(topP: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_TOP_P] = topP }
    }
}

data class EngineSettings(
    val useGpu: Boolean = true,
    val selectedBackend: Int = -1, // -1 = Auto, 0 = CPU, 1 = Vulkan, 2 = OpenCL, 3 = Hexagon QNN, 4 = KleidiAI
    val gpuLayers: Int = 99,
    val cpuThreads: Int = 8,
    val contextSize: Int = 4096,
    val kvCacheQuant: String = "FP16", // "FP16", "Q8_0", "Q4_0"
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f
)
