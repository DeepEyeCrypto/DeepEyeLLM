package com.deepeye.agent.ui.history

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onItemClick: (HistoryItemUi) -> Unit,
    onOpenItem: (HistoryItemUi) -> Unit,
    onDeleteItem: (HistoryItemUi) -> Unit,
    onShareItem: (HistoryItemUi) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("History") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchBarSection(
                query = state.query,
                onQueryChange = onQueryChange
            )

            FilterChipRow(
                selectedFilter = state.selectedFilter,
                onFilterChange = onFilterChange
            )

            if (state.error != null) {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("History error", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (state.selectedItem != null) {
                SelectedItemDetail(
                    item = state.selectedItem,
                    onOpen = { onOpenItem(state.selectedItem) },
                    onDelete = { onDeleteItem(state.selectedItem) },
                    onShare = { onShareItem(state.selectedItem) }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onFilterClick = onFilterChange
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        placeholder = { Text("Search conversations, files, debug sessions...") },
        singleLine = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(
    selectedFilter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.name) },
                leadingIcon = if (selectedFilter == filter) {
                    {
                        Icon(Icons.Default.FilterAlt, contentDescription = "Filter")
                    }
                } else null
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryItemUi,
    onClick: () -> Unit,
    onFilterClick: (HistoryFilter) -> Unit
) {
    ElevatedCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Description, contentDescription = "History entry")
                Text(item.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(item.subtitle)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { 
                        HistoryFilter.values().find { it.name.equals(item.modeTag, ignoreCase = true) }?.let { onFilterClick(it) }
                    }, 
                    label = { Text(item.modeTag) }
                )
                AssistChip(
                    onClick = { 
                        HistoryFilter.values().find { it.name.equals(item.sourceTag, ignoreCase = true) }?.let { onFilterClick(it) }
                    }, 
                    label = { Text(item.sourceTag) }
                )
                AssistChip(onClick = {}, label = { Text(item.timestamp) })
            }
        }
    }
}

@Composable
private fun SelectedItemDetail(
    item: HistoryItemUi,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Selected item", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(item.details)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen) { Text("Open") }
                OutlinedButton(onClick = onShare) { Text("Share") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
