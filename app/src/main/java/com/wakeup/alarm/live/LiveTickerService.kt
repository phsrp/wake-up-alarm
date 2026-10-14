package com.wakeup.alarm.live

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.R
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.TimerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs only while a timer or the stopwatch is counting. Once a second it refreshes the live
 * notifications so the capsule shows the time. It stops itself when nothing is running.
 */
class LiveTickerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticking: Job? = null
    private var timers: List<TimerEntity> = emptyList()
    private val posted = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() promises a notification straight away.
        val placeholder = NotificationCompat.Builder(this, LiveUpdates.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Wake Up")
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                LiveUpdates.FOREGROUND_NOTIFICATION_ID,
                placeholder,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(LiveUpdates.FOREGROUND_NOTIFICATION_ID, placeholder)
        }

        if (ticking == null) {
            ticking = scope.launch {
                val dao = AppDatabase.get(this@LiveTickerService).dao()
                timers = dao.getTimers()
                launch { dao.observeTimers().collect { timers = it } }
                while (isActive) {
                    val keepGoing = try {
                        tick()
                    } catch (e: Exception) {
                        Log.w(TAG, "Live update tick failed", e)
                        true
                    }
                    if (!keepGoing) {
                        stopSelf()
                        break
                    }
                    delay(1000 - System.currentTimeMillis() % 1000)
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Returns false when nothing is running any more. */
    private fun tick(): Boolean {
        val now = System.currentTimeMillis()
        val prefs = AppPrefs(this)
        val manager = getSystemService(NotificationManager::class.java)

        class Item(val id: Int, val build: () -> android.app.Notification)

        val items = buildList {
            if (prefs.stopwatchRunning) {
                add(Item(LiveUpdates.STOPWATCH_NOTIFICATION_ID) { LiveUpdates.stopwatchNotification(this@LiveTickerService, prefs, now) })
            }
            timers
                .filter { it.state == TimerEntity.STATE_RUNNING && it.endAtMillis > now }
                .sortedBy { it.endAtMillis }
                .forEach { timer ->
                    add(Item(LiveUpdates.TIMER_NOTIFICATION_BASE + timer.id.toInt()) {
                        LiveUpdates.timerNotification(this@LiveTickerService, timer, now)
                    })
                }
        }
        if (items.isEmpty()) return false

        // The first item is also the one holding the foreground slot; the rest get their own notification.
        val wanted = mutableSetOf<Int>()
        items.forEachIndexed { index, item ->
            val id = if (index == 0) LiveUpdates.FOREGROUND_NOTIFICATION_ID else item.id
            wanted += id
            manager.notify(id, item.build())
        }
        for (stale in posted - wanted) manager.cancel(stale)
        posted.clear()
        posted += wanted
        return true
    }

    override fun onDestroy() {
        val manager = getSystemService(NotificationManager::class.java)
        for (id in posted) manager.cancel(id)
        manager.cancel(LiveUpdates.FOREGROUND_NOTIFICATION_ID)
        manager.cancel(LiveUpdates.STOPWATCH_NOTIFICATION_ID)
        timers.forEach { manager.cancel(LiveUpdates.TIMER_NOTIFICATION_BASE + it.id.toInt()) }
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LiveTickerService"
    }
}
