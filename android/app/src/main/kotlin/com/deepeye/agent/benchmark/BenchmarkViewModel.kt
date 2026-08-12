package com.deepeye.agent.benchmark

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

data class BenchmarkUiState(
    val isRunning: Boolean = false,
    val currentPromptName: String = "",
    val progress: Float = 0f,
    val lastResult: AggregateBenchmarkResult? = null,
    val exportStatus: String? = null
)

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val runner: LLMBenchmarkRunner
) : ViewModel() {

    private val _uiState = MutableStateFlow(BenchmarkUiState())
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    fun runBenchmark() = viewModelScope.launch {
        _uiState.update { it.copy(isRunning = true, progress = 0f, exportStatus = null) }
        val result = runner.runSuite { status, progress ->
            _uiState.update { it.copy(currentPromptName = status, progress = progress) }
        }
        _uiState.update { it.copy(isRunning = false, progress = 1f, lastResult = result) }
    }

    fun exportResultsCsv() = viewModelScope.launch(Dispatchers.IO) {
        val result = _uiState.value.lastResult ?: return@launch
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "DeepEyeLLM")
            if (!appDir.exists()) appDir.mkdirs()

            val csvFile = File(appDir, "benchmark_${System.currentTimeMillis()}.csv")
            FileWriter(csvFile).use { writer ->
                writer.append("Model,Backend,Prompt,TTFT_ms,Tokens_Per_Sec,Total_Tokens,Total_Time_ms,Peak_Memory_MB\n")
                result.promptResults.forEach { p ->
                    writer.append("${result.modelName},${result.backendName},\"${p.promptName}\",${p.ttftMs},${p.tokensPerSec},${p.totalTokens},${p.totalTimeMs},${p.peakMemoryMb}\n")
                }
            }
            _uiState.update { it.copy(exportStatus = "Exported CSV to Download/DeepEyeLLM/${csvFile.name}") }
        } catch (e: Exception) {
            _uiState.update { it.copy(exportStatus = "Export failed: ${e.message}") }
        }
    }
}
