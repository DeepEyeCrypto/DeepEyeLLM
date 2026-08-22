package com.deepeye.agent.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Thin wrapper around Android's on-device [SpeechRecognizer].
 *
 * The system speech recognition service performs on-device speech-to-text
 * (works fully offline when a language pack is downloaded), so voice input
 * never leaves the device — matching the app's air-gapped/private philosophy.
 *
 * Lifecycle:
 *   val stt = OnDeviceSpeechToText(context)
 *   stt.startListening(partial = { .. }, final = { .. }, onError = { .. })
 *   stt.stopListening()
 *   stt.destroy()   // from onDispose
 */
class OnDeviceSpeechToText(private val context: Context) {

    interface Callbacks {
        /** Called with live partial hypotheses while the user is still speaking. */
        fun onPartial(text: String) = Unit
        /** Called with the final, stabilized transcription when recognition ends. */
        fun onFinal(text: String) = Unit
        /** Called when recognition stops/interrupts without a usable result. */
        fun onEndOfSpeech() = Unit
        /** signalled with a stable error code from [SpeechRecognizer]. */
        fun onError(errorCode: Int) = Unit
    }

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null

    private var callbacks: Callbacks? = null

    val isAvailable: Boolean get() = recognizer != null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            callbacks?.onEndOfSpeech()
        }
        override fun onError(error: Int) {
            Log.w(TAG, "SpeechRecognizer error code=$error")
            callbacks?.onError(error)
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) callbacks?.onFinal(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) callbacks?.onPartial(text)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        recognizer?.setRecognitionListener(listener)
    }

    /** Begins listening. Returns false if on-device recognition is unavailable. */
    fun startListening(callbacks: Callbacks): Boolean {
        val rec = recognizer ?: return false
        this.callbacks = callbacks
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        rec.startListening(intent)
        return true
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        callbacks = null
    }

    companion object {
        private const val TAG = "DeepEye-STT"

        /** Maps a [SpeechRecognizer] error code to a human-readable label for snackbars/UI. */
        fun errorLabel(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "audio error"
            SpeechRecognizer.ERROR_CLIENT -> "client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission denied"
            SpeechRecognizer.ERROR_NETWORK -> "network required"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "no speech detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "recognizer error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech"
            else -> "code $errorCode"
        }
    }
}
