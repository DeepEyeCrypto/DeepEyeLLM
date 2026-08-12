package com.deepeye.agent.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.domain.EngineController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class DexBookmark(
    val name: String,
    val url: String,
    val iconEmoji: String
)

@Immutable
data class BraveBrowserUiState(
    val currentUrl: String = "https://dexscreener.com",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val isDeepResearchActive: Boolean = false,
    val researchQuery: String = "",
    val aiAnalysisResult: String? = null,
    val bookmarks: List<DexBookmark> = listOf(
        DexBookmark("DexScreener", "https://dexscreener.com", "📈"),
        DexBookmark("Uniswap", "https://app.uniswap.org", "🦄"),
        DexBookmark("Raydium", "https://raydium.io/swap", "⚡"),
        DexBookmark("Jupiter", "https://jup.ag", "🪐"),
        DexBookmark("Birdeye", "https://birdeye.so", "🦅"),
        DexBookmark("Pump.fun", "https://pump.fun", "🚀")
    )
)

@HiltViewModel
class BraveBrowserViewModel @Inject constructor(
    private val engineController: EngineController
) : ViewModel() {

    private val _uiState = MutableStateFlow(BraveBrowserUiState())
    val uiState: StateFlow<BraveBrowserUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = if (formattedUrl.contains(".")) {
                "https://$formattedUrl"
            } else {
                "https://dexscreener.com/search?q=$formattedUrl"
            }
        }
        _uiState.value = _uiState.value.copy(currentUrl = formattedUrl)
    }

    fun setPageLoading(loading: Boolean, progress: Int = 0) {
        _uiState.value = _uiState.value.copy(isLoading = loading, progress = progress)
    }

    fun toggleDeepResearchOverlay() {
        val current = _uiState.value.isDeepResearchActive
        _uiState.value = _uiState.value.copy(isDeepResearchActive = !current)
    }

    fun updateResearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(researchQuery = query)
    }

    fun runDeepCryptoResearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, aiAnalysisResult = null)
            val prompt = """
                [Brave Web3 DEX AI Research Agent]
                Perform deep crypto research for pair / token / URL: '${_uiState.value.currentUrl}'
                Query: '$query'
                
                Evaluate:
                1. Token Contract Security & Liquidity Lock Status
                2. Volume, Price Momentum & Smart Money Inflow
                3. Risk Rating (1-10) & Trading Recommendation
            """.trimIndent()
            
            val response = try {
                engineController.executeChat(prompt).second
            } catch (e: Exception) {
                "Analysis failed: ${e.localizedMessage ?: "Engine not ready. Load a model in Settings → Manage Models."}"
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                aiAnalysisResult = response,
                isDeepResearchActive = true
            )
        }
    }
}
