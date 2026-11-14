package com.deepeye.agent.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.deepeye.agent.core.diagnostics.PerformanceGovernor
import com.deepeye.agent.ui.components.AnimatedCyberBackground
import com.deepeye.agent.ui.navigation.DeepEyeNavHost

/**
 * Fullscreen Minimalist ChatGPT-style Shell with 60fps Dynamic Animated Cyber Neural Canvas.
 */
@Composable
fun AgentAppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Pause the animated background when the governor throttles (heat / zero-blur),
    // so the 3-4 background frame animators release their per-frame work.
    val perfMetrics by PerformanceGovernor.metrics.collectAsStateWithLifecycle()
    val backgroundEnabled = !perfMetrics.isZeroBlurActive &&
        !perfMetrics.thermalThrottlingLevel.isThrottled

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Dynamic Animated GPU Neural Canvas
        AnimatedCyberBackground(
            modifier = Modifier.fillMaxSize(),
            particleCount = 24,
            enabled = backgroundEnabled
        )

        // State-preserving Navigation Host
        DeepEyeNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )
    }
}
