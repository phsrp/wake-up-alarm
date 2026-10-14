package com.wakeup.alarm.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wakeup.alarm.R
import com.wakeup.alarm.ui.MainActivity

/**
 * Rings when a timer runs out. Unlike an alarm there is nothing to solve: tap Stop in the
 * notification. It gives up on its own after a few minutes so it never rings all day.
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var beep: FallbackBeep? = null
    private var ringing = false
    private var finishedCount = 0

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            return START_NOT_STICKY
        }
        finishedCount += 1
        val notification = buildNotification(finishedCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!ringing) {
            ringing = true
            startSound()
            startVibration()
            handler.postDelayed({ stopRinging() }, AUTO_STOP_MS)
        }
        return START_NOT_STICKY
    }

    private fun startSound() {
        val uris = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in uris) {
            try {
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setAudioAttributes(attributes)
                mediaPlayer.setDataSource(this, uri)
                mediaPlayer.isLooping = true
                mediaPlayer.prepare()
                mediaPlayer.start()
                player = mediaPlayer
                return
            } catch (e: Exception) {
                Log.w(TAG, "Could not play $uri", e)
            }
        }
        beep = FallbackBeep(attributes).also { it.start() }
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
        runCatching {
            vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0), attributes)
        }
    }

    private fun buildNotification(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java)
                .setAction(AlarmClock.ACTION_SHOW_TIMERS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            11,
            Intent(this, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(if (count > 1) "$count timers finished" else "Timer finished")
            .setContentText("Tap Stop to silence it")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun stopRinging() {
        ringing = false
        finishedCount = 0
        handler.removeCallbacksAndMessages(null)
        player?.runCatching {
            stop()
            release()
        }
        player = null
        beep?.stop()
        beep = null
        runCatching { vibrator().cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TimerService"
        private const val CHANNEL_ID = "timer_finished"
        private const val NOTIFICATION_ID = 4243
        private const val ACTION_STOP = "com.wakeup.alarm.STOP_TIMER"
        private const val AUTO_STOP_MS = 5 * 60 * 1000L

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Finished timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while a timer that has run out is ringing"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context, timerId: Long) {
            val intent = Intent(context, TimerService::class.java).putExtra("timer_id", timerId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the timer service", e)
            }
        }
    }
}
