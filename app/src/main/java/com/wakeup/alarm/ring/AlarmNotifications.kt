package com.wakeup.alarm.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wakeup.alarm.R

object AlarmNotifications {
    const val CHANNEL_ID = "alarm_ringing"
    const val NOTIFICATION_ID = 4242

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Ringing alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while an alarm is ringing"
            setSound(null, null) // the service plays the sound itself, on the alarm stream
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun ringIntent(context: Context): Intent =
        Intent(context, RingActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )

    fun build(context: Context, label: String): Notification {
        val openRing = PendingIntent.getActivity(
            context,
            1,
            ringIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label.ifBlank { "Alarm" })
            .setContentText("Solve the math problems to stop it")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openRing)
            .setFullScreenIntent(openRing, true)
            .build()
    }
}
