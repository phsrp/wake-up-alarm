package com.wakeup.alarm.ring

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.NO_ALARM
import com.wakeup.alarm.TEST_ALARM_ID
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.scheduling.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays the alarm. Runs as a foreground service so Android keeps it alive, keeps the volume
 * at maximum, and keeps bringing the ringing screen back until the math problems are solved.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: AppPrefs
    private lateinit var audioManager: AudioManager

    private var player: MediaPlayer? = null
    private var beep: FallbackBeep? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    private var ringing = false
    private var currentId = NO_ALARM
    private var stopAtElapsed = 0L // 0 means ring until dismissed
    private var lastLaunch = 0L

    // Only used for test rings, which carry their own settings instead of reading a saved alarm.
    private var testSoundUri: String? = null
    private var testDurationMinutes = 0
    private var testLabel: String? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val watchdog = object : Runnable {
        override fun run() {
            if (!ringing) return
            forceMaxVolume()
            val now = SystemClock.elapsedRealtime()
            if (stopAtElapsed != 0L && now >= stopAtElapsed) {
                stopRinging()
                return
            }
            if (!RingActivity.isVisible && now - lastLaunch > RELAUNCH_INTERVAL_MS) {
                launchRingScreen()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        audioManager = getSystemService(AudioManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            return START_NOT_STICKY
        }

        // A null intent means Android restarted us after killing the process: resume the saved ring.
        val requested = intent?.getLongExtra(EXTRA_ALARM_ID, NO_ALARM) ?: NO_ALARM
        val alarmId = if (requested != NO_ALARM) requested else prefs.ringingAlarmId

        if (intent != null && !ringing) {
            testSoundUri = intent.getStringExtra(EXTRA_SOUND_URI)
            testDurationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 0)
            testLabel = intent.getStringExtra(EXTRA_LABEL)
        }

        // startForegroundService() promises a notification within seconds, so always post one first.
        enterForeground(prefs.ringingLabel)

        if (alarmId == NO_ALARM) {
            stopRinging()
            return START_NOT_STICKY
        }
        if (!ringing) beginRinging(alarmId)
        return START_STICKY
    }

    private fun enterForeground(label: String) {
        val notification = AlarmNotifications.build(this, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AlarmNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun beginRinging(alarmId: Long) {
        ringing = true
        currentId = alarmId
        prefs.ringingAlarmId = alarmId

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakeup:ringing")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

        requestAudioFocus()
        forceMaxVolume()
        startVibration()
        launchRingScreen()
        handler.postDelayed(watchdog, 1000)

        scope.launch {
            val alarm = try {
                if (alarmId == TEST_ALARM_ID) null
                else withContext(Dispatchers.IO) { AppDatabase.get(this@AlarmService).dao().getAlarm(alarmId) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not read alarm $alarmId, ringing with defaults", e)
                null
            }

            val isTest = alarmId == TEST_ALARM_ID
            val label = if (isTest) testLabel?.ifBlank { null } ?: "Test alarm" else alarm?.label.orEmpty()
            prefs.ringingLabel = label
            getSystemService(NotificationManager::class.java)
                .notify(AlarmNotifications.NOTIFICATION_ID, AlarmNotifications.build(this@AlarmService, label))

            val minutes = if (isTest) testDurationMinutes else alarm?.durationMinutes ?: 0
            if (minutes > 0) stopAtElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L

            startSound(if (isTest) testSoundUri else alarm?.soundUri)
        }
    }

    private fun startSound(soundUri: String?) {
        val candidates = listOfNotNull(
            soundUri?.let { Uri.parse(it) },
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setAudioAttributes(audioAttributes)
                mediaPlayer.setDataSource(this, uri)
                mediaPlayer.isLooping = true
                mediaPlayer.setVolume(1f, 1f)
                mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mediaPlayer.setOnErrorListener { _, _, _ ->
                    Log.e(TAG, "MediaPlayer error, switching to the built-in beep")
                    startBeep()
                    true
                }
                mediaPlayer.prepare()
                mediaPlayer.start()
                player = mediaPlayer
                return
            } catch (e: Exception) {
                Log.w(TAG, "Could not play $uri", e)
            }
        }
        // Nothing playable (for example a deleted file before first unlock): never ring silently.
        startBeep()
    }

    private fun startBeep() {
        if (beep != null) return
        player?.runCatching { release() }
        player = null
        beep = FallbackBeep(audioAttributes).also { it.start() }
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun forceMaxVolume() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != max) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not raise the alarm volume", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0)
            vibrator().vibrate(effect, audioAttributes)
        } catch (e: Exception) {
            Log.w(TAG, "Could not vibrate", e)
        }
    }

    private fun launchRingScreen() {
        lastLaunch = SystemClock.elapsedRealtime()
        try {
            startActivity(AlarmNotifications.ringIntent(this))
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the ringing screen directly", e)
        }
    }

    private fun stopRinging() {
        ringing = false
        currentId = NO_ALARM
        prefs.ringingAlarmId = NO_ALARM
        prefs.ringingLabel = ""
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(AlarmNotifications.NOTIFICATION_ID)
        stopSelf()
    }

    private fun releaseResources() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching {
            stop()
            release()
        }
        player = null
        beep?.stop()
        beep = null
        runCatching { vibrator().cancel() }
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    /** Swiped away from recents: ask the system to bring the ringing straight back. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (ringing) AlarmScheduler.scheduleRingRestart(this, currentId)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseResources()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_STOP = "com.wakeup.alarm.STOP_RINGING"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_SOUND_URI = "sound_uri"
        private const val EXTRA_DURATION_MINUTES = "duration_minutes"
        private const val EXTRA_LABEL = "label"
        private const val RELAUNCH_INTERVAL_MS = 2_500L
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        fun start(
            context: Context,
            alarmId: Long,
            soundUri: String? = null,
            durationMinutes: Int = 0,
            label: String? = null,
        ) {
            val intent = Intent(context, AlarmService::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SOUND_URI, soundUri)
                .putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
                .putExtra(EXTRA_LABEL, label)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the ringing service", e)
                val prefs = AppPrefs(context)
                prefs.ringingAlarmId = alarmId
                context.getSystemService(NotificationManager::class.java)
                    .notify(AlarmNotifications.NOTIFICATION_ID, AlarmNotifications.build(context, ""))
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }
}
