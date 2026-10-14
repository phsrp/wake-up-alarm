package com.wakeup.alarm.data

import android.content.Context
import androidx.room.ColumnInfo
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wakeup.alarm.Storage
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Master switch. When off, every alarm in the folder is silent but keeps its own on/off state. */
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
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
    /** Manual order. Alarms that were never moved share 0 and are listed by time. */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val durationSeconds: Int,
    /** One of [STATE_IDLE], [STATE_RUNNING], [STATE_PAUSED], [STATE_FINISHED]. */
    val state: Int = STATE_IDLE,
    /** While running: the wall-clock moment (epoch millis) the timer ends. */
    val endAtMillis: Long = 0,
    /** While paused: how much time is left. */
    val remainingMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
) {
    companion object {
        const val STATE_IDLE = 0
        const val STATE_RUNNING = 1
        const val STATE_PAUSED = 2
        const val STATE_FINISHED = 3
    }
}

@Dao
abstract class AlarmDao {
    // ----- alarms -----
    @Query("SELECT * FROM alarms ORDER BY position, hour, minute")
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

    @Query("DELETE FROM alarms WHERE id IN (:ids)")
    abstract suspend fun deleteAlarms(ids: List<Long>)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setAlarmEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET folderId = :folderId WHERE id = :id")
    abstract suspend fun moveAlarm(id: Long, folderId: Long?)

    @Query("UPDATE alarms SET folderId = NULL WHERE folderId = :folderId")
    abstract suspend fun ungroupFolder(folderId: Long)

    @Query("UPDATE alarms SET position = :position WHERE id = :id")
    abstract suspend fun setAlarmPosition(id: Long, position: Int)

    @Transaction
    open suspend fun applyAlarmOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setAlarmPosition(id, index + 1) }
    }

    // ----- folders -----
    @Query("SELECT * FROM folders ORDER BY position, name COLLATE NOCASE")
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

    @Query("UPDATE folders SET position = :position WHERE id = :id")
    abstract suspend fun setFolderPosition(id: Long, position: Int)

    /** Deleting a folder never deletes alarms: they move to "Ungrouped". */
    @Transaction
    open suspend fun deleteFolderKeepAlarms(id: Long) {
        ungroupFolder(id)
        deleteFolderRow(id)
    }

    @Transaction
    open suspend fun applyFolderOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setFolderPosition(id, index + 1) }
    }

    // ----- timers -----
    @Query("SELECT * FROM timers ORDER BY position, id")
    abstract fun observeTimers(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers")
    abstract suspend fun getTimers(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    abstract suspend fun getTimer(id: Long): TimerEntity?

    @Insert
    abstract suspend fun insertTimer(timer: TimerEntity): Long

    @Update
    abstract suspend fun updateTimer(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id IN (:ids)")
    abstract suspend fun deleteTimers(ids: List<Long>)

    @Query("UPDATE timers SET position = :position WHERE id = :id")
    abstract suspend fun setTimerPosition(id: Long, position: Int)

    @Transaction
    open suspend fun applyTimerOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setTimerPosition(id, index + 1) }
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS timers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "label TEXT NOT NULL, " +
                "durationSeconds INTEGER NOT NULL, " +
                "state INTEGER NOT NULL, " +
                "endAtMillis INTEGER NOT NULL, " +
                "remainingMillis INTEGER NOT NULL, " +
                "position INTEGER NOT NULL DEFAULT 0)",
        )
    }
}

@Database(
    entities = [AlarmEntity::class, FolderEntity::class, TimerEntity::class],
    version = 2,
    exportSchema = false,
)
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
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
