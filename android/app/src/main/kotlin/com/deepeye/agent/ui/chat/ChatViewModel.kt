package com.deepeye.agent.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.analysis.FileAnalysisService
import com.deepeye.agent.core.model.ChatMessage
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.EngineStatus
import com.deepeye.agent.domain.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.LOCAL_ACTIVE
)

@Immutable
data class ChatUiState(
    val prompt: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val activeStreamingMessageId: String? = null,
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
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _activeStreamingMessageId = MutableStateFlow<String?>(null)
    val activeStreamingMessageId: StateFlow<String?> = _activeStreamingMessageId.asStateFlow()

    private var activeGenerationJob: Job? = null

    val state: StateFlow<ChatUiState> = combine(
        _chatState,
        _isGenerating,
        _activeStreamingMessageId,
        engineController.engineStatus
    ) { chat, generating, streamingId, engine ->
        chat.copy(
            isGenerating = generating,
            activeStreamingMessageId = streamingId,
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
        viewModelScope.launch(Dispatchers.IO) {
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

    fun sendStream(promptText: String? = null, onChunk: (String) -> Unit = {}) {
        val inputPrompt = promptText ?: _chatState.value.prompt
        if (inputPrompt.isBlank()) return

        cancelGeneration()

        val userMessage = Message(text = inputPrompt, isUser = true)
        val assistantPlaceholderId = java.util.UUID.randomUUID().toString()
        val assistantPlaceholder = Message(
            id = assistantPlaceholderId,
            text = "",
            isUser = false,
            isStreaming = true
        )

        _chatState.update {
            it.copy(
                prompt = "",
                error = null,
                messages = it.messages + userMessage + assistantPlaceholder
            )
        }
        _isGenerating.value = true
        _activeStreamingMessageId.value = assistantPlaceholderId

        activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                engineController.executeChatStream(inputPrompt) { chunk ->
                    onChunk(chunk)
                    _chatState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == assistantPlaceholderId) {
                                msg.copy(text = msg.text + chunk, isStreaming = true)
                            } else msg
                        }
                        state.copy(messages = updatedMessages)
                    }
                }
                _chatState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == assistantPlaceholderId) msg.copy(isStreaming = false) else msg
                    }
                    state.copy(messages = updatedMessages)
                }
            } catch (e: Throwable) {
                _chatState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == assistantPlaceholderId) {
                            msg.copy(
                                text = if (msg.text.isBlank()) "Error: ${e.message ?: "Generation failed"}" else msg.text,
                                isStreaming = false,
                                isError = true
                            )
                        } else msg
                    }
                    state.copy(messages = updatedMessages, error = e.message)
                }
            } finally {
                _isGenerating.value = false
                _activeStreamingMessageId.value = null
            }
        }
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
        _activeStreamingMessageId.value = null
        _chatState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.isStreaming) msg.copy(isStreaming = false) else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    fun sendPrompt() {
        sendStream()
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
