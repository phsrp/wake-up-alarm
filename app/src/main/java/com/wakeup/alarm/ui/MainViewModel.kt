package com.wakeup.alarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.FolderEntity
import com.wakeup.alarm.data.TimerEntity
import com.wakeup.alarm.scheduling.AlarmScheduler
import com.wakeup.alarm.scheduling.Timers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).dao()

    val alarms: StateFlow<List<AlarmEntity>> =
        dao.observeAlarms().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        dao.observeFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val timers: StateFlow<List<TimerEntity>> =
        dao.observeTimers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Runs a database change, then makes the system alarm list match the database again. */
    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }

    private fun timerChange(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    // ----- alarms and folders -----

    fun setAlarmEnabled(id: Long, enabled: Boolean) = change { dao.setAlarmEnabled(id, enabled) }

    fun saveAlarm(alarm: AlarmEntity) = change {
        if (alarm.id == 0L) dao.insertAlarm(alarm) else dao.updateAlarm(alarm)
    }

    fun deleteAlarm(alarm: AlarmEntity) = change {
        AlarmScheduler.cancel(getApplication(), alarm.id)
        dao.deleteAlarm(alarm)
    }

    fun deleteAlarms(ids: List<Long>) = change {
        ids.forEach { AlarmScheduler.cancel(getApplication(), it) }
        dao.deleteAlarms(ids)
    }

    fun reorderAlarms(idsInOrder: List<Long>) = change { dao.applyAlarmOrder(idsInOrder) }

    fun createFolder(name: String) = change { dao.insertFolder(FolderEntity(name = name.trim())) }

    fun renameFolder(id: Long, name: String) = change { dao.renameFolder(id, name.trim()) }

    fun setFolderEnabled(id: Long, enabled: Boolean) = change { dao.setFolderEnabled(id, enabled) }

    fun deleteFolder(id: Long) = change { dao.deleteFolderKeepAlarms(id) }

    /** Moves a folder one place up (-1) or down (+1) in the list. */
    fun moveFolder(id: Long, direction: Int) = change {
        val order = folders.value.map { it.id }.toMutableList()
        val from = order.indexOf(id)
        val to = from + direction
        if (from < 0 || to !in order.indices) return@change
        order.add(to, order.removeAt(from))
        dao.applyFolderOrder(order)
    }

    // ----- timers -----

    fun addTimer(durationSeconds: Int, label: String) = timerChange {
        Timers.create(getApplication(), durationSeconds, label, startNow = false)
    }

    fun startTimer(id: Long) = timerChange { Timers.start(getApplication(), id) }

    fun pauseTimer(id: Long) = timerChange { Timers.pause(getApplication(), id) }

    fun resetTimer(id: Long) = timerChange { Timers.reset(getApplication(), id) }

    fun deleteTimers(ids: List<Long>) = timerChange { Timers.delete(getApplication(), ids) }

    fun reorderTimers(idsInOrder: List<Long>) = timerChange { dao.applyTimerOrder(idsInOrder) }
}
