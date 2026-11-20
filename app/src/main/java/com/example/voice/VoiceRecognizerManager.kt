package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.local.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceRecognitionState {
    data object Idle : VoiceRecognitionState
    data class Listening(
        val rmsLevel: Float = 0f,
        val partialText: String = "",
        val languageCode: String = "en-US"
    ) : VoiceRecognitionState

    data object Processing : VoiceRecognitionState
    data class Error(val message: String, val isUnavailable: Boolean = false) : VoiceRecognitionState
}

class VoiceRecognizerManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null

    private val _state = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val state: StateFlow<VoiceRecognitionState> = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        language: AssistantLanguage,
        onResult: (String) -> Unit
    ) {
        mainHandler.post {
            if (!isAvailable()) {
                _state.value = VoiceRecognitionState.Error(
                    message = "Speech recognition service is unavailable on this device. You can still type commands in Chat.",
                    isUnavailable = true
                )
                return@post
            }

            onFinalResultCallback = onResult
            val localeTag = when (language) {
                AssistantLanguage.BANGLA -> "bn-BD"
                AssistantLanguage.ENGLISH -> "en-US"
                AssistantLanguage.AUTO -> "en-US"
            }

            try {
                recognizer?.destroy()
                recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createListener(localeTag))
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    if (language == AssistantLanguage.AUTO) {
                        putExtra(
                            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                            arrayListOf("en-US", "bn-BD")
                        )
                    }
                }

                _state.value = VoiceRecognitionState.Listening(
                    rmsLevel = 0.12f,
                    partialText = "",
                    languageCode = localeTag
                )
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _state.value = VoiceRecognitionState.Error(
                    message = "Could not start microphone recognition: ${e.localizedMessage}"
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                recognizer?.stopListening()
                if (_state.value is VoiceRecognitionState.Listening) {
                    _state.value = VoiceRecognitionState.Processing
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SpeechRecognizer", e)
                _state.value = VoiceRecognitionState.Idle
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
            } catch (_: Exception) {
            }
            _state.value = VoiceRecognitionState.Idle
        }
    }

    fun resetToIdle() {
        _state.value = VoiceRecognitionState.Idle
    }

    private fun createListener(localeTag: String) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceRecognitionState.Listening(
                rmsLevel = 0.15f,
                partialText = "",
                languageCode = localeTag
            )
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value
            if (current is VoiceRecognitionState.Listening) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                _state.value = current.copy(rmsLevel = normalized)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (_state.value is VoiceRecognitionState.Listening) {
                _state.value = VoiceRecognitionState.Processing
            }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio recording error."
                SpeechRecognizer.ERROR_CLIENT -> "Speech client cancelled."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network required for cloud speech recognition."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly and try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please wait a moment."
                SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out with no speech detected."
                else -> "Speech recognition error ($error)."
            }
            Log.w(TAG, "SpeechRecognizer error=$error: $message")
            _state.value = VoiceRecognitionState.Error(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                _state.value = VoiceRecognitionState.Processing
                onFinalResultCallback?.invoke(best)
            } else {
                _state.value = VoiceRecognitionState.Error("No speech detected.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            val current = _state.value
            if (current is VoiceRecognitionState.Listening && partial.isNotBlank()) {
                _state.value = current.copy(partialText = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private companion object {
        const val TAG = "VoiceRecognizerMgr"
    }
}
