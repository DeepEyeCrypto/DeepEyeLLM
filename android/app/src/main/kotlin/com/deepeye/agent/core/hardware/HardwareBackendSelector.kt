package com.deepeye.agent.core.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class BackendConfig(
    val backendType: Int, // 0 = CPU, 1 = Vulkan, 2 = OpenCL, 3 = Hexagon QNN
    val nGpuLayers: Int
)

object HardwareBackendSelector {

    fun selectOptimalBackend(context: Context? = null): BackendConfig {
        return when {
            isHexagonNpuAvailable() -> BackendConfig(backendType = 3, nGpuLayers = 99)
            isVulkanAvailable(context) -> BackendConfig(backendType = 1, nGpuLayers = 99)
            isOpenCLAvailable() -> BackendConfig(backendType = 2, nGpuLayers = 99)
            else -> BackendConfig(backendType = 0, nGpuLayers = 0)
        }
    }

    fun applyBackendConfig(nativeHandle: Long = 0L, context: Context? = null): BackendConfig {
        return selectOptimalBackend(context)
    }

    private fun isHexagonNpuAvailable(): Boolean {
        val platform = getSystemProperty("ro.board.platform").lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return platform.startsWith("msm") || platform.startsWith("sdm") || platform.startsWith("sm") || hardware.contains("qcom")
    }

    private fun isVulkanAvailable(context: Context?): Boolean {
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

    private fun isOpenCLAvailable(): Boolean {
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
