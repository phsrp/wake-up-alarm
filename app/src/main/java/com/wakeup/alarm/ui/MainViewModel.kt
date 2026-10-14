package com.wakeup.alarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.FolderEntity
import com.wakeup.alarm.scheduling.AlarmScheduler
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

    /** Runs a database change, then makes the system alarm list match the database again. */
    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }

    fun setAlarmEnabled(id: Long, enabled: Boolean) = change { dao.setAlarmEnabled(id, enabled) }

    fun saveAlarm(alarm: AlarmEntity) = change {
        if (alarm.id == 0L) dao.insertAlarm(alarm) else dao.updateAlarm(alarm)
    }

    fun deleteAlarm(alarm: AlarmEntity) = change {
        AlarmScheduler.cancel(getApplication(), alarm.id)
        dao.deleteAlarm(alarm)
    }

    fun moveAlarm(id: Long, folderId: Long?) = change { dao.moveAlarm(id, folderId) }

    fun createFolder(name: String) = change { dao.insertFolder(FolderEntity(name = name.trim())) }

    fun renameFolder(id: Long, name: String) = change { dao.renameFolder(id, name.trim()) }

    fun setFolderEnabled(id: Long, enabled: Boolean) = change { dao.setFolderEnabled(id, enabled) }

    fun deleteFolder(id: Long) = change { dao.deleteFolderKeepAlarms(id) }
}
