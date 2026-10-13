package ru.uniautoalarm.kgeu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** БД приложения (кэш расписания, свои занятия, исключения). Версия 1. */
@Database(
    entities = [LessonEntity::class, CustomEventEntity::class, ExceptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lessonDao(): LessonDao
    abstract fun customEventDao(): CustomEventDao
    abstract fun exceptionDao(): ExceptionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Потокобезопасный синглтон (double-checked locking). */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uni_autoalarm.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
