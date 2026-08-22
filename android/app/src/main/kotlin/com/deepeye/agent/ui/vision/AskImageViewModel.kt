package com.deepeye.agent.ui.vision

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.DeepEyeAgentEngine
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class AskImageUiState(
    val selectedImageUri: Uri? = null,
    val prompt: String = "",
    val response: String = "",
    val isStreaming: Boolean = false,
    val isModelReady: Boolean = false,
    val error: String? = null,
    val tokensPerSecond: Float = 0f
)

@HiltViewModel
class AskImageViewModel @Inject constructor(
    application: Application,
    private val engineController: EngineController
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AskImageUiState())
    val uiState: StateFlow<AskImageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            engineController.engineStatus.collect { status ->
                _uiState.update { it.copy(isModelReady = status.modelStatus == ModelStatus.LOCAL_ACTIVE) }
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        _uiState.update { it.copy(selectedImageUri = uri, error = null) }
    }

    fun onImageCaptured(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val tempFile = File(context.cacheDir, "captured_image_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                _uiState.update { it.copy(selectedImageUri = Uri.fromFile(tempFile), error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save captured image") }
            }
        }
    }

    fun onPromptChange(text: String) {
        _uiState.update { it.copy(prompt = text) }
    }

    fun clearImage() {
        _uiState.update { it.copy(selectedImageUri = null, response = "", error = null) }
    }

    fun analyzeImage() {
        val currentState = _uiState.value
        if (!currentState.isModelReady) {
            _uiState.update { it.copy(error = "Engine not ready") }
            return
        }

        val uri = currentState.selectedImageUri
        if (uri == null) {
            _uiState.update { it.copy(error = "No image selected") }
            return
        }

        _uiState.update { it.copy(isStreaming = true, response = "", error = null, tokensPerSecond = 0f) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val tempFile = File(context.cacheDir, "temp_image_analysis_${System.currentTimeMillis()}.jpg")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val imagePath = tempFile.absolutePath
                val startTime = System.currentTimeMillis()
                var tokens = 0

                engineController.executeChatStream(
                    "Analyze this image at path: $imagePath\n\nUser question: ${currentState.prompt}"
                ) { chunk ->
                    tokens++
                    val timeSec = (System.currentTimeMillis() - startTime) / 1000f
                    val tps = if (timeSec > 0) tokens / timeSec else 0f

                    _uiState.update {
                        it.copy(
                            response = it.response + chunk,
                            tokensPerSecond = tps
                        )
                    }
                }

                _uiState.update { it.copy(isStreaming = false) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isStreaming = false, error = e.localizedMessage ?: "Unknown error") }
            }
        }
    }
}
