package com.deepeye.agent.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SystemHealth(
    val totalRamGb: Double,
    val availableRamGb: Double,
    val totalStorageGb: Double,
    val availableStorageGb: Double,
    val isLowMemory: Boolean,
    val isStorageLow: Boolean
)

@Singleton
class HealthMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getSystemHealth(): SystemHealth {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamGb = memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val availableRamGb = memoryInfo.availMem.toDouble() / (1024 * 1024 * 1024)
        
        val statFs = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = statFs.blockSizeLong * statFs.availableBlocksLong
        val bytesTotal = statFs.blockSizeLong * statFs.blockCountLong
        
        val totalStorageGb = bytesTotal.toDouble() / (1024 * 1024 * 1024)
        val availableStorageGb = bytesAvailable.toDouble() / (1024 * 1024 * 1024)
        
        return SystemHealth(
            totalRamGb = totalRamGb,
            availableRamGb = availableRamGb,
            totalStorageGb = totalStorageGb,
            availableStorageGb = availableStorageGb,
            isLowMemory = memoryInfo.lowMemory,
            isStorageLow = availableStorageGb < 2.0 // Less than 2 GB
        )
    }
}
