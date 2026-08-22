package com.deepeye.agent.ui.promptlab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PromptLabVariantState(
    val name: String,
    val systemPrompt: String,
    val temperature: Float,
    val topK: Int,
    val maxTokens: Int,
    val outputText: String = "",
    val isStreaming: Boolean = false,
    val ttftMs: Long = 0,
    val tokensPerSec: Float = 0f
)

data class PromptLabUiState(
    val sharedPrompt: String = "Analyze the reentrancy attack surface on standard ERC-20 transferFrom implementations.",
    val selectedSchema: StructuredSchemaPreset = StructuredSchemaPreset.FREEFORM,
    val activeTab: Int = 0,
    val variantA: PromptLabVariantState = PromptLabVariantState(
        name = "Variant A (Deterministic)",
        systemPrompt = "You are a senior security researcher. Output strictly verified technical invariants.",
        temperature = 0.2f,
        topK = 20,
        maxTokens = 512
    ),
    val variantB: PromptLabVariantState = PromptLabVariantState(
        name = "Variant B (Creative / Broad)",
        systemPrompt = "You are an exploratory AI assistant. Provide comprehensive exploit scenarios and edge-case theories.",
        temperature = 0.8f,
        topK = 50,
        maxTokens = 1024
    ),
    val isRunningDual: Boolean = false,
    val error: String? = null,
    val presetPrompts: List<Pair<String, String>> = listOf(
        "ERC-20 Audit" to "Analyze the reentrancy attack surface on standard ERC-20 transferFrom implementations.",
        "Zero-Day Scan" to "Scan memory allocation patterns in C++ pointer dereferencing for buffer overflows.",
        "JSON Extractor" to "Extract DEX pool liquidity reserves, token decimals, and slippage tolerance from swap logs.",
        "Agent Plan" to "Synthesize multi-step autonomous execution plan for smart contract fuzz testing."
    )
)

@HiltViewModel
class PromptLabViewModel @Inject constructor(
    private val engineController: EngineController
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptLabUiState())
    val uiState: StateFlow<PromptLabUiState> = _uiState

    fun onSharedPromptChange(prompt: String) {
        _uiState.update { it.copy(sharedPrompt = prompt) }
    }

    fun onSchemaChange(schema: StructuredSchemaPreset) {
        _uiState.update { it.copy(selectedSchema = schema) }
    }

    fun onTabChange(tab: Int) {
        _uiState.update { it.copy(activeTab = tab) }
    }
    
    fun saveCurrentPromptAsPreset() {
        val currentPrompt = _uiState.value.sharedPrompt
        if (currentPrompt.isBlank()) return
        
        // Use a simple name based on first few words
        val name = currentPrompt.take(15) + "..."
        _uiState.update { state ->
            val updatedPresets = state.presetPrompts.toMutableList()
            // Don't add duplicate text
            if (updatedPresets.none { it.second == currentPrompt }) {
                updatedPresets.add(name to currentPrompt)
            }
            state.copy(presetPrompts = updatedPresets)
        }
    }

    fun updateVariantA(
        systemPrompt: String? = null,
        temperature: Float? = null,
        topK: Int? = null,
        maxTokens: Int? = null
    ) {
        _uiState.update { state ->
            val v = state.variantA
            state.copy(
                variantA = v.copy(
                    systemPrompt = systemPrompt ?: v.systemPrompt,
                    temperature = temperature ?: v.temperature,
                    topK = topK ?: v.topK,
                    maxTokens = maxTokens ?: v.maxTokens
                )
            )
        }
    }

    fun updateVariantB(
        systemPrompt: String? = null,
        temperature: Float? = null,
        topK: Int? = null,
        maxTokens: Int? = null
    ) {
        _uiState.update { state ->
            val v = state.variantB
            state.copy(
                variantB = v.copy(
                    systemPrompt = systemPrompt ?: v.systemPrompt,
                    temperature = temperature ?: v.temperature,
                    topK = topK ?: v.topK,
                    maxTokens = maxTokens ?: v.maxTokens
                )
            )
        }
    }

    fun runBenchmark() {
        val state = _uiState.value
        if (state.isRunningDual) {
            _uiState.update { it.copy(isRunningDual = false) }
            return
        }

        _uiState.update {
            it.copy(
                isRunningDual = true,
                variantA = it.variantA.copy(outputText = "", isStreaming = true, ttftMs = 0, tokensPerSec = 0f),
                variantB = it.variantB.copy(outputText = "", isStreaming = true, ttftMs = 0, tokensPerSec = 0f)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Run Variant A
            runVariant(isVariantA = true)
            // Wait briefly then run Variant B
            if (_uiState.value.isRunningDual) {
                runVariant(isVariantA = false)
            }
            
            _uiState.update { it.copy(isRunningDual = false) }
        }
    }

    private suspend fun runVariant(isVariantA: Boolean) {
        val state = _uiState.value
        val variant = if (isVariantA) state.variantA else state.variantB
        val schemaPrompt = if (state.selectedSchema != StructuredSchemaPreset.FREEFORM) {
            "\n\nOutput Format required: ${state.selectedSchema.schemaDescription}"
        } else ""
        
        val fullPrompt = "<system>\n${variant.systemPrompt}\n</system>\n\nUser: ${state.sharedPrompt}$schemaPrompt"
        
        var firstTokenTime = 0L
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        var tps = 0f
        var currentText = ""

        val onChunk: (String) -> Unit = { chunk ->
            if (firstTokenTime == 0L) {
                firstTokenTime = System.currentTimeMillis()
            }
            tokenCount++
            currentText += chunk
            
            val elapsedSec = (System.currentTimeMillis() - firstTokenTime) / 1000f
            if (elapsedSec > 0.05f && tokenCount > 1) {
                tps = ((tokenCount - 1) / elapsedSec).coerceAtLeast(1f)
            }

            _uiState.update { st ->
                if (isVariantA) {
                    st.copy(variantA = st.variantA.copy(
                        outputText = currentText,
                        ttftMs = if (firstTokenTime > 0) firstTokenTime - startTime else 0,
                        tokensPerSec = tps
                    ))
                } else {
                    st.copy(variantB = st.variantB.copy(
                        outputText = currentText,
                        ttftMs = if (firstTokenTime > 0) firstTokenTime - startTime else 0,
                        tokensPerSec = tps
                    ))
                }
            }
        }

        try {
            engineController.executeChatStream(fullPrompt, onChunk)
            
            val nativeStats = engineController.getPerformanceStats()
            if (nativeStats != null && nativeStats.tokensPerSec > 0) {
                tps = nativeStats.tokensPerSec.toFloat()
            }
        } catch (e: Exception) {
            currentText += "\n[Error: ${e.message}]"
        } finally {
            _uiState.update { st ->
                if (isVariantA) {
                    st.copy(variantA = st.variantA.copy(
                        isStreaming = false,
                        outputText = currentText,
                        tokensPerSec = tps
                    ))
                } else {
                    st.copy(variantB = st.variantB.copy(
                        isStreaming = false,
                        outputText = currentText,
                        tokensPerSec = tps
                    ))
                }
            }
        }
    }
}
