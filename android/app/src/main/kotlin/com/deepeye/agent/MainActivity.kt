package com.deepeye.agent

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.deepeye.agent.automation.TestAutomationReceiver
import com.deepeye.agent.domain.engine.ColibriEngine
import com.deepeye.agent.domain.engine.LlamaCppEngine
import com.deepeye.agent.ui.AgentAppShell
import com.deepeye.agent.ui.theme.DeepEyeTheme
import com.deepeye.agent.updater.UpdateDialog
import com.deepeye.agent.updater.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
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

        // ─── Autonomous 0.5B Self-Healing Inference Smoke Test ────────────────
        // Default OFF: a full model init + generation on every cold start burns
        // CPU/battery and races the user's first interaction. Opt in per-launch
        // from debug tooling:
        //   adb shell am start -n com.deepeye.agent/.MainActivity --ez deepeye.smoke_test true
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("deepeye.smoke_test", false) == true) {
            lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i("DeepEye-Colibri", "=== [Apex-Architect] Initializing Autonomous 0.5B Smoke Test ===")
                val modelsDir = File(filesDir, "models")
                val availableModels = modelsDir.listFiles { _, name ->
                    (name.endsWith(".gguf") || name.endsWith(".colibri") || name.endsWith(".coli") || name.endsWith(".bin")) && !name.endsWith(".tmp")
                }
                val targetModel = availableModels?.firstOrNull() ?: File(modelsDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")

                Log.i("DeepEye-Colibri", "Target model: ${targetModel.absolutePath} (exists=${targetModel.exists()}, size=${if (targetModel.exists()) targetModel.length() else 0} bytes)")

                if (targetModel.exists()) {
                    val isColibriFormat = targetModel.name.endsWith(".colibri") || targetModel.name.endsWith(".coli") || targetModel.isDirectory
                    val engine = if (isColibriFormat) {
                        ColibriEngine(
                            modelDir = targetModel.absolutePath,
                            context = applicationContext,
                            contextTokens = 2048,
                            nThreads = 4,
                            temperature = 0.7f,
                            topP = 0.9f
                        )
                    } else {
                        LlamaCppEngine(
                            modelPath = targetModel.absolutePath,
                            context = applicationContext,
                            useGpu = false,
                            gpuLayers = 0,
                            customThreads = 4,
                            customContextSize = 2048
                        )
                    }

                    Log.i("DeepEye-Colibri", "Initializing engine backend: ${engine.backend}...")
                    val initResult = engine.init()
                    if (initResult.isSuccess) {
                        Log.i("DeepEye-Colibri", "Engine initialized successfully! Sending test prompt: 'Say Hello'")
                        val output = StringBuilder()
                        engine.chatStream("Say 'Hello'") { tokenChunk ->
                            output.append(tokenChunk)
                            Log.i("DeepEye-Colibri", "CHUNK: $tokenChunk")
                        }
                        val fullResponse = output.toString()
                        Log.i("DeepEye-Colibri", "=== [Apex-Architect] 0.5B TEST INFERENCE SUCCESS ===")
                        Log.i("DeepEye-Colibri", "GENERATED TEXT: $fullResponse")
                    } else {
                        Log.e("DeepEye-Colibri", "Engine initialization failed: ${initResult.exceptionOrNull()?.message}", initResult.exceptionOrNull())
                    }
                } else {
                    Log.w("DeepEye-Colibri", "Model file not found at ${targetModel.absolutePath}")
                }
            } catch (e: Throwable) {
                Log.e("DeepEye-Colibri", "Exception during autonomous 0.5B inference test: ${e.message}", e)
            }
        }
        } // end deepeye.smoke_test opt-in gate
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
