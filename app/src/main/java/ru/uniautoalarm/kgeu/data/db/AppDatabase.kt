package ru.uniautoalarm.kgeu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** БД приложения (кэш расписания, свои занятия, исключения). Версия 2. */
@Database(
    entities = [LessonEntity::class, CustomEventEntity::class, ExceptionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lessonDao(): LessonDao
    abstract fun customEventDao(): CustomEventDao
    abstract fun exceptionDao(): ExceptionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * 1 → 2 (0.6.0): у исключений появился тип пары — исключать можно не только
         * дисциплину целиком, но и только её лекции/практики/лабораторные.
         * Колонка nullable (null = все типы); миграция НЕ разрушающая: правила
         * и свои занятия пользователей сохраняются.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exceptions ADD COLUMN kind TEXT")
            }
        }

        /** Потокобезопасный синглтон (double-checked locking). */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uni_autoalarm.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
