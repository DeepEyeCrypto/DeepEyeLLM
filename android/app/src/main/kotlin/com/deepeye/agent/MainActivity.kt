package com.deepeye.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.deepeye.agent.ui.AgentAppShell
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.updater.UpdateManager
import com.deepeye.agent.updater.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.content.Context
import com.deepeye.agent.automation.TestAutomationReceiver

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updateManager: UpdateManager

    private val automationReceiver = TestAutomationReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        updateManager.checkForUpdates()

        setContent {
            DeepEyeTheme {
                AgentAppShell()
                UpdateDialog(updateManager = updateManager)
            }
        }
        
        val filter = android.content.IntentFilter(com.deepeye.agent.automation.TestAutomationReceiver.ACTION_COMMAND)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(automationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(automationReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(automationReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }
}
