package com.deepeye.agent.ui.file

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.deepeye.agent.analysis.FileAnalysisService
import com.deepeye.agent.policy.LocalSafetyContext
import com.deepeye.agent.policy.PolicyCheckLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FileAnalysisUiState(
    val currentFileUri: Uri? = null,
    val isAnalyzing: Boolean = false,
    val isLoading: Boolean = false,
    val analysisResult: String? = null,
    val error: String? = null,
    val fileName: String = "",
    val fileType: String = "",
    val fileSize: String = "",
    val fileSource: String = "",
    val summary: String = "",
    val confidence: String = "",
    val findings: List<FindingUi> = emptyList(),
    val suggestions: List<String> = emptyList()
)

data class FindingUi(
    val title: String,
    val description: String,
    val severity: String,
    val location: String
)

@HiltViewModel
class FileAnalysisViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val fileService: FileAnalysisService,
    private val policyCheckLayer: PolicyCheckLayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileAnalysisUiState())
    val uiState: StateFlow<FileAnalysisUiState> = _uiState.asStateFlow()

    fun onExplainFile(uri: Uri) = viewModelScope.launch {
        _uiState.update { it.copy(currentFileUri = uri, isAnalyzing = true, isLoading = true, error = null, analysisResult = null) }
        
        // Extract real file metadata from ContentResolver
        val contentResolver = appContext.contentResolver
        val realMimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val realFileName = uri.lastPathSegment ?: "unknown"
        var realFileSize = 0L
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                realFileSize = pfd.statSize
            }
        }
        
        val context = LocalSafetyContext(
            fileMimeType = realMimeType,
            fileName = realFileName,
            fileSizeBytes = realFileSize,
            offlineMode = true
        )
        
        val decision = policyCheckLayer.evaluate(context)
        if (!decision.allowed) {
            _uiState.update { 
                it.copy(
                    isAnalyzing = false, 
                    isLoading = false,
                    error = "Policy blocked analysis: ${decision.reason}"
                ) 
            }
            return@launch
        }

        runCatching {
            fileService.analyze(uri)
        }.onSuccess { result ->
            _uiState.update { 
                it.copy(
                    isAnalyzing = false,
                    isLoading = false,
                    analysisResult = result,
                    fileName = realFileName,
                    fileType = realMimeType,
                    fileSize = formatFileSize(realFileSize),
                    summary = if (result.isNotBlank()) "Analysis Complete" else "No findings",
                    confidence = if (result.length > 100) "High" else "Medium",
                    findings = parseFindings(result),
                    suggestions = parseSuggestions(result)
                ) 
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isAnalyzing = false, isLoading = false, error = e.message ?: "Analysis failed") }
        }
    }

    fun requestDeepDebugLocally(uri: Uri) = viewModelScope.launch {
         _uiState.update { it.copy(isAnalyzing = true, isLoading = true, error = null) }
        runCatching {
            fileService.deepDebugLocally(uri)
        }.onSuccess { result ->
             _uiState.update { 
                it.copy(
                    isAnalyzing = false,
                    isLoading = false,
                    analysisResult = "Deep Debug Results:\n\n$result"
                ) 
            }
        }.onFailure { e ->
             _uiState.update { it.copy(isAnalyzing = false, isLoading = false, error = e.message) }
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun parseFindings(result: String): List<FindingUi> {
        if (result.isBlank()) return emptyList()
        // Parse engine output into structured findings
        return result.lines()
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .mapIndexed { index, line ->
                FindingUi(
                    title = "Finding ${index + 1}",
                    description = line.removePrefix("- ").removePrefix("* ").trim(),
                    severity = "Info",
                    location = "Line ${index + 1}"
                )
            }
            .ifEmpty {
                listOf(FindingUi("Summary", result.take(200), "Info", "Global"))
            }
    }

    private fun parseSuggestions(result: String): List<String> {
        if (result.isBlank()) return emptyList()
        return result.lines()
            .filter { it.contains("suggest", ignoreCase = true) || it.contains("recommend", ignoreCase = true) }
            .map { it.trim() }
            .ifEmpty { emptyList() }
    }
}
