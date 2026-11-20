package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object WakeWordEventBus {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _wakeEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val wakeEvents: SharedFlow<String> = _wakeEvents.asSharedFlow()

    internal fun setRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    internal fun emitWakeTrigger(commandAfterWake: String) {
        _wakeEvents.tryEmit(commandAfterWake)
    }
}

/**
 * Opt-in foreground service for "Hey Sifat" wake-word detection.
 * Never runs secretly: always displays a persistent system notification with a Stop button.
 */
class WakeWordForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var active = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!active) {
            active = true
            WakeWordEventBus.setRunning(true)
            startWakeListeningCycle()
        }

        return START_STICKY
    }

    private fun startWakeListeningCycle() {
        mainHandler.post {
            if (!active || !SpeechRecognizer.isRecognitionAvailable(this)) return@post
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}

                        override fun onError(error: Int) {
                            if (active && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                mainHandler.postDelayed({ startWakeListeningCycle() }, 900L)
                            } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                stopSelf()
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val heard = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            checkForWakePhrase(heard)
                            if (active) {
                                mainHandler.postDelayed({ startWakeListeningCycle() }, 650L)
                            }
                        }
                    })
                }
                val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(listenIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Wake-word listener cycle failed", e)
            }
        }
    }

    private fun checkForWakePhrase(heard: String) {
        val lower = heard.lowercase()
        val wakePhrases = listOf("hey sifat", "hi sifat", "ok sifat", "হেই সিফাত", "হ্যালো সিফাত", "সিফাত")
        val matched = wakePhrases.firstOrNull { lower.contains(it) } ?: return
        val trailingCommand = lower.substringAfter(matched).trim()
        WakeWordEventBus.emitWakeTrigger(trailingCommand)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wake_word_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.wake_word_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sifat Agent Wake-Word Active")
            .setContentText("Listening for \"Hey Sifat\" (Tap Stop anytime)")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Listening", stopIntent)
            .build()
    }

    override fun onDestroy() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        WakeWordEventBus.setRunning(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "sifat_wake_word_channel"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.example.voice.STOP_WAKE_WORD"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordForegroundService::class.java))
        }
    }
}
