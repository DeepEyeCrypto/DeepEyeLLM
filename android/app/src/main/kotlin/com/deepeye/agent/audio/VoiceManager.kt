package com.deepeye.agent.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * Omni Voice Manager: Unified STT (Speech-to-Text) and TTS (Text-to-Speech) pipeline.
 */
@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var speechRecognizer: SpeechRecognizer? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((Int) -> Unit)? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript: StateFlow<String> = _finalTranscript.asStateFlow()

    private var tts: TextToSpeech? = null
    private val _isTtsInitialized = MutableStateFlow(false)
    val isTtsInitialized: StateFlow<Boolean> = _isTtsInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val utteranceCounter = AtomicInteger(0)
    private val pendingUtteranceCount = AtomicInteger(0)
    private val activeUtterances = ConcurrentLinkedQueue<String>()
    private var amplitudeJob: Job? = null

    init {
        initializeTts()
        startAmplitudeLoop()
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts ?: return
            try {
                val result = ttsEngine.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsEngine.language = Locale.getDefault()
                }
                ttsEngine.setPitch(1.0f)
                ttsEngine.setSpeechRate(1.05f)

                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                        _voiceState.update { current ->
                            if (current != VoiceState.LISTENING) VoiceState.SPEAKING else current
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        activeUtterances.remove(utteranceId)
                        val remaining = pendingUtteranceCount.decrementAndGet()
                        if (remaining <= 0) {
                            pendingUtteranceCount.set(0)
                            _isSpeaking.value = false
                            _voiceState.update { current ->
                                if (current == VoiceState.SPEAKING) VoiceState.IDLE else current
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        activeUtterances.remove(utteranceId)
                        val remaining = pendingUtteranceCount.decrementAndGet()
                        if (remaining <= 0) {
                            pendingUtteranceCount.set(0)
                            _isSpeaking.value = false
                            _voiceState.update { current ->
                                if (current == VoiceState.SPEAKING) VoiceState.IDLE else current
                            }
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        onError(utteranceId)
                    }
                })

                _isTtsInitialized.value = true
                Log.d(TAG, "TextToSpeech engine initialized successfully.")
            } catch (e: Throwable) {
                Log.e(TAG, "Error configuring TextToSpeech engine params", e)
            }
        } else {
            Log.w(TAG, "TextToSpeech init returned non-success code: $status")
        }
    }

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)


    fun startListening(
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit = {},
        onError: (Int) -> Unit = {}
    ) {
        mainHandler.post {
            try {
                stopSpeaking()
                cleanupRecognizer()

                this.onPartialCallback = onPartial
                this.onFinalCallback = onFinal
                this.onErrorCallback = onError

                _partialTranscript.value = ""
                _finalTranscript.value = ""

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _voiceState.value = VoiceState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                        _voiceState.value = VoiceState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioAmplitude.value = norm
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _voiceState.update { current ->
                            if (current == VoiceState.LISTENING) VoiceState.THINKING else current
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "Speech recognition error code=$error (${errorLabel(error)})")
                        _isListening.value = false
                        _voiceState.value = VoiceState.IDLE
                        onErrorCallback?.invoke(error)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty().trim()
                        _isListening.value = false
                        if (text.isNotBlank()) {
                            _finalTranscript.value = text
                            _partialTranscript.value = text
                            onFinalCallback?.invoke(text)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty().trim()
                        if (text.isNotBlank()) {
                            _partialTranscript.value = text
                            onPartialCallback?.invoke(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.startListening(intent)
                _isListening.value = true
                _voiceState.value = VoiceState.LISTENING
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start speech recognition", e)
                _isListening.value = false
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Throwable) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            } finally {
                _isListening.value = false
                _voiceState.update { current ->
                    if (current == VoiceState.LISTENING) VoiceState.THINKING else current
                }
            }
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Throwable) {
                Log.w(TAG, "Error canceling SpeechRecognizer", e)
            } finally {
                _isListening.value = false
                _voiceState.value = VoiceState.IDLE
            }
        }
    }
    fun setTtsEnabled(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    fun speakSentence(rawText: String) {
        if (!_isTtsEnabled.value || !_isTtsInitialized.value) return
        val cleaned = sanitizeTextForSpeech(rawText)
        if (cleaned.isBlank()) return

        mainHandler.post {
            try {
                val utteranceId = "deepeye_utt_${utteranceCounter.incrementAndGet()}"
                activeUtterances.add(utteranceId)
                pendingUtteranceCount.incrementAndGet()

                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }

                tts?.speak(cleaned, TextToSpeech.QUEUE_ADD, params, utteranceId)
                _isSpeaking.value = true
                _voiceState.update { current ->
                    if (current != VoiceState.LISTENING) VoiceState.SPEAKING else current
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error executing TTS speech for chunk: $cleaned", e)
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                tts?.stop()
                activeUtterances.clear()
                pendingUtteranceCount.set(0)
            } catch (e: Throwable) {
                Log.w(TAG, "Error stopping TTS", e)
            } finally {
                _isSpeaking.value = false
                _voiceState.update { current ->
                    if (current == VoiceState.SPEAKING) VoiceState.IDLE else current
                }
            }
        }
    }

    fun setVoiceState(state: VoiceState) {
        _voiceState.value = state
    }

    private fun startAmplitudeLoop() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (isActive) {
                when (_voiceState.value) {
                    VoiceState.SPEAKING -> {
                        phase += 0.35
                        val base = (Math.sin(phase) * 0.4 + Math.cos(phase * 2.3) * 0.3 + 0.5).toFloat()
                        _audioAmplitude.value = base.coerceIn(0.15f, 0.95f)
                    }
                    VoiceState.THINKING -> {
                        phase += 0.12
                        val base = ((Math.sin(phase) + 1.0) * 0.25).toFloat()
                        _audioAmplitude.value = base.coerceIn(0.08f, 0.55f)
                    }
                    VoiceState.LISTENING -> {
                        if (_audioAmplitude.value > 0.05f) {
                            _audioAmplitude.value = (_audioAmplitude.value * 0.85f)
                        }
                    }
                    VoiceState.IDLE -> {
                        if (_audioAmplitude.value > 0.02f) {
                            _audioAmplitude.value = (_audioAmplitude.value * 0.8f)
                        } else {
                            _audioAmplitude.value = 0f
                        }
                    }
                }
                delay(40L)
            }
        }
    }

    private fun sanitizeTextForSpeech(input: String): String {
        return input
            .replace(Regex("<think>[\\s\\S]*?</think>"), " ")
            .replace(Regex("<context>[\\s\\S]*?</context>"), " ")
            .replace(Regex("<.*?>"), " ")
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
            .replace(Regex("`[^`]*`"), " code snippet ")
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            .replace(Regex("[#*_~>\\[\\]]"), "")
            .replace(Regex("\\bhttps?://\\S+"), " web link ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun destroy() {
        amplitudeJob?.cancel()
        cleanupRecognizer()
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Throwable) {
                Log.w(TAG, "Error shutting down TTS", e)
            }
            tts = null
        }
    }

    companion object {
        private const val TAG = "DeepEye-VoiceManager"

        fun errorLabel(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection required"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
            else -> "Speech recognition error ($errorCode)"
        }
    }


    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "Error destroying recognizer", e)
        }
        speechRecognizer = null
    }

    private fun initializeTts() {
        mainHandler.post {
            try {
                tts = TextToSpeech(context, this)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize TextToSpeech engine", e)
            }
        }
    }
}
