package ru.uniautoalarm.kgeu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {

    /** Все занятия цели за конкретный день, по времени начала (одиночный срез). */
    @Query(
        "SELECT * FROM lessons WHERE targetKey = :targetKey AND dateEpochDay = :dateEpochDay " +
            "ORDER BY startMinutes ASC"
    )
    suspend fun forDay(targetKey: String, dateEpochDay: Long): List<LessonEntity>

    /** Реактивная версия [forDay]: перевыпускается после любой записи в таблицу. */
    @Query(
        "SELECT * FROM lessons WHERE targetKey = :targetKey AND dateEpochDay = :dateEpochDay " +
            "ORDER BY startMinutes ASC"
    )
    fun observeDay(targetKey: String, dateEpochDay: Long): Flow<List<LessonEntity>>

    @Insert
    suspend fun insertAll(lessons: List<LessonEntity>): List<Long>

    @Query(
        "DELETE FROM lessons WHERE targetKey = :targetKey " +
            "AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay"
    )
    suspend fun deleteRange(targetKey: String, fromEpochDay: Long, toEpochDay: Long)

    @Query("DELETE FROM lessons WHERE targetKey = :targetKey")
    suspend fun deleteAllFor(targetKey: String)

    @Query("DELETE FROM lessons")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM lessons WHERE targetKey = :targetKey")
    suspend fun countFor(targetKey: String): Int

    /** Уникальные названия дисциплин в кэше — для автодополнения исключений. */
    @Query("SELECT DISTINCT subject FROM lessons ORDER BY subject ASC")
    fun distinctSubjects(): Flow<List<String>>

    /** Одиночный срез уникальных названий — миграция правил-исключений 0.9.0 (DATA-3). */
    @Query("SELECT DISTINCT subject FROM lessons")
    suspend fun distinctSubjectsOnce(): List<String>

    /**
     * Уникальные времена НАЧАЛА первой пары каждого дня — список для настройки
     * «подъём под каждую пару». Подзапрос берёт MIN(startMinutes) по каждой дате,
     * DISTINCT убирает повторы недели.
     */
    @Query(
        "SELECT DISTINCT firstStart FROM " +
            "(SELECT MIN(startMinutes) AS firstStart FROM lessons " +
            "WHERE targetKey = :targetKey GROUP BY dateEpochDay) " +
            "ORDER BY firstStart ASC"
    )
    fun distinctFirstStartMinutes(targetKey: String): Flow<List<Int>>
}

@Dao
interface CustomEventDao {

    @Query("SELECT * FROM custom_events ORDER BY startMinutes ASC")
    fun all(): Flow<List<CustomEventEntity>>

    @Query("SELECT * FROM custom_events ORDER BY startMinutes ASC")
    suspend fun allOnce(): List<CustomEventEntity>

    @Upsert
    suspend fun upsert(event: CustomEventEntity)

    @Query("DELETE FROM custom_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE custom_events SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

@Dao
interface ExceptionDao {

    @Query("SELECT * FROM exceptions ORDER BY subject ASC")
    fun all(): Flow<List<ExceptionEntity>>

    @Query("SELECT * FROM exceptions ORDER BY subject ASC")
    suspend fun allOnce(): List<ExceptionEntity>

    @Upsert
    suspend fun upsert(rule: ExceptionEntity)

    @Query("DELETE FROM exceptions WHERE id = :id")
    suspend fun delete(id: Long)
}
