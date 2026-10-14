package com.wakeup.alarm

import android.app.Application
import com.wakeup.alarm.ring.AlarmNotifications

class WakeUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.createChannel(this)
    }
}
