package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class VoiceTtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _banglaVoiceSupported = MutableStateFlow(true)
    val banglaVoiceSupported: StateFlow<Boolean> = _banglaVoiceSupported.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val bnLocale = Locale.Builder().setLanguage("bn").setRegion("BD").build()
                val bnSupport = tts?.isLanguageAvailable(bnLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                _banglaVoiceSupported.value = bnSupport >= TextToSpeech.LANG_AVAILABLE

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status=$status")
            }
        }
    }

    fun speak(text: String, speechRate: Float = 1.0f, languageHint: String? = null) {
        val cleanText = text.trim()
        if (!isInitialized || cleanText.isBlank()) return

        val isBangla = containsBengaliScript(cleanText) ||
            languageHint?.startsWith("bn", ignoreCase = true) == true

        val targetLocale = if (isBangla && _banglaVoiceSupported.value) {
            Locale.Builder().setLanguage("bn").setRegion("BD").build()
        } else {
            Locale.US
        }

        tts?.language = targetLocale
        tts?.setSpeechRate(speechRate.coerceIn(0.6f, 1.8f))
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }

    private fun containsBengaliScript(text: String): Boolean =
        text.any { ch -> ch in '\u0980'..'\u09FF' }

    private companion object {
        const val TAG = "VoiceTtsManager"
    }
}
