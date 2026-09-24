package com.deepeye.agent

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deepeye.agent.automation.TestAutomationReceiver
import com.deepeye.agent.ui.AgentAppShell
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.updater.UpdateDialog
import com.deepeye.agent.updater.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updateManager: UpdateManager

    private val automationReceiver = TestAutomationReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure True Immersive Fullscreen (Hide Status Bar completely)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        updateManager.checkForUpdates()

        setContent {
            DeepEyeTheme {
                AgentAppShell()
                UpdateDialog(updateManager = updateManager)
            }
        }
        
        val filter = android.content.IntentFilter(com.deepeye.agent.automation.TestAutomationReceiver.ACTION_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(automationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(automationReceiver, filter)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
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
