package com.deepeye.agent.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.analysis.AgentToolExecutor
import com.deepeye.agent.analysis.DocumentParser
import com.deepeye.agent.analysis.LocalFileAnalysisService
import com.deepeye.agent.analysis.ParsedDocument
import com.deepeye.agent.audio.VoiceManager
import com.deepeye.agent.audio.VoiceState
import com.deepeye.agent.core.model.ChatMessage
import com.deepeye.agent.core.model.ToolCallModel
import com.deepeye.agent.core.model.ToolCallStatus
import com.deepeye.agent.data.local.KnowledgeRepository
import com.deepeye.agent.data.local.KnowledgeRetriever
import com.deepeye.agent.data.local.MessageDao
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.EngineStatus
import com.deepeye.agent.domain.ModelStatus
import com.deepeye.agent.ui.components.ToolCallParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val modelStatus: ModelStatus = ModelStatus.LOCAL_ACTIVE,
    val dexTradeIntent: com.deepeye.agent.core.dex.DexTradeIntent? = null,
    val imageUris: List<String> = emptyList(),
    val toolCalls: List<com.deepeye.agent.core.model.ToolCallModel> = emptyList(),
    val attachedDocuments: List<ParsedDocument> = emptyList()
)

fun ChatMessage.toMessage(): Message = Message(
    id = id,
    text = content,
    isUser = isUser,
    isStreaming = isStreaming,
    isError = isError,
    timestamp = timestamp,
    imageUris = imageUris,
    toolCalls = toolCalls
)

