package com.deepeye.agent

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deepeye.agent.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * DeepEyeLLM Application entry point.
 * @HiltAndroidApp triggers Hilt's code generation for the DI graph.
 */
@HiltAndroidApp
class DeepEyeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // StrictMode in debug builds — catches main-thread I/O and network calls
        // that would cause BLAST sync timeouts and potential ANRs.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        // Auto-Update Configuration: Network connection required
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        // 1. Immediate startup auto-update check
        val immediateSyncRequest = androidx.work.OneTimeWorkRequestBuilder<com.deepeye.agent.services.SyncWorker>()
            .setConstraints(syncConstraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "immediate_startup_sync",
            androidx.work.ExistingWorkPolicy.REPLACE,
            immediateSyncRequest
        )
            
        // 2. Periodic background auto-update sync (every 12 hours)
        val periodicSyncRequest = PeriodicWorkRequestBuilder<com.deepeye.agent.services.SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "upstream_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncRequest
        )
    }
}
