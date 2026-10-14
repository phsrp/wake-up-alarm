package com.wakeup.alarm.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import com.wakeup.alarm.scheduling.Timers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Handles Android's standard "set a timer" request, which is what Google Assistant and Gemini send
 * for "set a 5 minute timer". Like Google's own clock, it creates the timer and starts it straight away.
 */
class SetTimerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    private fun handle(request: Intent) {
        if (request.action != AlarmClock.ACTION_SET_TIMER) return

        val seconds = request.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
        if (seconds !in 1..MAX_SECONDS) {
            // No usable length: just show the timers so it can be set by hand.
            showTimers()
            return
        }

        val label = request.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        runBlocking(Dispatchers.IO) {
            Timers.create(applicationContext, seconds, label, startNow = true)
        }

        Toast.makeText(this, "Timer set for ${formatCountdown(seconds * 1000L)}", Toast.LENGTH_LONG).show()
        if (!request.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) showTimers()
    }

    private fun showTimers() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(AlarmClock.ACTION_SHOW_TIMERS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private companion object {
        const val MAX_SECONDS = 24 * 60 * 60
    }
}
