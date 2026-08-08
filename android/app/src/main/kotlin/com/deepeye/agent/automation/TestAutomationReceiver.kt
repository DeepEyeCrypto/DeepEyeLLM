package com.deepeye.agent.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deepeye.agent.core.memory.HermesDatabase
import com.deepeye.agent.core.update.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TestAutomationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var hermesDatabase: HermesDatabase

    companion object {
        const val ACTION_COMMAND = "com.deepeye.agent.automation.COMMAND"
        const val EXTRA_COMMAND = "command"
        
        const val CMD_TRIGGER_SYNC = "TRIGGER_SYNC"
        const val CMD_CLEAR_MEMORY = "CLEAR_MEMORY"
        const val CMD_MOCK_DOWNLOAD_SUCCESS = "MOCK_DOWNLOAD_SUCCESS"
        const val CMD_CHAOS_MODE = "CHAOS_MODE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COMMAND) {
            val command = intent.getStringExtra(EXTRA_COMMAND)
            Log.d("DeepEye-Automation", "Received command: $command")
            
            CoroutineScope(Dispatchers.IO).launch {
                when (command) {
                    CMD_TRIGGER_SYNC -> {
                        if (::updateChecker.isInitialized) {
                            updateChecker.checkForUpdates()
                            Log.d("DeepEye-Automation", "Sync triggered via ADB")
                        } else {
                            Log.w("DeepEye-Automation", "updateChecker not initialized")
                        }
                    }
                    CMD_CLEAR_MEMORY -> {
                        if (::hermesDatabase.isInitialized) {
                            hermesDatabase.memoryDao().clearMemories()
                            Log.d("DeepEye-Automation", "Memory cleared via ADB")
                        } else {
                            Log.w("DeepEye-Automation", "hermesDatabase not initialized")
                        }
                    }
                    CMD_MOCK_DOWNLOAD_SUCCESS -> {
                        // Mock implementation for test hooks
                        Log.d("DeepEye-Automation", "Mock download triggered")
                    }
                    CMD_CHAOS_MODE -> {
                        // Simulating a critical failure for testing resilience
                        Log.e("DeepEye-Automation", "🔥 CHAOS MODE ACTIVATED: Simulating engine crash...")
                        throw RuntimeException("Chaos Mode: Intentional Crash")
                    }
                    else -> {
                        Log.e("DeepEye-Automation", "Unknown command: $command")
                    }
                }
            }
        }
    }
}
