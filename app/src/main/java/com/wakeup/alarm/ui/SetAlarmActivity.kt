package com.wakeup.alarm.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.scheduling.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Handles Android's standard "set an alarm" request, which is what Google Assistant and Gemini
 * send. It only ever creates alarms. Android's dismiss and snooze requests are deliberately not
 * handled, so the math problems cannot be skipped by voice.
 */
class SetAlarmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    private fun handle(request: Intent) {
        if (request.action != AlarmClock.ACTION_SET_ALARM) return

        val hour = request.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
        val minute = request.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
        if (hour !in 0..23 || minute !in 0..59) {
            // No usable time was given: just show the app so the user can set it by hand.
            startActivity(Intent(this, MainActivity::class.java))
            return
        }

        val alarm = AlarmEntity(
            hour = hour,
            minute = minute,
            repeatDays = daysToMask(request.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty()),
            label = request.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty().trim(),
        )

        // A tiny local database write; done before the screen closes so it cannot be lost.
        runBlocking(Dispatchers.IO) {
            AppDatabase.get(applicationContext).dao().insertAlarm(alarm)
            AlarmScheduler.rescheduleAll(applicationContext)
        }

        val (time, suffix) = formatTime(hour, minute, DateFormat.is24HourFormat(this))
        Toast.makeText(this, "Alarm set for $time $suffix".trim(), Toast.LENGTH_LONG).show()

        if (!request.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    /** Calendar days (Sunday = 1 ... Saturday = 7) to this app's bit mask (Monday = bit 0 ... Sunday = bit 6). */
    private fun daysToMask(days: List<Int>): Int {
        var mask = 0
        for (day in days) {
            if (day !in Calendar.SUNDAY..Calendar.SATURDAY) continue
            mask = mask or (1 shl ((day + 5) % 7))
        }
        return mask
    }
}
