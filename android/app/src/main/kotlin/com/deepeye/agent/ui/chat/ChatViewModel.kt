package com.deepeye.agent.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.analysis.FileAnalysisService
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.EngineStatus
import com.deepeye.agent.domain.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val modelStatus: ModelStatus = ModelStatus.LOCAL_ACTIVE
)

data class ChatUiState(
    val prompt: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.LOCAL_ACTIVE,
    val error: String? = null,
    val showModelPicker: Boolean = false,
    val activeModelName: String = "No Model Loaded"
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engineController: EngineController,
    private val fileService: FileAnalysisService
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatUiState())

    /**
     * Combined state: merges internal chat state with shared engine status.
     * ChatViewModel observes EngineController.engineStatus for model name/status
     * changes triggered by ModelCatalogViewModel.
     */
    val state: StateFlow<ChatUiState> = combine(
        _chatState,
        engineController.engineStatus
    ) { chat, engine ->
        chat.copy(
            modelStatus = engine.modelStatus,
            activeModelName = engine.activeModelName,
            error = if (engine.statusMessage.isNotBlank()) engine.statusMessage else chat.error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ChatUiState()
    )

    init {
        viewModelScope.launch {
            _chatState.update { it.copy(isLoading = true) }
            val (status, msg) = engineController.initialize()
            _chatState.update {
                it.copy(
                    isLoading = false,
                    modelStatus = status,
                    messages = listOf(Message(text = msg, isUser = false, modelStatus = status))
                )
            }
        }
    }

    fun onPromptChange(value: String) {
        _chatState.update { it.copy(prompt = value) }
    }

    fun toggleModelPicker(show: Boolean) {
        _chatState.update { it.copy(showModelPicker = show) }
    }

    fun sendPrompt() = viewModelScope.launch {
        val prompt = _chatState.value.prompt
        if (prompt.isBlank()) return@launch

        _chatState.update {
            val userMsg = Message(text = prompt, isUser = true)
            it.copy(
                isLoading = true,
                prompt = "",
                error = null,
                messages = it.messages + userMsg
            )
        }

        val (status, responseText) = engineController.executeChat(prompt)

        _chatState.update {
            val responseMsg = Message(text = responseText, isUser = false, modelStatus = status)
            it.copy(
                isLoading = false,
                modelStatus = status,
                messages = it.messages + responseMsg
            )
        }
    }

    fun analyzeFile(uri: Uri) = viewModelScope.launch {
        _chatState.update {
            it.copy(
                isLoading = true,
                error = null,
                messages = it.messages + Message(text = "Analyzing file: ${uri.lastPathSegment}", isUser = true)
            )
        }
        runCatching {
            fileService.analyze(uri)
        }.onSuccess { result ->
            _chatState.update {
                it.copy(
                    isLoading = false,
                    messages = it.messages + Message(text = result, isUser = false, modelStatus = _chatState.value.modelStatus)
                )
            }
        }.onFailure { e ->
            _chatState.update { it.copy(error = e.message, isLoading = false) }
        }
    }

    fun deepDebugFile(uri: Uri) = viewModelScope.launch {
        _chatState.update {
            it.copy(
                isLoading = true,
                error = null,
                messages = it.messages + Message(text = "Deep Debugging file: ${uri.lastPathSegment}", isUser = true)
            )
        }
        runCatching {
            fileService.deepDebugLocally(uri)
        }.onSuccess { result ->
            _chatState.update {
                it.copy(
                    isLoading = false,
                    modelStatus = ModelStatus.LOCAL_ACTIVE,
                    messages = it.messages + Message(text = result, isUser = false, modelStatus = ModelStatus.LOCAL_ACTIVE)
                )
            }
        }.onFailure { e ->
            _chatState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
