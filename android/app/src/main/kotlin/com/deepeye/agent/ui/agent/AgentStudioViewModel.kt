package com.deepeye.agent.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.core.agent.AgentExecutionStep
import com.deepeye.agent.core.agent.AgentRegistry
import com.deepeye.agent.core.agent.AgentSpec
import com.deepeye.agent.core.agent.AutonomousAgentEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentStudioUiState(
    val agents: List<AgentSpec> = emptyList(),
    val selectedAgent: AgentSpec? = null,
    val researchQuery: String = "",
    val executionSteps: List<AgentExecutionStep> = emptyList(),
    val isExecuting: Boolean = false,
    val isCreatorDialogVisible: Boolean = false,
    val finalResult: String? = null
)

@HiltViewModel
class AgentStudioViewModel @Inject constructor(
    private val agentRegistry: AgentRegistry,
    private val autonomousAgentEngine: AutonomousAgentEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentStudioUiState())
    val uiState: StateFlow<AgentStudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            agentRegistry.agents.collect { agentList ->
                _uiState.update { state ->
                    state.copy(
                        agents = agentList,
                        selectedAgent = state.selectedAgent ?: agentList.firstOrNull()
                    )
                }
            }
        }
    }

    fun selectAgent(agent: AgentSpec) {
        _uiState.update { it.copy(selectedAgent = agent) }
    }

    fun updateResearchQuery(query: String) {
        _uiState.update { it.copy(researchQuery = query) }
    }

    fun toggleCreatorDialog(show: Boolean) {
        _uiState.update { it.copy(isCreatorDialogVisible = show) }
    }

    fun createCustomAgent(name: String, role: String, systemPrompt: String, tools: List<String>, emoji: String) {
        val newAgent = agentRegistry.addCustomAgent(
            name = name,
            role = role,
            systemPrompt = systemPrompt,
            tools = tools,
            iconEmoji = emoji
        )
        _uiState.update {
            it.copy(
                selectedAgent = newAgent,
                isCreatorDialogVisible = false
            )
        }
    }

    fun executeDeepResearch() {
        val agent = _uiState.value.selectedAgent ?: return
        val query = _uiState.value.researchQuery
        if (query.isBlank()) return

        _uiState.update {
            it.copy(
                isExecuting = true,
                executionSteps = emptyList(),
                finalResult = null
            )
        }

        viewModelScope.launch {
            autonomousAgentEngine.runResearchLoop(agent, query).collect { step ->
                _uiState.update { state ->
                    val updatedSteps = state.executionSteps + step
                    val finalRes = if (step.phase == com.deepeye.agent.core.agent.ExecutionPhase.SYNTHESIS) step.detail else state.finalResult
                    state.copy(
                        executionSteps = updatedSteps,
                        finalResult = finalRes,
                        isExecuting = step.phase != com.deepeye.agent.core.agent.ExecutionPhase.COMPLETE
                    )
                }
            }
        }
    }
}
