package com.wakeup.alarm

import android.app.Application
import com.wakeup.alarm.live.LiveUpdates
import com.wakeup.alarm.ring.AlarmNotifications
import com.wakeup.alarm.ring.TimerService

class WakeUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.createChannel(this)
        TimerService.createChannel(this)
        LiveUpdates.createChannel(this)
    }
}
