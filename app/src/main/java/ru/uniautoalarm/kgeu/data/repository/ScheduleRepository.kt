package ru.uniautoalarm.kgeu.data.repository

import androidx.room.withTransaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.uniautoalarm.kgeu.data.db.AppDatabase
import ru.uniautoalarm.kgeu.data.db.LessonEntity
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.remote.KgeuApi
import ru.uniautoalarm.kgeu.data.remote.KgeuRaspItem

/**
 * Кэш расписания (Room) + загрузка из API КГЭУ.
 * Сеть уже выполняется в Dispatchers.IO внутри KgeuApi; suspend-запросы Room работают
 * на собственных исполнителях Room — дополнительная обёртка не требуется.
 */
class ScheduleRepository(
    private val db: AppDatabase,
    private val api: KgeuApi,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope, // глобальный scope движка (fire-and-forget задачи)
) {

    private val _syncState = MutableStateFlow(SyncInfo())

    /** Состояние синхронизации для UI: занятость, время, ошибка, число записей за неделю. */
    val syncState: StateFlow<SyncInfo> = _syncState.asStateFlow()

    /** Уникальные названия дисциплин в кэше — автодополнение исключений в настройках. */
    val subjects: Flow<List<String>> = db.lessonDao().distinctSubjects()

    /** Ключ цели в БД: «g:<id>» для группы/студента, «t:<id>» для преподавателя. */
    suspend fun targetKey(t: Target): String = when (t.type) {
        TargetType.STUDENT -> "g:${t.id}"
        TargetType.TEACHER -> "t:${t.id}"
    }

    /**
     * Обновить неделю (пн–вс), содержащую [onDate]: диапазон в БД вычищается и заполняется заново.
     * Ошибки сети/парсинга наружу не бросаются: Result.failure + запись в syncState
     * (кэш при этом остаётся — движок продолжит работать на старых данных).
     */
    suspend fun refreshWeek(target: Target, onDate: LocalDate): Result<Int> {
        val key = targetKey(target)
        val monday = onDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        _syncState.update { it.copy(busy = true) }
        return try {
            val items = api.rasp(target, monday)
            val fromDay = monday.toEpochDay()
            val toDay = sunday.toEpochDay()
            val entities = items.mapNotNull { it.toEntity(key) }
                .filter { it.dateEpochDay in fromDay..toDay }
            db.withTransaction {
                db.lessonDao().deleteRange(key, fromDay, toDay)
                db.lessonDao().insertAll(entities)
            }
            _syncState.update {
                it.copy(
                    busy = false,
                    lastSyncEpochMs = System.currentTimeMillis(),
                    lastError = null,
                    lastSyncWeekCount = entities.size
                )
            }
            Result.success(entities.size)
        } catch (e: CancellationException) {
            _syncState.update { it.copy(busy = false) } // отмену не глотаем, но busy сбрасываем
            throw e
        } catch (e: Exception) {
            _syncState.update {
                it.copy(busy = false, lastError = e.message ?: "Ошибка обновления расписания")
            }
            Result.failure(e)
        }
    }

    /**
     * Полная синхронизация: запрос без sdate (весь семестр одним ответом ~200–300 КБ)
     * + границы семестра (GetRaspDates → semesterMin/Max) + отметка lastFullSyncEpochMs.
     */
    suspend fun refreshFull(target: Target): Result<Int> {
        val key = targetKey(target)
        _syncState.update { it.copy(busy = true) }
        return try {
            val items = api.rasp(target, null)
            val entities = items.mapNotNull { it.toEntity(key) }
            db.withTransaction {
                db.lessonDao().deleteAllFor(key)
                db.lessonDao().insertAll(entities)
            }
            // Границы семестра — вспомогательные: их сбой не должен валить синхронизацию
            runCatching {
                val dates = api.raspDates(target)
                val min = dates.minDate?.let { parseDate(it) }
                val max = dates.maxDate?.let { parseDate(it) }
                if (min != null || max != null) {
                    settings.update { s ->
                        s.copy(
                            semesterMin = min ?: s.semesterMin,
                            semesterMax = max ?: s.semesterMax,
                            lastFullSyncEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            }
            _syncState.update {
                it.copy(
                    busy = false,
                    lastSyncEpochMs = System.currentTimeMillis(),
                    lastError = null,
                    lastSyncWeekCount = entities.size
                )
            }
            Result.success(entities.size)
        } catch (e: CancellationException) {
            _syncState.update { it.copy(busy = false) } // отмену не глотаем, но busy сбрасываем
            throw e
        } catch (e: Exception) {
            _syncState.update {
                it.copy(busy = false, lastError = e.message ?: "Ошибка полной синхронизации")
            }
            Result.failure(e)
        }
    }

    /** Реактивные занятия на [date] по текущей цели из настроек. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun lessonsFlow(date: LocalDate): Flow<List<Lesson>> =
        settings.settings
            .map { s -> s.toTargetOrNull() }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                if (target == null) flowOf(emptyList())
                else db.lessonDao()
                    .observeDay(targetKey(target), date.toEpochDay())
                    .map { entities -> entities.map { it.toModel() } }
            }

    /** Срез занятий на [date] (одиночное чтение кэша). */
    suspend fun lessonsOn(date: LocalDate): List<Lesson> {
        val target = settings.current().toTargetOrNull() ?: return emptyList()
        return db.lessonDao()
            .forDay(targetKey(target), date.toEpochDay())
            .map { it.toModel() }
    }

    /**
     * Времена первых пар по всем дням кэша (отсортированы) — список для настройки
     * «подъём под каждую пару». Реактивно: меняется при смене цели и синхронизации.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun firstLessonStartTimes(): Flow<List<LocalTime>> =
        settings.settings
            .map { s -> s.toTargetOrNull() }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                if (target == null) flowOf(emptyList())
                else db.lessonDao().distinctFirstStartMinutes(targetKey(target))
                    .map { list -> list.map { minutesToTime(it) } }
            }

    /**
     * Первая пара на [date] среди НЕ исключённых (точное совпадение полного названия
     * с [excludedSubjects]). Пар нет или все исключены → null.
     */
    suspend fun firstLessonOn(date: LocalDate, excludedSubjects: Set<String>): Lesson? =
        lessonsOn(date)
            .filter { ScheduleRepository.normalizeForMatch(it.subject) !in excludedSubjects }
            .minByOrNull { it.start }

    /** Полная очистка кэша расписания (свои занятия и исключения не трогаются). */
    suspend fun clearAll() {
        db.lessonDao().deleteAll()
        _syncState.value = SyncInfo()
    }

    /**
     * Свежие данные для мгновенной постановки будильника: неделя сегодня и неделя
     * через 7 дней — вместе покрывают двухнедельный горизонт планировщика при любом
     * дне недели. Расписание вуза часто меняют ПОСЛЕ последней синхронизации (замены
         * отменяют/переносят пары) — по устаревшему кэшу тумблер ставил будильник на день,
     * где пар уже нет (баг 0.6.0). Ошибки сети глотаются: при сбое постановка идёт по кэшу.
     */
    suspend fun refreshAround(from: LocalDate = LocalDate.now()) {
        val target = settings.current().toTargetOrNull() ?: return
        runCatching { refreshWeek(target, from) }
        runCatching { refreshWeek(target, from.plusDays(7)) }
    }

    // --- Маппинг DTO ↔ Entity ↔ домен ---

    /** Запись API → Entity. Записи без даты начала не сохраняем (по ним не построить будильник). */
    private fun KgeuRaspItem.toEntity(targetKey: String): LessonEntity? {
        val startDt = KgeuApi.parseIsoDateTime(startDateTime) ?: return null
        val startDate = startDt.toLocalDate()
        val startT = startDt.toLocalTime()
        // конец: из датаОкончания, иначе строка «конец», иначе — началу
        val endT = KgeuApi.parseIsoDateTime(endDateTime)?.toLocalTime()
            ?: endTime?.let { parseTime(it) }
            ?: startT
        return LessonEntity(
            targetKey = targetKey,
            dateEpochDay = startDate.toEpochDay(),
            startMinutes = startT.minutesOfDay(),
            endMinutes = endT.minutesOfDay().coerceAtLeast(startT.minutesOfDay()),
            subject = subject ?: "",
            teacher = teacher ?: teacherFull ?: "",
            room = room ?: "",
            kind = extractKind(subject),
            isReplacement = isReplacement,
            group = group ?: ""
        )
    }

    private fun LessonEntity.toModel(): Lesson = Lesson(
        date = LocalDate.ofEpochDay(dateEpochDay),
        start = minutesToTime(startMinutes),
        end = minutesToTime(endMinutes),
        subject = subject,
        teacher = teacher,
        room = room,
        kind = kind,
        isReplacement = isReplacement,
        group = group
    )

    /** Цель из настроек; цель не выбрана → null. */
    private fun Settings.toTargetOrNull(): Target? {
        val type = targetType ?: return null
        val id = targetId ?: return null
        return Target(type, id, targetName)
    }

    private fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()
    private fun parseTime(hhmm: String): LocalTime? = runCatching { LocalTime.parse(hhmm) }.getOrNull()

    private fun LocalTime.minutesOfDay(): Int = hour * 60 + minute

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val KNOWN_KINDS = setOf("лек", "пр", "лаб", "кп", "ргр", "сем", "конс")

        /**
         * Нормализация для сопоставления ИСКЛЮЧЕНИЙ с парами: нижний регистр, трим,
         * схлопывание пробелов. Приставку типа («лек /пр /лаб ») НЕ срезаем: она — часть
         * названия пары, и «л. материаловедение» должно отличаться от
         * «пр. материаловедение» (баг 0.6.0: срезанный префикс ловил все типы разом).
         */
        fun normalizeForMatch(s: String): String =
            s.trim().lowercase().replace(WHITESPACE, " ")

        /** Минуты от 00:00 → LocalTime (coerce от «24:00» и прочего мусора). */
        fun minutesToTime(minutes: Int): LocalTime =
            LocalTime.of((minutes / 60).coerceAtMost(23), minutes % 60)

        /** Тип занятия из префикса дисциплины («пр Атомные …» → «пр»). */
        private fun extractKind(subject: String?): String {
            if (subject.isNullOrBlank()) return ""
            val token = subject.trim().split(' ').firstOrNull()?.trimEnd('.')?.lowercase()
            return if (token != null && token in KNOWN_KINDS) token else ""
        }
    }
}
