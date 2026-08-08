package com.deepeye.agent.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.deepeye.agent.core.datastore.EngineSettings
import com.deepeye.agent.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val offlineMode: Boolean = true,
    val policyCheckEnabled: Boolean = true,
    val autoUpdateSkills: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
    val enableAuditLogging: Boolean = true,
    val modelStorageUsed: String = "0 B",
    val modelStorageTotal: String = "0 B",
    val recentLogsCount: Int = 0,
    val error: String? = null,
    val showModelManager: Boolean = false,
    val engineSettings: EngineSettings = EngineSettings()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _settingsState = MutableStateFlow(SettingsUiState())
    
    val settingsState: StateFlow<SettingsUiState> = combine(
        _settingsState,
        settingsDataStore.engineSettingsFlow
    ) { state, engineSettings ->
        state.copy(engineSettings = engineSettings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        val modelsDir = File(context.filesDir, "models")
        val usedBytes = if (modelsDir.exists()) modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        val totalBytes = context.filesDir.totalSpace
        _settingsState.update {
            it.copy(
                modelStorageUsed = formatBytes(usedBytes),
                modelStorageTotal = formatBytes(totalBytes)
            )
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        else -> "%.1f KB".format(bytes / 1024.0)
    }

    fun toggleOfflineMode(enabled: Boolean) {
        _settingsState.update { it.copy(offlineMode = enabled) }
    }

    fun togglePolicyChecks(enabled: Boolean) {
        _settingsState.update { it.copy(policyCheckEnabled = enabled) }
    }

    fun toggleAutoUpdate(enabled: Boolean) {
        _settingsState.update { it.copy(autoUpdateSkills = enabled) }
    }

    fun toggleDiagnostics(enabled: Boolean) {
        _settingsState.update { it.copy(diagnosticsEnabled = enabled) }
    }
    
    fun toggleAuditLogging(enabled: Boolean) {
        _settingsState.update { it.copy(enableAuditLogging = enabled) }
    }

    fun updateUseGpu(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateUseGpu(enabled) }
    }

    fun updateCpuThreads(threads: Int) {
        viewModelScope.launch { settingsDataStore.updateCpuThreads(threads) }
    }

    fun updateContextSize(contextSize: Int) {
        viewModelScope.launch { settingsDataStore.updateContextSize(contextSize) }
    }

    fun updateTemperature(temp: Float) {
        viewModelScope.launch { settingsDataStore.updateTemperature(temp) }
    }

    fun updateTopP(topP: Float) {
        viewModelScope.launch { settingsDataStore.updateTopP(topP) }
    }
}
