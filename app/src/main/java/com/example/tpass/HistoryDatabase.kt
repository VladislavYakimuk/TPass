package com.example.tpass

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Date
import androidx.room.migration.Migration

@Entity(tableName = "password_history")
data class PasswordHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceName: String = "",
    val username: String = "",
    val oldPassword: String = "",
    val newPassword: String = "",
    val action: String = "",
    val timestamp: Date = Date()
)

@Dao
interface PasswordHistoryDao {
    @Query("SELECT * FROM password_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<PasswordHistoryEntity>

    @Insert
    suspend fun insertHistory(history: PasswordHistoryEntity)

    @Query("DELETE FROM password_history")
    suspend fun clearHistory()

    @Query("DELETE FROM password_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long)
}

@Database(entities = [PasswordHistoryEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun passwordHistoryDao(): PasswordHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE password_history ADD COLUMN username TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE password_history ADD COLUMN oldPassword TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE password_history ADD COLUMN newPassword TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Если колонки уже существуют, игнорируем ошибку
                }
            }
        }

        fun getDatabase(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "history_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 