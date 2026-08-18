package com.deepeye.agent.core.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class BackendConfig(
    val backendType: Int, // -1 = Auto, 0 = CPU, 1 = Vulkan, 2 = OpenCL, 3 = Hexagon QNN, 4 = KleidiAI
    val nGpuLayers: Int,
    val name: String = ""
)

data class HardwareBackendOption(
    val id: Int,
    val label: String,
    val description: String,
    val isAvailable: Boolean
)

object HardwareBackendSelector {

    const val BACKEND_AUTO = -1
    const val BACKEND_CPU = 0
    const val BACKEND_VULKAN = 1
    const val BACKEND_OPENCL = 2
    const val BACKEND_HEXAGON_QNN = 3
    const val BACKEND_KLEIDIAI = 4

    fun selectOptimalBackend(
        context: Context? = null,
        userBackend: Int = BACKEND_AUTO,
        userGpuLayers: Int = 99
    ): BackendConfig {
        if (userBackend != BACKEND_AUTO) {
            val layers = if (userBackend == BACKEND_CPU) 0 else userGpuLayers
            return BackendConfig(
                backendType = userBackend,
                nGpuLayers = layers,
                name = getBackendName(userBackend)
            )
        }

        val isMaliOrMediaTek = isMediaTekPlatform() || isMaliPlatform()

        return when {
            isHexagonNpuAvailable() -> BackendConfig(backendType = BACKEND_HEXAGON_QNN, nGpuLayers = userGpuLayers, name = "Snapdragon Hexagon NPU")
            isAdrenoGpu() && isVulkanAvailable(context) -> BackendConfig(backendType = BACKEND_VULKAN, nGpuLayers = userGpuLayers, name = "Adreno Vulkan GPU")
            isMaliOrMediaTek -> BackendConfig(backendType = BACKEND_CPU, nGpuLayers = 0, name = "ARM NEON Turbo CPU")
            isVulkanAvailable(context) -> BackendConfig(backendType = BACKEND_VULKAN, nGpuLayers = userGpuLayers, name = "Vulkan GPU Acceleration")
            isOpenCLAvailable() -> BackendConfig(backendType = BACKEND_OPENCL, nGpuLayers = userGpuLayers, name = "OpenCL GPU")
            else -> BackendConfig(backendType = BACKEND_CPU, nGpuLayers = 0, name = "CPU Only (ARM NEON)")
        }
    }

    fun applyBackendConfig(
        nativeHandle: Long = 0L,
        context: Context? = null,
        userBackend: Int = BACKEND_AUTO,
        userGpuLayers: Int = 99
    ): BackendConfig {
        return selectOptimalBackend(context, userBackend, userGpuLayers)
    }

    fun getBackendName(type: Int): String = when (type) {
        BACKEND_AUTO -> "Auto (Smart Detect)"
        BACKEND_VULKAN -> "Vulkan GPU"
        BACKEND_HEXAGON_QNN -> "Qualcomm Hexagon NPU"
        BACKEND_OPENCL -> "OpenCL GPU"
        BACKEND_KLEIDIAI -> "ARM KleidiAI"
        BACKEND_CPU -> "CPU Only"
        else -> "Auto (Smart Detect)"
    }

    fun getAvailableBackends(context: Context? = null): List<HardwareBackendOption> {
        return listOf(
            HardwareBackendOption(
                id = BACKEND_AUTO,
                label = "⚡ Auto (Smart Select)",
                description = "Automatically detects fastest NPU / GPU hardware available",
                isAvailable = true
            ),
            HardwareBackendOption(
                id = BACKEND_VULKAN,
                label = "🌋 Vulkan GPU",
                description = "Universal cross-vendor GPU acceleration (Adreno, Mali, PowerVR)",
                isAvailable = isVulkanAvailable(context)
            ),
            HardwareBackendOption(
                id = BACKEND_HEXAGON_QNN,
                label = "🧠 Hexagon NPU (QNN)",
                description = "Snapdragon Neural Processing Unit for ultra-low power inference",
                isAvailable = isHexagonNpuAvailable()
            ),
            HardwareBackendOption(
                id = BACKEND_OPENCL,
                label = "🌀 OpenCL GPU",
                description = "OpenCL compute shaders offloading for mobile GPUs",
                isAvailable = isOpenCLAvailable()
            ),
            HardwareBackendOption(
                id = BACKEND_KLEIDIAI,
                label = "🧬 ARM KleidiAI",
                description = "Optimized ARM SIMD micro-kernels for Cortex-A cores",
                isAvailable = true
            ),
            HardwareBackendOption(
                id = BACKEND_CPU,
                label = "💻 CPU Only",
                description = "Standard multi-threaded ARM CPU processing",
                isAvailable = true
            )
        )
    }

    fun isMediaTekPlatform(): Boolean {
        val platform = getSystemProperty("ro.board.platform").lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase() else ""
        return platform.startsWith("mt") || hardware.contains("mt") || hardware.contains("dimensity") || soc.contains("dimensity") || soc.contains("mt")
    }

    fun isMaliPlatform(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val platform = getSystemProperty("ro.board.platform").lowercase()
        return hardware.contains("mali") || platform.contains("mali") || hardware.contains("exynos") || hardware.contains("tensor")
    }

    fun isAdrenoGpu(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val platform = getSystemProperty("ro.board.platform").lowercase()
        return platform.startsWith("msm") || platform.startsWith("sdm") || platform.startsWith("sm") || hardware.contains("qcom") || hardware.contains("adreno")
    }

    fun isHexagonNpuAvailable(): Boolean {
        val platform = getSystemProperty("ro.board.platform").lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return platform.startsWith("msm") || platform.startsWith("sdm") || platform.startsWith("sm") || hardware.contains("qcom")
    }

    fun isVulkanAvailable(context: Context?): Boolean {
        if (context != null) {
            val pm = context.packageManager
            return pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)
        }
        return try {
            System.loadLibrary("vulkan")
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun isOpenCLAvailable(): Boolean {
        return try {
            System.loadLibrary("OpenCL")
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            getMethod.invoke(null, key) as String
        } catch (e: Exception) {
            ""
        }
    }
}
