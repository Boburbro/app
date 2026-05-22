package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.FocusRepository
import com.example.data.FocusSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Random

class TimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var repository: FocusRepository? = null

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository(AppDatabase.getDatabase(this))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION, 1500)
                val type = intent.getStringExtra(EXTRA_TYPE) ?: "FOCUS"
                val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1).let { if (it == -1) null else it }
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
                start(duration, type, taskId, taskTitle)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopAndClear()
            ACTION_TOGGLE_NOISE -> toggleNoise()
        }
        return START_NOT_STICKY
    }

    private fun start(durationSeconds: Int, type: String, taskId: Int?, taskTitle: String?) {
        serviceScope.launch {
            // Setup static flows
            Companion.totalDurationSeconds.value = durationSeconds
            Companion.timeLeftSeconds.value = durationSeconds
            Companion.currentType.value = type
            Companion.currentTaskId.value = taskId
            Companion.currentTaskTitle.value = taskTitle
            Companion.isRunning.value = true
            Companion.distractions.value = 0

            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            startCountdown()
        }
    }

    private fun pause() {
        Companion.isRunning.value = false
        timerJob?.cancel()
        updateNotification()
    }

    private fun resume() {
        Companion.isRunning.value = true
        startCountdown()
        updateNotification()
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && timeLeftSeconds.value > 0) {
                delay(1000)
                timeLeftSeconds.value -= 1
                updateNotification()
            }
            if (timeLeftSeconds.value == 0) {
                onTimerFinished()
            }
        }
    }

    private suspend fun onTimerFinished() {
        Companion.isRunning.value = false
        timerJob?.cancel()
        vibratePhone()

        // Persistent save
        val startT = System.currentTimeMillis() - (totalDurationSeconds.value * 1000)
        val endT = System.currentTimeMillis()
        val session = FocusSession(
            taskId = currentTaskId.value,
            startTime = startT,
            endTime = endT,
            durationSeconds = totalDurationSeconds.value,
            completed = true,
            type = currentType.value,
            distractions = distractions.value
        )
        withContext(Dispatchers.IO) {
            repository?.insertSession(session)
        }

        // Notify complete via notification and state
        Companion.timeLeftSeconds.value = 0
        updateNotification()
        
        // Stop noise if playing
        stopNoise()
        Companion.isNoiseEnabled.value = false
    }

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        }
    }

    private fun toggleNoise() {
        if (isNoisePlaying) {
            stopNoise()
            Companion.isNoiseEnabled.value = false
        } else {
            startNoise()
            Companion.isNoiseEnabled.value = true
        }
        updateNotification()
    }

    private var audioTrack: AudioTrack? = null
    private var isNoisePlaying = false
    private var noiseJob: Job? = null

    private fun startNoise() {
        if (isNoisePlaying) return
        isNoisePlaying = true
        val sampleRate = 32000
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack = track
        track.play()

        noiseJob = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(minBufferSize / 2)
            val random = Random()
            var lastValue = 0f
            while (isNoisePlaying && isActive) {
                for (i in buffer.indices) {
                    val rawNoise = random.nextFloat() * 2f - 1f
                    // Low pass filter to make it brownian/rain-like and comforting
                    val filtered = 0.92f * lastValue + 0.08f * rawNoise
                    lastValue = filtered
                    // Scale to comfortable soft volume
                    buffer[i] = (filtered * 10000).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
            try {
                track.stop()
            } catch (e: Exception) {}
            track.release()
        }
    }

    private fun stopNoise() {
        isNoisePlaying = false
        noiseJob?.cancel()
        audioTrack = null
    }

    private fun stopAndClear() {
        stopNoise()
        Companion.isNoiseEnabled.value = false
        Companion.isRunning.value = false
        timerJob?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        stopAndClear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun buildNotification(): Notification {
        val titleText = when (currentType.value) {
            "FOCUS" -> "Fokus: " + (currentTaskTitle.value ?: "Qayd etilmagan")
            "SHORT_BREAK" -> "Kichik Tanaffus"
            else -> "Uzoq Tanaffus"
        }
        val contentText = if (timeLeftSeconds.value == 0) {
            "Sessiya tugadi!"
        } else {
            "${formatTime(timeLeftSeconds.value)} qoldi"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification buttons
        val pauseResumeAction = if (isRunning.value) {
            val intent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
            val pi = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pi)
        } else {
            val intent = Intent(this, TimerService::class.java).apply { action = ACTION_RESUME }
            val pi = PendingIntent.getService(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Resume", pi)
        }

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System icon fallback
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning.value && timeLeftSeconds.value > 0)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusNest Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "FocusTimer countdown notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "focus_timer_channel"
        const val NOTIFICATION_ID = 4851

        // Actions
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_PAUSE = "com.example.service.action.PAUSE"
        const val ACTION_RESUME = "com.example.service.action.RESUME"
        const val ACTION_STOP = "com.example.service.action.STOP"
        const val ACTION_TOGGLE_NOISE = "com.example.service.action.TOGGLE_NOISE"

        // Extras
        const val EXTRA_DURATION = "com.example.service.extra.DURATION"
        const val EXTRA_TYPE = "com.example.service.extra.TYPE"
        const val EXTRA_TASK_ID = "com.example.service.extra.TASK_ID"
        const val EXTRA_TASK_TITLE = "com.example.service.extra.TASK_TITLE"

        // Static Flows for UI reactive binding
        val timeLeftSeconds = MutableStateFlow(1500)
        val totalDurationSeconds = MutableStateFlow(1500)
        val isRunning = MutableStateFlow(false)
        val currentType = MutableStateFlow("FOCUS")
        val currentTaskId = MutableStateFlow<Int?>(null)
        val currentTaskTitle = MutableStateFlow<String?>(null)
        val distractions = MutableStateFlow(0)
        val isNoiseEnabled = MutableStateFlow(false)

        fun serviceIntent(context: Context, action: String): Intent {
            return Intent(context, TimerService::class.java).apply {
                this.action = action
            }
        }
    }
}
