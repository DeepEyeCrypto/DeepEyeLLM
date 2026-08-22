package com.deepeye.agent.ui.audio

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class AudioScribeUiState(
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val audioFilePath: String? = null,
    val transcript: String = "",
    val translatedText: String? = null,
    val targetLanguage: String = "English",
    val isTranscribing: Boolean = false,
    val isTranslating: Boolean = false,
    val isModelReady: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AudioScribeViewModel @Inject constructor(
    private val engineController: EngineController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioScribeUiState())
    val uiState: StateFlow<AudioScribeUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var transcriptionJob: Job? = null
    private var translationJob: Job? = null

    init {
        viewModelScope.launch {
            engineController.engineStatus.collect { status ->
                _uiState.update { it.copy(isModelReady = status.modelStatus == ModelStatus.LOCAL_ACTIVE) }
            }
        }
    }

    fun startRecording() {
        try {
            val cacheFile = File(context.cacheDir, "scribe_recording_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setOutputFile(cacheFile.absolutePath)
                prepare()
                start()
            }

            _uiState.update { 
                it.copy(
                    isRecording = true, 
                    audioFilePath = cacheFile.absolutePath,
                    recordingDurationMs = 0L,
                    transcript = "",
                    translatedText = null,
                    error = null
                ) 
            }
            startTimer()
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to start recording: ${e.message}", isRecording = false) }
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            stopTimer()
            _uiState.update { it.copy(isRecording = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to stop recording: ${e.message}", isRecording = false) }
        }
    }
    
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                stopTimer()
                _uiState.update { it.copy(isRecording = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to pause recording: ${e.message}") }
            }
        }
    }
    
    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                startTimer()
                _uiState.update { it.copy(isRecording = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to resume recording: ${e.message}") }
            }
        }
    }

    fun importAudioFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open stream")
                val cacheFile = File(context.cacheDir, "imported_audio_${System.currentTimeMillis()}.m4a")
                val outputStream = FileOutputStream(cacheFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                _uiState.update { 
                    it.copy(
                        audioFilePath = cacheFile.absolutePath,
                        recordingDurationMs = 0L,
                        transcript = "",
                        translatedText = null,
                        error = null
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to import file: ${e.message}") }
            }
        }
    }

    fun transcribeAudio() {
        if (!uiState.value.isModelReady) return
        
        _uiState.update { it.copy(isTranscribing = true, transcript = "", translatedText = null, error = null) }
        
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                engineController.executeChatStream(
                    prompt = "Transcribe the following audio recording accurately. Output only the transcription text, nothing else."
                ) { chunk ->
                    _uiState.update { it.copy(transcript = it.transcript + chunk) }
                }
                _uiState.update { it.copy(isTranscribing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Transcription failed: ${e.message}", isTranscribing = false) }
            }
        }
    }

    fun translateTranscript(targetLang: String) {
        if (!uiState.value.isModelReady || uiState.value.transcript.isBlank()) return
        
        _uiState.update { it.copy(isTranslating = true, targetLanguage = targetLang, translatedText = "", error = null) }
        
        translationJob?.cancel()
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                engineController.executeChatStream(
                    prompt = "Translate the following text to $targetLang. Output only the translation:\n\n${uiState.value.transcript}"
                ) { chunk ->
                    val currentTranslation = _uiState.value.translatedText ?: ""
                    _uiState.update { it.copy(translatedText = currentTranslation + chunk) }
                }
                _uiState.update { it.copy(isTranslating = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Translation failed: ${e.message}", isTranslating = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearState() {
        stopRecording()
        _uiState.update { it.copy(audioFilePath = null, transcript = "", translatedText = null, recordingDurationMs = 0L) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                _uiState.update { it.copy(recordingDurationMs = it.recordingDurationMs + 100) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}
