package com.deepeye.agent.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

enum class UiLayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@Composable
fun currentUiLayoutMode(): UiLayoutMode {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthSizeClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    val heightSizeClass = adaptiveInfo.windowSizeClass.windowHeightSizeClass

    return when {
        widthSizeClass == WindowWidthSizeClass.COMPACT -> UiLayoutMode.COMPACT
        widthSizeClass == WindowWidthSizeClass.MEDIUM || heightSizeClass == WindowHeightSizeClass.COMPACT -> UiLayoutMode.MEDIUM
        widthSizeClass == WindowWidthSizeClass.EXPANDED -> UiLayoutMode.EXPANDED
        else -> UiLayoutMode.COMPACT
    }
}
