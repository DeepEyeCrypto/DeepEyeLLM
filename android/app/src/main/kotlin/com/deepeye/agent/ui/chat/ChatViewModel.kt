package com.deepeye.agent.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.analysis.LocalFileAnalysisService
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
    val modelStatus: ModelStatus = ModelStatus.LOCAL_ACTIVE,
    val dexTradeIntent: com.deepeye.agent.core.dex.DexTradeIntent? = null
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
    val activeModelName: String = "No Model Loaded",
    val tokensPerSecond: Float = 0f
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engineController: EngineController,
    private val fileService: LocalFileAnalysisService,
    private val dexTradingEngine: com.deepeye.agent.core.dex.DexTradingEngine
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
            error = if (engine.modelStatus == ModelStatus.ERROR && engine.statusMessage.isNotBlank()) engine.statusMessage else null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ChatUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val (status, _) = engineController.initialize()
            _chatState.update {
                it.copy(
                    isLoading = false,
                    modelStatus = status
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

    fun selectAndActivateModel(modelId: String, fileName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val modelsDir = java.io.File(engineController.context.filesDir, "models")
            var destFile: java.io.File? = null

            if (!fileName.isNullOrBlank()) {
                val direct = java.io.File(modelsDir, fileName)
                if (direct.exists() && direct.length() > 1_000_000L) {
                    destFile = direct
                }
            }

            if (destFile == null) {
                val directId = java.io.File(modelsDir, modelId)
                if (directId.exists() && directId.length() > 1_000_000L) {
                    destFile = directId
                }
            }

            if (destFile == null) {
                val filesOnDisk = modelsDir.listFiles() ?: emptyArray()
                destFile = filesOnDisk.find {
                    it.name.equals(modelId, ignoreCase = true) ||
                    it.nameWithoutExtension.equals(modelId, ignoreCase = true) ||
                    (!fileName.isNullOrBlank() && it.name.equals(fileName, ignoreCase = true)) ||
                    (!fileName.isNullOrBlank() && it.nameWithoutExtension.equals(fileName.substringBeforeLast('.'), ignoreCase = true))
                }
            }

            if (destFile != null && destFile.exists() && destFile.length() > 1_000_000L) {
                android.util.Log.d("DeepEye", "{\"event\":\"activating_model\", \"path\":\"${destFile.absolutePath}\"}")
                engineController.reinitializeWithModel(destFile.absolutePath)
            }
        }
    }

    fun executeDexSwap(messageId: String, intent: com.deepeye.agent.core.dex.DexTradeIntent) {
        val executed = dexTradingEngine.executeSwap(intent)
        _chatState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(
                        text = msg.text + "\n\n✅ **Transaction Broadcasted**: ${executed.statusMessage}",
                        dexTradeIntent = executed
                    )
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    fun sendStream(promptText: String? = null, onChunk: (String) -> Unit = {}) {
        val inputPrompt = promptText ?: _chatState.value.prompt
        if (inputPrompt.isBlank()) return

        cancelGeneration()

        val userMessage = Message(text = inputPrompt, isUser = true)
        val assistantPlaceholderId = java.util.UUID.randomUUID().toString()

        // Handle specialized /dex crypto trading command & natural language trade intents
        val isDexTrade = inputPrompt.startsWith("/dex", ignoreCase = true) ||
            ((inputPrompt.contains("buy", ignoreCase = true) || inputPrompt.contains("swap", ignoreCase = true) || inputPrompt.contains("sell", ignoreCase = true)) &&
             (inputPrompt.contains("sol", ignoreCase = true) || inputPrompt.contains("eth", ignoreCase = true) || inputPrompt.contains("btc", ignoreCase = true) || inputPrompt.contains("usdc", ignoreCase = true) || inputPrompt.contains("usdt", ignoreCase = true)))

        if (isDexTrade) {
            val intent = dexTradingEngine.parseTradingIntent(inputPrompt)
            val responseText = if (intent.securityAudit.isSafeToTrade) {
                "🦁 **Nous Hermes 3 DEX Sentinel**: Verified Trade Intent.\n\n" +
                "• **Action**: ${intent.action} ${intent.amountIn} ${intent.tokenIn} ➔ ~${"%.4f".format(intent.estimatedAmountOut)} ${intent.tokenOut}\n" +
                "• **DEX Protocol**: ${intent.quote.dexRouter.displayName}\n" +
                "• **Safety Radar**: ${intent.securityAudit.overallSafetyScore}/100 (Honeypot Clean • LP Locked ${intent.securityAudit.lpLockDurationDays}d)\n" +
                "• **Slippage**: Max ${intent.maxSlippagePct}%\n\n" +
                "Interactive non-custodial trade ticket formulated below:"
            } else {
                "⚠️ **Trade Blocked by Safety Policy**: ${intent.statusMessage}"
            }

            _chatState.update {
                it.copy(
                    prompt = "",
                    error = null,
                    messages = it.messages + userMessage + Message(
                        id = assistantPlaceholderId,
                        text = responseText,
                        isUser = false,
                        dexTradeIntent = intent
                    )
                )
            }
            return
        }

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
            val responseBuffer = StringBuilder()
            var lastFlushTime = 0L
            val flushIntervalMs = 25L // Decouple token arrival from Compose recomposition loop
            var tokenCount = 0
            var firstTokenTime = 0L
            var liveTps = 0f

            fun flushToUi(force: Boolean = false, isFinished: Boolean = false) {
                val now = System.currentTimeMillis()
                if (force || now - lastFlushTime >= flushIntervalMs) {
                    lastFlushTime = now
                    if (firstTokenTime > 0L && tokenCount > 1) {
                        val elapsedDecodeSec = (now - firstTokenTime) / 1000.0f
                        if (elapsedDecodeSec > 0.02f) {
                            liveTps = ((tokenCount - 1) / elapsedDecodeSec).coerceAtLeast(1f)
                        }
                    }
                    val currentText = responseBuffer.toString()
                    _chatState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == assistantPlaceholderId) {
                                msg.copy(text = currentText, isStreaming = !isFinished)
                            } else msg
                        }
                        state.copy(messages = updatedMessages, tokensPerSecond = if (liveTps > 0f) liveTps else state.tokensPerSecond)
                    }
                }
            }

            try {
                engineController.executeChatStream(inputPrompt) { chunk ->
                    if (firstTokenTime == 0L) {
                        firstTokenTime = System.currentTimeMillis()
                    }
                    tokenCount++
                    onChunk(chunk)
                    responseBuffer.append(chunk)
                    flushToUi(force = false, isFinished = false)
                }
                val nativeStats = engineController.getPerformanceStats()
                val finalTps = nativeStats?.tokensPerSec?.toFloat()?.takeIf { it > 0f } ?: liveTps
                _chatState.update { it.copy(tokensPerSecond = finalTps) }
                flushToUi(force = true, isFinished = true)
            } catch (e: Throwable) {
                val errorMsg = e.message ?: "Generation failed"
                _chatState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == assistantPlaceholderId) {
                            msg.copy(
                                text = if (responseBuffer.isBlank()) "Error: $errorMsg" else responseBuffer.toString(),
                                isStreaming = false,
                                isError = true
                            )
                        } else msg
                    }
                    state.copy(messages = updatedMessages, error = errorMsg)
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
