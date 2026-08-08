package com.deepeye.agent.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val query: String = "",
    val selectedFilter: HistoryFilter = HistoryFilter.All,
    val items: List<HistoryItemUi> = emptyList(),
    val selectedItem: HistoryItemUi? = null,
    val error: String? = null,
    val isLoading: Boolean = false
)

enum class HistoryFilter {
    All, Local, Debug, Files, Logs
}

data class HistoryItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val sourceTag: String,
    val modeTag: String,
    val details: String,
    val summary: String = "", // Added for filtering logic
    val type: HistoryFilter = HistoryFilter.All // Added for filtering logic
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository
) : ViewModel() {

    private val _historyState = MutableStateFlow(HistoryUiState())
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() = viewModelScope.launch {
        _historyState.update { it.copy(isLoading = true, error = null) }
        runCatching {
            repository.getHistory()
        }.onSuccess { allItems ->
            _historyState.update { 
                it.copy(
                    items = applyFilters(allItems, it.selectedFilter, it.query),
                    isLoading = false
                )
            }
        }.onFailure { e ->
            _historyState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun setFilterType(type: HistoryFilter) {
        _historyState.update { it.copy(selectedFilter = type) }
        loadHistory()
    }

    fun setSearchQuery(query: String) {
        _historyState.update { it.copy(query = query) }
        loadHistory()
    }

    fun deleteItem(id: String) = viewModelScope.launch {
        repository.deleteHistory(id)
        loadHistory()
    }

    fun shareItem(id: String) = viewModelScope.launch {
        repository.exportHistoryItem(id)
        // Sharing intent handled at UI layer via exported URI
    }

    private fun applyFilters(items: List<HistoryItemUi>, filter: HistoryFilter, query: String): List<HistoryItemUi> {
        val filteredByType = if (filter == HistoryFilter.All) items else items.filter { it.type == filter }
        if (query.isBlank()) return filteredByType
        val lowercaseQuery = query.lowercase()
        return filteredByType.filter {
            it.title.lowercase().contains(lowercaseQuery) || 
            it.summary.lowercase().contains(lowercaseQuery)
        }
    }
}
