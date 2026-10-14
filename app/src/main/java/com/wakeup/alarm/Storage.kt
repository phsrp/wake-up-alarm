package com.wakeup.alarm

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * The database and settings live in device-protected storage, so alarms can be
 * rescheduled and can ring even after an overnight reboot, before the phone is unlocked.
 */
object Storage {
    fun context(base: Context): Context {
        val app = base.applicationContext
        return ContextCompat.createDeviceProtectedStorageContext(app) ?: app
    }
}

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
}

const val NO_ALARM = Long.MIN_VALUE
const val TEST_ALARM_ID = -1L

class AppPrefs(context: Context) {
    private val prefs = Storage.context(context)
        .getSharedPreferences("wakeup_prefs", Context.MODE_PRIVATE)

    var difficulty: Difficulty
        get() = runCatching { Difficulty.valueOf(prefs.getString("difficulty", null) ?: "") }
            .getOrDefault(Difficulty.MEDIUM)
        set(value) = prefs.edit().putString("difficulty", value.name).apply()

    /** How many problems in a row must be solved to stop an alarm. */
    var problemCount: Int
        get() = prefs.getInt("problem_count", 3)
        set(value) = prefs.edit().putInt("problem_count", value.coerceIn(1, 10)).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    /** Id of the alarm that is ringing right now, or [NO_ALARM]. Survives the service being killed. */
    var ringingAlarmId: Long
        get() = prefs.getLong("ringing_alarm_id", NO_ALARM)
        set(value) = prefs.edit().putLong("ringing_alarm_id", value).commit().let { }

    var ringingLabel: String
        get() = prefs.getString("ringing_label", "") ?: ""
        set(value) = prefs.edit().putString("ringing_label", value).apply()

    // ----- stopwatch (kept here so it keeps counting when the app is closed) -----

    var stopwatchRunning: Boolean
        get() = prefs.getBoolean("sw_running", false)
        set(value) = prefs.edit().putBoolean("sw_running", value).apply()

    /** Wall-clock moment the stopwatch was last started. */
    var stopwatchStartedAt: Long
        get() = prefs.getLong("sw_started_at", 0L)
        set(value) = prefs.edit().putLong("sw_started_at", value).apply()

    /** Time counted before the last start (the stopwatch was paused). */
    var stopwatchAccumulated: Long
        get() = prefs.getLong("sw_accumulated", 0L)
        set(value) = prefs.edit().putLong("sw_accumulated", value).apply()

    /** Total elapsed time at each lap, in milliseconds, oldest first. */
    var stopwatchLaps: List<Long>
        get() = (prefs.getString("sw_laps", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        set(value) = prefs.edit().putString("sw_laps", value.joinToString(",")).apply()

    fun stopwatchElapsed(now: Long = System.currentTimeMillis()): Long =
        stopwatchAccumulated + if (stopwatchRunning) (now - stopwatchStartedAt).coerceAtLeast(0) else 0L
}
