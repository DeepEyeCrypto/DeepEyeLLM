package com.deepeye.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.core.diagnostics.HealthMonitor
import com.deepeye.agent.core.diagnostics.SystemHealth
import com.deepeye.agent.core.policy.PolicyAuditLog
import com.deepeye.agent.core.policy.PolicyAuditEntry
import com.deepeye.agent.core.update.UpdateChecker
import com.deepeye.agent.core.update.UpdateManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val systemHealth: SystemHealth? = null,
    val auditLogs: List<PolicyAuditEntry> = emptyList(),
    val updateManifest: UpdateManifest? = null
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val healthMonitor: HealthMonitor,
    private val policyAuditLog: PolicyAuditLog,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val health = healthMonitor.getSystemHealth()
            val logs = policyAuditLog.getRecent(50)
            val manifest = updateChecker.getManifest()

            _uiState.value = DiagnosticsUiState(
                systemHealth = health,
                auditLogs = logs,
                updateManifest = manifest
            )
        }
    }
}
