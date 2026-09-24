package com.deepeye.agent.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Dynamic Animated GPU Neural Canvas
        AnimatedCyberBackground(
            modifier = Modifier.fillMaxSize(),
            particleCount = 38
        )

        // State-preserving Navigation Host
        DeepEyeNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )
    }
}
