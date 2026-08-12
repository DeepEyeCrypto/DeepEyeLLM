package com.deepeye.agent.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/**
 * Type-safe DeepEye Navigation Host with smooth entry/exit animations and predictive back gesture handling.
 */
@Composable
fun DeepEyeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    DeepEyeNavGraph(
        navController = navController,
        modifier = modifier
    )
}