fun Message.toChatMessage(): ChatMessage = ChatMessage(
    id = id,
    role = if (isUser) ChatMessage.ROLE_USER else ChatMessage.ROLE_ASSISTANT,
    content = text,
    timestamp = timestamp,
    isStreaming = isStreaming,
    isError = isError,
    imageUris = imageUris,
    toolCalls = toolCalls
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
    val tokensPerSecond: Float = 0f,
    val isThinkingModeEnabled: Boolean = false,
    val selectedImageUris: List<String> = emptyList(),
    // Project Omni Multimodal Additions
    val attachedDocuments: List<ParsedDocument> = emptyList(),
    val isVoiceModeActive: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val voiceState: VoiceState = VoiceState.IDLE,
    val audioAmplitude: Float = 0f,
    val liveVoiceTranscript: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engineController: EngineController,
    private val fileService: LocalFileAnalysisService,
    private val dexTradingEngine: com.deepeye.agent.core.dex.DexTradingEngine,
    private val messageDao: MessageDao,
    val voiceManager: VoiceManager,
    private val documentParser: DocumentParser,
    private val agentToolExecutor: AgentToolExecutor,
    private val knowledgeRepository: KnowledgeRepository,
    private val knowledgeRetriever: KnowledgeRetriever,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatUiState())
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _activeStreamingMessageId = MutableStateFlow<String?>(null)
    val activeStreamingMessageId: StateFlow<String?> = _activeStreamingMessageId.asStateFlow()

    private var activeGenerationJob: Job? = null

    private val baseChatState = combine(
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
    }

    val state: StateFlow<ChatUiState> = combine(
        baseChatState,
        voiceManager.voiceState,
        voiceManager.audioAmplitude,
        voiceManager.isTtsEnabled
    ) { chat, vState, amplitude, ttsOn ->
        chat.copy(
            voiceState = vState,
            audioAmplitude = amplitude,
            isTtsEnabled = ttsOn
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ChatUiState()
    )

    init {
        // Load persisted offline chat messages from Room DB
        viewModelScope.launch(Dispatchers.IO) {
            val savedMessages = messageDao.getAllMessages()
            if (savedMessages.isNotEmpty()) {
                _chatState.update { state ->
                    state.copy(messages = savedMessages.map { it.toMessage() })
                }
            }
        }

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


    fun newChat() {
        cancelGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.clearAllMessages()
        }
        _chatState.update {
            it.copy(
                prompt = "",
                messages = emptyList(),
                error = null
            )
        }
    }

    fun onPromptChange(value: String) {
        _chatState.update { it.copy(prompt = value) }
    }

    fun toggleModelPicker(show: Boolean) {
        _chatState.update { it.copy(showModelPicker = show) }
    }

    fun toggleThinkingMode() {
        _chatState.update { it.copy(isThinkingModeEnabled = !it.isThinkingModeEnabled) }
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

    fun onImagesSelected(uris: List<String>) {
        _chatState.update { it.copy(selectedImageUris = uris) }
    }

    fun removeImage(uri: String) {
        _chatState.update { state ->
            state.copy(selectedImageUris = state.selectedImageUris.filter { it != uri })
        }
    }

    // ── Local RAG Document Ingestion & Attachment Handling ───────────────────

    fun onDocumentsSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _chatState.update { it.copy(isLoading = true, error = null) }
            val parsedList = mutableListOf<ParsedDocument>()
            for (uri in uris) {
                documentParser.parseDocument(context, uri)
                    .onSuccess { doc -> parsedList.add(doc) }
                    .onFailure { err ->
                        android.util.Log.e("DeepEye-ChatVM", "Failed parsing doc: $uri", err)
                    }
            }
            _chatState.update { state ->
                state.copy(
                    isLoading = false,
                    attachedDocuments = (state.attachedDocuments + parsedList).distinctBy { it.uriString }
                )
            }
        }
    }

    fun removeDocument(docId: String) {
        _chatState.update { state ->
            state.copy(attachedDocuments = state.attachedDocuments.filter { it.id != docId })
        }
    }

    fun clearAttachedDocuments() {
        _chatState.update { it.copy(attachedDocuments = emptyList()) }
    }

    // ── Omni Voice Pipeline Controls ──────────────────────────────────────────

    fun toggleVoiceMode(active: Boolean? = null) {
        val target = active ?: !_chatState.value.isVoiceModeActive
        if (!target) {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
        }
        _chatState.update { it.copy(isVoiceModeActive = target) }
    }

    fun toggleTts() {
        val newState = !_chatState.value.isTtsEnabled
        voiceManager.setTtsEnabled(newState)
        _chatState.update { it.copy(isTtsEnabled = newState) }
    }

    fun startVoiceListening() {
        voiceManager.startListening(
            onPartial = { partial ->
                _chatState.update { it.copy(liveVoiceTranscript = partial, prompt = partial) }
            },
            onFinal = { final ->
                _chatState.update { it.copy(liveVoiceTranscript = final, prompt = final) }
                if (_chatState.value.isVoiceModeActive && final.isNotBlank()) {
                    sendStream(final)
                }
            },
            onError = { errCode ->
                android.util.Log.w("DeepEye-ChatVM", "Voice listening error: $errCode")
            }
        )
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun cancelVoiceListening() {
        voiceManager.cancelListening()
    }

    /**
     * Highly Optimized Token Streaming Pipeline.
     * Uses micro-batching (30ms frame-locked debouncing) to eliminate Compose recomposition thrashing.
     * Database I/O & memory synthesis are isolated strictly to generation completion.
     */
    fun sendStream(promptText: String? = null, onChunk: (String) -> Unit = {}) {
        val inputPrompt = promptText ?: _chatState.value.prompt
        val attachedImages = _chatState.value.selectedImageUris
        val attachedDocs = _chatState.value.attachedDocuments
        if (inputPrompt.isBlank() && attachedImages.isEmpty() && attachedDocs.isEmpty()) return

        cancelGeneration()

        val userMessage = Message(
            text = inputPrompt,
            isUser = true,
            imageUris = attachedImages,
            attachedDocuments = attachedDocs
        )
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

            val assistantMsg = Message(
                id = assistantPlaceholderId,
                text = responseText,
                isUser = false,
                dexTradeIntent = intent
            )

            _chatState.update {
                it.copy(
                    prompt = "",
                    selectedImageUris = emptyList(),
                    attachedDocuments = emptyList(),
                    error = null,
                    messages = it.messages + userMessage + assistantMsg
                )
            }

            if (_chatState.value.isTtsEnabled) {
                voiceManager.speakSentence(responseText)
            }

            viewModelScope.launch(Dispatchers.IO) {
                messageDao.insertMessage(userMessage.toChatMessage())
                messageDao.insertMessage(assistantMsg.toChatMessage())
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
                selectedImageUris = emptyList(),
                attachedDocuments = emptyList(),
                error = null,
                messages = it.messages + userMessage + assistantPlaceholder
            )
        }
        _isGenerating.value = true
        _activeStreamingMessageId.value = assistantPlaceholderId

        // Persist User Message to Room DB immediately
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(userMessage.toChatMessage())
        }

        activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            runReActGeneration(
                inputPrompt = inputPrompt,
                attachedDocs = attachedDocs,
                assistantPlaceholderId = assistantPlaceholderId,
                onChunk = onChunk
            )
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        if (text.length < 5) return -1
        if (text.contains("<think>") && !text.contains("</think>")) return -1
        for (i in 0 until text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                if (i + 1 < text.length && text[i + 1].isWhitespace()) {
                    return i
                } else if (i == text.length - 1 && text.length > 25) {
                    return i
                }
            }
        }
        return -1
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
        _activeStreamingMessageId.value = null
        voiceManager.stopSpeaking()
        _chatState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.isStreaming) msg.copy(isStreaming = false) else msg
            }
            state.copy(messages = updatedMessages)
        }
    }
    private fun buildReActSystemPrompt(): String = buildString {
        appendLine("<system>")
        appendLine("You are DeepEye Sovereign Omni-Agent powered by Nous Hermes 3 / Colibri 14B.")
        appendLine("You solve user requests autonomously using Step-by-Step Reasoning (<think>...</think>) and Tool Invocations (<tool_call>...</tool_call>).")
        appendLine()
        appendLine("Available Tools:")
        appendLine("• knowledge_search: Search local SQLite FTS5 / Vector Knowledge base. Arguments: {\"query\": \"keywords\"}")
        appendLine("• calculator: Perform mathematical arithmetic. Arguments: {\"expression\": \"25 * 4.5\"}")
        appendLine("• device_info: Inspect battery, RAM, CPU thermal, and hardware state. Arguments: {}")
        appendLine("• file_reader: Read text from local device storage. Arguments: {\"path\": \"/path/to/file\"}")
        appendLine("• dex_quote: Get crypto swap quotes and slippage estimate. Arguments: {\"tokenIn\": \"SOL\", \"tokenOut\": \"USDC\", \"amount\": 1.0}")
        appendLine()
        appendLine("Tool Calling Protocol:")
        appendLine("To execute a tool, output:")
        appendLine("<tool_call>")
        appendLine("{\"name\": \"tool_name\", \"arguments\": {\"param\": \"value\"}}")
        appendLine("</tool_call>")
        appendLine("When you invoke a tool, wait for the <tool_result> observation before synthesizing the final response.")
        appendLine("</system>")
    }

    private fun sanitizeOutput(raw: String): String {
        return raw
            .replace("<|im_end|>", "")
            .replace("<end_of_turn>", "")
            .replace("<|eot_id|>", "")
            .replace("</s>", "")
            .replace("<|end|>", "")
    }
    private suspend fun executeSingleReActTurn(
        prompt: String,
        accumulatedToolCalls: List<ToolCallModel>,
        assistantPlaceholderId: String,
        onChunk: (String) -> Unit
    ): Pair<String, Float> {
        val stepBuffer = StringBuilder(512)
        val ttsSentenceBuffer = StringBuilder(256)
        var lastFlushTime = 0L
        val flushIntervalMs = 30L
        var tokenCount = 0
        var firstTokenTime = 0L
        var liveTps = 0f

        fun flushToUi(force: Boolean = false, isFinished: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastFlushTime >= flushIntervalMs) {
                lastFlushTime = now
                if (firstTokenTime > 0L && tokenCount > 1) {
                    val elapsedSec = (now - firstTokenTime) / 1000.0f
                    if (elapsedSec > 0.02f) {
                        liveTps = ((tokenCount - 1) / elapsedSec).coerceAtLeast(1f)
                    }
                }
                val rawText = stepBuffer.toString()
                val parsed = ToolCallParser.parse(rawText, isStreaming = !isFinished)
                val merged = (accumulatedToolCalls + parsed.toolCalls).distinctBy { it.id }
                val displayText = if (isFinished) sanitizeOutput(parsed.cleanText).trimEnd() else sanitizeOutput(parsed.cleanText)

                _chatState.update { state ->
                    val updated = state.messages.map { msg ->
                        if (msg.id == assistantPlaceholderId) {
                            msg.copy(text = displayText, toolCalls = merged, isStreaming = !isFinished)
                        } else msg
                    }
                    state.copy(messages = updated, tokensPerSecond = if (liveTps > 0f) liveTps else state.tokensPerSecond)
                }
            }
        }

        engineController.streamChat(prompt).collect { chunk ->
            if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis()
            tokenCount++
            onChunk(chunk)
            stepBuffer.append(chunk)
            ttsSentenceBuffer.append(chunk)

            if (_chatState.value.isTtsEnabled) {
                var boundaryIdx = findSentenceBoundary(ttsSentenceBuffer.toString())
                while (boundaryIdx != -1) {
                    val s = ttsSentenceBuffer.substring(0, boundaryIdx + 1).trim()
                    ttsSentenceBuffer.delete(0, boundaryIdx + 1)
                    if (s.isNotBlank()) voiceManager.speakSentence(s)
                    boundaryIdx = findSentenceBoundary(ttsSentenceBuffer.toString())
                }
            }
            flushToUi(force = false, isFinished = false)
        }
        flushToUi(force = true, isFinished = true)

        if (_chatState.value.isTtsEnabled && ttsSentenceBuffer.isNotBlank()) {
            voiceManager.speakSentence(ttsSentenceBuffer.toString().trim())
        }
        return Pair(stepBuffer.toString(), liveTps)
    }

    private suspend fun runReActGeneration(
        inputPrompt: String,
        attachedDocs: List<ParsedDocument>,
        assistantPlaceholderId: String,
        onChunk: (String) -> Unit
    ) {
        val accumulatedToolCalls = mutableListOf<ToolCallModel>()
        var loopCount = 0
        val maxLoops = 3 // Hard cap (max 3 loop iterations) to prevent infinite loops
        var isConversationDone = false
        var finalCleanedResponse = ""
        var liveTps = 0f

        try {
            val promptWithRag = documentParser.formatContextPrompt(inputPrompt, attachedDocs)

            val systemPrompt = buildReActSystemPrompt()
            var currentEnginePrompt = if (_chatState.value.isThinkingModeEnabled) {
                "$systemPrompt\n\n<system>\nYou are in deep reasoning mode. Use <think> and </think> tags to show your chain of thought.</system>\n\nUser: $promptWithRag\n\nAssistant:"
            } else {
                "$systemPrompt\n\nUser: $promptWithRag\n\nAssistant:"
            }

            while (!isConversationDone && loopCount < maxLoops) {
                loopCount++
                val (rawStepOutput, tps) = executeSingleReActTurn(
                    prompt = currentEnginePrompt,
                    accumulatedToolCalls = accumulatedToolCalls,
                    assistantPlaceholderId = assistantPlaceholderId,
                    onChunk = onChunk
                )
                if (tps > 0f) liveTps = tps

                val parsedStep = ToolCallParser.parse(rawStepOutput, isStreaming = false)

                if (parsedStep.toolCalls.isNotEmpty()) {
                    for (tc in parsedStep.toolCalls) {
                        val executingTc = tc.copy(status = ToolCallStatus.EXECUTING)
                        accumulatedToolCalls.removeAll { it.id == tc.id || it.name == tc.name }
                        accumulatedToolCalls.add(executingTc)

                        _chatState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantPlaceholderId) msg.copy(toolCalls = accumulatedToolCalls.toList(), isStreaming = true) else msg
                            }
                            state.copy(messages = updated)
                        }

                        val toolStartTime = System.currentTimeMillis()
                        val toolResult = agentToolExecutor.execute(tc.name, tc.arguments)
                        val durationMs = System.currentTimeMillis() - toolStartTime

                        val completedTc = tc.copy(
                            result = toolResult,
                            status = ToolCallStatus.SUCCESS,
                            durationMs = durationMs
                        )
                        accumulatedToolCalls.removeAll { it.id == tc.id || it.name == tc.name }
                        accumulatedToolCalls.add(completedTc)

                        _chatState.update { state ->
                            val updated = state.messages.map { msg ->
                                if (msg.id == assistantPlaceholderId) msg.copy(toolCalls = accumulatedToolCalls.toList(), isStreaming = true) else msg
                            }
                            state.copy(messages = updated)
                        }

                        currentEnginePrompt += "\n<tool_call>\n{\"name\": \"${tc.name}\", \"arguments\": ${tc.arguments}}\n</tool_call>\n<tool_result>\n$toolResult\n</tool_result>\n\nAssistant:"
                    }
                } else {
                    isConversationDone = true
                    finalCleanedResponse = sanitizeOutput(parsedStep.cleanText).trimEnd()
                }
            }

            val nativeStats = engineController.getPerformanceStats()
            val finalTps = nativeStats?.tokensPerSec?.toFloat()?.takeIf { it > 0f } ?: liveTps
            _chatState.update { it.copy(tokensPerSecond = finalTps) }

            val completedAssistantMsg = ChatMessage.assistant(
                id = assistantPlaceholderId,
                content = if (finalCleanedResponse.isNotBlank()) finalCleanedResponse else "Task completed successfully.",
                isStreaming = false,
                isError = false,
                toolCalls = accumulatedToolCalls.toList()
            )
            messageDao.insertMessage(completedAssistantMsg)
        } catch (e: Throwable) {
            val errorMsg = e.message ?: "Generation failed"
            _chatState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == assistantPlaceholderId) {
                        msg.copy(
                            text = if (msg.text.isBlank()) "Error: $errorMsg" else msg.text,
                            isStreaming = false,
                            isError = true,
                            toolCalls = accumulatedToolCalls.map { 
                                if (it.status == ToolCallStatus.EXECUTING) it.copy(status = ToolCallStatus.FAILED, result = "Aborted: $errorMsg") else it
                            }
                        )
                    } else msg
                }
                state.copy(messages = updatedMessages, error = errorMsg)
            }

            val failedAssistantMsg = ChatMessage.assistant(
                id = assistantPlaceholderId,
                content = "Error: $errorMsg",
                isStreaming = false,
                isError = true,
                toolCalls = accumulatedToolCalls.toList()
            )
            messageDao.insertMessage(failedAssistantMsg)
        } finally {
            _isGenerating.value = false
            _activeStreamingMessageId.value = null
        }
    }



    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }

    fun sendPrompt() {
        sendStream()
    }

    fun analyzeFile(uri: Uri) = viewModelScope.launch {
        val userMsg = Message(text = "Analyzing file: ${uri.lastPathSegment}", isUser = true)
        _chatState.update {
            it.copy(
                isLoading = true,
                error = null,
                messages = it.messages + userMsg
            )
        }
        withContext(Dispatchers.IO) { messageDao.insertMessage(userMsg.toChatMessage()) }
        runCatching {
            fileService.analyze(uri)
        }.onSuccess { result ->
            val assistantMsg = Message(text = result, isUser = false, modelStatus = _chatState.value.modelStatus)
            _chatState.update {
                it.copy(
                    isLoading = false,
                    messages = it.messages + assistantMsg
                )
            }
            withContext(Dispatchers.IO) { messageDao.insertMessage(assistantMsg.toChatMessage()) }
        }.onFailure { e ->
            _chatState.update { it.copy(error = e.message, isLoading = false) }
        }
    }

    fun deepDebugFile(uri: Uri) = viewModelScope.launch {
        val userMsg = Message(text = "Deep Debugging file: ${uri.lastPathSegment}", isUser = true)
        _chatState.update {
            it.copy(
                isLoading = true,
                error = null,
                messages = it.messages + userMsg
            )
        }
        withContext(Dispatchers.IO) { messageDao.insertMessage(userMsg.toChatMessage()) }
        runCatching {
            fileService.deepDebugLocally(uri)
        }.onSuccess { result ->
            val assistantMsg = Message(text = result, isUser = false, modelStatus = ModelStatus.LOCAL_ACTIVE)
            _chatState.update {
                it.copy(
                    isLoading = false,
                    modelStatus = ModelStatus.LOCAL_ACTIVE,
                    messages = it.messages + assistantMsg
                )
            }
            withContext(Dispatchers.IO) { messageDao.insertMessage(assistantMsg.toChatMessage()) }
        }.onFailure { e ->
            _chatState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
