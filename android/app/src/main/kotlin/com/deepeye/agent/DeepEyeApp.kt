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
import kotlinx.coroutines.launch

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

        // Offload WorkManager initialization to IO thread to prevent main-thread startup frame drops
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val syncConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val immediateSyncRequest = androidx.work.OneTimeWorkRequestBuilder<com.deepeye.agent.services.SyncWorker>()
                .setConstraints(syncConstraints)
                .build()

            WorkManager.getInstance(this@DeepEyeApp).enqueueUniqueWork(
                "immediate_startup_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                immediateSyncRequest
            )
                
            val periodicSyncRequest = PeriodicWorkRequestBuilder<com.deepeye.agent.services.SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(syncConstraints)
                .build()
                
            WorkManager.getInstance(this@DeepEyeApp).enqueueUniquePeriodicWork(
                "upstream_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicSyncRequest
            )
        }
    }
}
