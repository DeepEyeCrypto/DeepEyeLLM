package com.deepeye.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

/**
 * Modular Bento Grid Layout.
 * Dynamically adjusts column count based on Window Size Class:
 * - Phone (Compact): 1 column
 * - Tablet/Foldable (Medium): 2 columns
 * - Desktop/Expanded: 3 to 4 columns
 */
@Composable
fun BentoGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalSpacing: Dp = 12.dp,
    horizontalSpacing: Dp = 12.dp,
    content: LazyGridScope.() -> Unit
) {
    val layoutMode = currentUiLayoutMode()
    val columns = when (layoutMode) {
        UiLayoutMode.COMPACT -> 1
        UiLayoutMode.MEDIUM -> 2
        UiLayoutMode.EXPANDED -> 3
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        content = content
    )
}
