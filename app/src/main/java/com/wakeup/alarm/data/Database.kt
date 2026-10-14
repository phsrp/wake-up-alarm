package com.wakeup.alarm.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.wakeup.alarm.Storage
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Master switch. When off, every alarm in the folder is silent but keeps its own on/off state. */
    val enabled: Boolean = true,
)

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday ... bit 6 = Sunday. 0 means a one-time alarm. */
    val repeatDays: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** Null means the phone's default alarm sound. */
    val soundUri: String? = null,
    /** 0 means ring until dismissed. */
    val durationMinutes: Int = 0,
    val folderId: Long? = null,
)

@Dao
abstract class AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    abstract fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    abstract suspend fun getAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    abstract suspend fun getAlarm(id: Long): AlarmEntity?

    @Insert
    abstract suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    abstract suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    abstract suspend fun deleteAlarm(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setAlarmEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET folderId = :folderId WHERE id = :id")
    abstract suspend fun moveAlarm(id: Long, folderId: Long?)

    @Query("UPDATE alarms SET folderId = NULL WHERE folderId = :folderId")
    abstract suspend fun ungroupFolder(folderId: Long)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    abstract fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    abstract suspend fun getFolders(): List<FolderEntity>

    @Insert
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    abstract suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE folders SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setFolderEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM folders WHERE id = :id")
    abstract suspend fun deleteFolderRow(id: Long)

    /** Deleting a folder never deletes alarms: they move to "Ungrouped". */
    @Transaction
    open suspend fun deleteFolderKeepAlarms(id: Long) {
        ungroupFolder(id)
        deleteFolderRow(id)
    }
}

@Database(entities = [AlarmEntity::class, FolderEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AlarmDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                Storage.context(context),
                AppDatabase::class.java,
                "wakeup.db",
            ).build().also { instance = it }
        }
    }
}
