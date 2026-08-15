package com.deepeye.agent.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

enum class MemoryFitLevel {
    PERFECT_FIT,        // < 50% available RAM: safe for sustained background & UI
    MODERATE_LOAD,      // 50-75% available RAM: NPU/GPU offload recommended
    HAZARDOUS_OOM_RISK  // > 75% available RAM: high risk of Android OS process termination
}

data class ModelFitReport(
    val fitLevel: MemoryFitLevel,
    val estimatedWeightsMb: Long,
    val estimatedKvCacheMb: Long,
    val estimatedTotalRamMb: Long,
    val availableDeviceRamMb: Long,
    val totalDeviceRamMb: Long,
    val recommendedThreads: Int,
    val fitRecommendation: String
)

data class ThermalAdvice(
    val thermalStatus: String,
    val isThrottled: Boolean,
    val recommendedThreadCap: Int
)

/**
 * On-Device Hardware Fit and Thermal Safety Governor.
 * Pre-computes RAM headspace and dynamic thread limits before loading large GGUF/LiteRT models.
 */
object HardwareFitEvaluator {

    /**
     * Evaluates whether a model with a given size and context window safely fits in available device memory.
     */
    fun evaluateModelFit(
        context: Context,
        modelSizeBytes: Long,
        contextLength: Int = 4096,
        bitsPerWeight: Float = 4.5f
    ): ModelFitReport {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalDeviceRamMb = memInfo.totalMem / (1024 * 1024)
        val availableDeviceRamMb = memInfo.availMem / (1024 * 1024)

        val estimatedWeightsMb = (modelSizeBytes / (1024 * 1024)).coerceAtLeast(1)
        // 4k KV cache with FP16 keys/values ~= 200MB - 400MB depending on layer count
        val estimatedKvCacheMb = ((contextLength.toFloat() / 4096f) * 280f).toLong()
        val estimatedTotalRamMb = estimatedWeightsMb + estimatedKvCacheMb

        val usageRatio = estimatedTotalRamMb.toFloat() / availableDeviceRamMb.toFloat()

        val fitLevel = when {
            usageRatio <= 0.50f -> MemoryFitLevel.PERFECT_FIT
            usageRatio <= 0.75f -> MemoryFitLevel.MODERATE_LOAD
            else -> MemoryFitLevel.HAZARDOUS_OOM_RISK
        }

        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val recommendedThreads = when (fitLevel) {
            MemoryFitLevel.PERFECT_FIT -> (availableProcessors - 1).coerceIn(2, 8)
            MemoryFitLevel.MODERATE_LOAD -> (availableProcessors / 2).coerceIn(2, 6)
            MemoryFitLevel.HAZARDOUS_OOM_RISK -> 2
        }

        val recommendation = when (fitLevel) {
            MemoryFitLevel.PERFECT_FIT -> "Optimal Fit. Full NPU/GPU acceleration enabled."
            MemoryFitLevel.MODERATE_LOAD -> "Moderate Load. Recommend closing heavy background tasks."
            MemoryFitLevel.HAZARDOUS_OOM_RISK -> "OOM Warning: Model may exceed available RAM ($estimatedTotalRamMb MB > $availableDeviceRamMb MB avail)."
        }

        return ModelFitReport(
            fitLevel = fitLevel,
            estimatedWeightsMb = estimatedWeightsMb,
            estimatedKvCacheMb = estimatedKvCacheMb,
            estimatedTotalRamMb = estimatedTotalRamMb,
            availableDeviceRamMb = availableDeviceRamMb,
            totalDeviceRamMb = totalDeviceRamMb,
            recommendedThreads = recommendedThreads,
            fitRecommendation = recommendation
        )
    }

    /**
     * Inspects device thermal status and provides throttle recommendations.
     */
    fun getThermalAdvice(context: Context): ThermalAdvice {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val status = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE

            return when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalAdvice("Nominal (Cool)", false, 8)
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalAdvice("Light Thermal Load", false, 6)
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalAdvice("Moderate (Warm)", false, 4)
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalAdvice("Severe Throttle", true, 2)
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalAdvice("Critical Temperature", true, 1)
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalAdvice("Emergency Cool-down", true, 1)
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalAdvice("Imminent Shutdown", true, 1)
                else -> ThermalAdvice("Nominal", false, 6)
            }
        }
        return ThermalAdvice("Nominal", false, 6)
    }

    /**
     * Estimates SoC temperature in Celsius based on thermal status.
     */
    fun getDeviceTemperature(context: Context): Float {
        val advice = getThermalAdvice(context)
        return when {
            advice.isThrottled -> 43.5f
            advice.thermalStatus.contains("Moderate", ignoreCase = true) -> 39.0f
            advice.thermalStatus.contains("Light", ignoreCase = true) -> 37.5f
            else -> 35.5f
        }
    }
}
