package ru.uniautoalarm.kgeu.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.uniautoalarm.kgeu.alarm.Notifications
import ru.uniautoalarm.kgeu.data.db.AppDatabase
import ru.uniautoalarm.kgeu.data.db.ExceptionEntity
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
    private val appContext: Context, // уведомление «проверьте группу» при подозрительно пустых ответах (DATA-1)
) {

    private val _syncState = MutableStateFlow(SyncInfo())

    /** Состояние синхронизации для UI: занятость, время, ошибка, число записей за неделю. */
    val syncState: StateFlow<SyncInfo> = _syncState.asStateFlow()

    /**
     * Single-flight синхронизации (DATA-8, 0.10.0): ночной тик + periodic WorkManager +
     * ручное обновление могут сойтись в один момент — без мьютекса это дубли сетевых
     * запросов и перетираемый busy; теперь запросы выполняются строго по одному.
     */
    private val syncGate = Mutex()

    /**
     * Уникальные названия дисциплин в кэше СЕКУЩЕЙ цели — автодополнение исключений
     * (DATA-5, 0.10.0): раньше подтягивались предметы ВСЕХ целей, когда-либо
     * попадавших в кэш, включая давно сменённые группы.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val subjects: Flow<List<String>> = settings.settings
        .map { s -> s.toTargetOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { target ->
            if (target == null) flowOf(emptyList())
            else db.lessonDao().observeDistinctSubjects(targetKey(target))
        }

    /**
     * Подозрительно пустые ответы подряд (DATA-1): сервер КГЭУ на несуществующий/устаревший
     * idGroup отвечает state:1 + rasp:[] — ШТАТНО, без ошибки. Смена учебного года
     * меняет id групп при сохранённом в настройках targetId — без guard'а один такой
     * ответ вытирал весь кэш, а ночная сверка снимала будильник как «фантом».
     */
    private var emptyResponseStreak = 0
    private val emptyStreakLock = Any()

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
    suspend fun refreshWeek(target: Target, onDate: LocalDate): Result<Int> =
        syncGate.withLock {
            val key = targetKey(target)
            val monday = onDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            _syncState.update { it.copy(busy = true) }
            try {
                val items = api.rasp(target, monday)
                val fromDay = monday.toEpochDay()
                val toDay = sunday.toEpochDay()
                val entities = items.mapNotNull { it.toEntity(key) }
                    .filter { it.dateEpochDay in fromDay..toDay }
                // DATA-1: пустой ответ при непустом кэше — подозрение на устаревший idGroup
                // (смена учебного года): кэш НЕ стираем, считаем серию пустых ответов
                val suspiciousEmpty = entities.isEmpty() && db.lessonDao().countFor(key) > 0
                db.withTransaction {
                    if (suspiciousEmpty) {
                        Log.w(TAG, "Пустой ответ при непустом кэше (idGroup мог устареть) — кэш сохранён (DATA-1)")
                    } else {
                        db.lessonDao().deleteRange(key, fromDay, toDay)
                        db.lessonDao().insertAll(entities)
                    }
                }
                updateEmptyStreak(suspiciousEmpty)
                if (!suspiciousEmpty) {
                    // 0.18.0: недельная синхронизация тоже штампует свежесть — раньше
                    // штамповала только полная (раз в 7 дней), и предупреждение
                    // «не обновлялось более 3 дней» не убиралось даже после
                    // ручного обновления кнопкой
                    val now = System.currentTimeMillis()
                    settings.update { s ->
                        s.copy(lastSyncEpochMs = now)
                    }
                }
                _syncState.update {
                    it.copy(
                        busy = false,
                        lastSyncEpochMs = if (suspiciousEmpty) it.lastSyncEpochMs else System.currentTimeMillis(),
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
     * + границы семестра (GetRaspDates → semesterMin/Max).
     *
     * DATA-4 (0.10.0): штамп свежести lastFullSyncEpochMs ставится по успеху ГЛАВНОГО
     * запроса /api/Rasp. Раньше он ставился только внутри блока GetRaspDates — сбой
     * вспомогательного запроса означал вечный full-sync каждые 12 часов и вечный
     * ложный баннер «расписание устарело».
     */
    suspend fun refreshFull(target: Target): Result<Int> = syncGate.withLock {
        val key = targetKey(target)
        _syncState.update { it.copy(busy = true) }
        try {
            val items = api.rasp(target, null)
            val entities = items.mapNotNull { it.toEntity(key) }
            // DATA-1: state:1 + пустой rasp при непустом кэше — НЕ успех-очистка:
            // не стираем семестр, не штампуем свежесть и считаем серию (см. updateEmptyStreak)
            val suspiciousEmpty = entities.isEmpty() && db.lessonDao().countFor(key) > 0
            db.withTransaction {
                if (suspiciousEmpty) {
                    Log.w(TAG, "Полный синк вернул пусто при непустом кэше — кэш сохранён (DATA-1)")
                } else {
                    db.lessonDao().deleteAllFor(key)
                    db.lessonDao().insertAll(entities)
                    // DATA-5: кэш прежних целей больше не нужен — таблица не растёт
                    db.lessonDao().deleteOtherTargets(key)
                }
            }
            updateEmptyStreak(suspiciousEmpty)
            if (!suspiciousEmpty) {
                // DATA-4: свежесть — от успеха основного запроса расписания;
                // полная синхронизация штампует и lastSyncEpochMs (предупреждение «3 дня»)
                val now = System.currentTimeMillis()
                settings.update { s ->
                    s.copy(lastFullSyncEpochMs = now, lastSyncEpochMs = now)
                }
            }
            // Границы семестра — вспомогательные: их сбой НЕ влияет на свежесть (DATA-4)
            runCatching {
                val dates = api.raspDates(target)
                val min = dates.minDate?.let { parseDate(it) }
                val max = dates.maxDate?.let { parseDate(it) }
                if (min != null || max != null) {
                    settings.update { s ->
                        s.copy(
                            semesterMin = min ?: s.semesterMin,
                            semesterMax = max ?: s.semesterMax
                        )
                    }
                }
            }
            _syncState.update {
                it.copy(
                    busy = false,
                    lastSyncEpochMs = if (suspiciousEmpty) it.lastSyncEpochMs else System.currentTimeMillis(),
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

    /**
     * Серия подозрительно пустых ответов (DATA-1). Несколько пустых синков подряд
     * при сохранённом кэше — почти наверняка устаревший idGroup: уведомляем
     * «проверьте группу». Один пустой ответ (каникулы, ещё не загруженная неделя)
     * не спамит.
     */
    private fun updateEmptyStreak(suspiciousEmpty: Boolean) {
        synchronized(emptyStreakLock) {
            if (suspiciousEmpty) {
                emptyResponseStreak += 1
                if (emptyResponseStreak == EMPTY_STREAK_NOTIFY) {
                    Log.w(TAG, "Цель не отдаёт пары $EMPTY_STREAK_NOTIFY синков подряд — уведомление «проверьте группу»")
                    Notifications.groupCheck(appContext)
                }
            } else {
                emptyResponseStreak = 0
            }
        }
    }

    /**
     * Одноразовая миграция правил-исключений 0.6.0 → 0.9.0 (DATA-3).
     *
     * В 0.6.0 правило хранилось БЕЗ приставки типа, а сопоставление срезало префикс:
     * «материаловедение» ловило и лекцию, и практику, и лабораторную. С 0.7.0 семантика
     * точная — тип уже написан в названии («л. материаловедение» ≠ «пр. материаловедение»),
     * и старые правила молча сузились: практики и лабы той же дисциплины снова звонили.
     *
     * Миграция расширяет каждое безпрефиксное правило на ВСЕ полные названия этой
     * дисциплины из кэша: «материаловедение» → «л. материаловедение» + «пр. материаловедение» + …
     * Правила, у которых тип уже прописан в названии, не трогаются; при пустом кэше
     * правило остаётся как есть (расширять не из чего). @return были ли изменения.
     */
    suspend fun migrateExceptionRulesToExactNames(): Boolean {
        val dao = db.exceptionDao()
        val rules = dao.allOnce()
        if (rules.isEmpty()) return false
        // база (название без типа) → все полные названия дисциплины в кэше
        val baseIndex = HashMap<String, HashSet<String>>()
        for (subject in db.lessonDao().distinctSubjectsOnce()) {
            val n = normalizeForMatch(subject)
            baseIndex.getOrPut(stripKindPrefix(n)) { HashSet() }.add(n)
        }
        var changed = false
        val existing = rules.mapTo(HashSet()) { normalizeForMatch(it.subject) }
        for (rule in rules) {
            val normalized = normalizeForMatch(rule.subject)
            if (hasKindPrefix(normalized)) continue // правило уже нового образца
            val fullNames = baseIndex[normalized] ?: continue // пар этой дисциплины в кэше нет
            val targets = fullNames - normalized
            if (targets.isEmpty()) continue
            Log.i(TAG, "Миграция правила 0.6.0 «${rule.subject}» → ${targets.size} полных названий (DATA-3)")
            dao.delete(rule.id)
            existing.remove(normalized)
            for (full in targets.sorted()) {
                if (existing.add(full)) dao.upsert(ExceptionEntity(subject = full))
            }
            changed = true
        }
        return changed
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
        private const val TAG = "ScheduleRepository"

        /** Сколько подозрительно пустых синков подряд до уведомления «проверьте группу» (DATA-1). */
        private const val EMPTY_STREAK_NOTIFY = 3

        private val WHITESPACE = Regex("\\s+")
        private val KNOWN_KINDS = setOf("лек", "пр", "лаб", "кп", "ргр", "сем", "конс")

        /**
         * Токены типов пар, встречающиеся ПЕРВЫМ словом названия (шире [KNOWN_KINDS]:
         * API пишет и «л.» для лекций). Нужны для миграции правил 0.9.0 (DATA-3):
         * отличить правило нового образца («пр. математика») от старого безпрефиксного
         * («математика»).
         */
        private val KIND_TOKENS = KNOWN_KINDS + setOf("л", "лек", "лабор", "семин")

        /**
         * Нормализация для сопоставления ИСКЛЮЧЕНИЙ с парами: нижний регистр, трим,
         * схлопывание пробелов. Приставку типа («лек /пр /лаб ») НЕ срезаем: она — часть
         * названия пары, и «л. материаловедение» должно отличаться от
         * «пр. материаловедение» (баг 0.6.0: срезанный префикс ловил все типы разом).
         */
        fun normalizeForMatch(s: String): String =
            s.trim().lowercase().replace(WHITESPACE, " ")

        /** Первый токен нормализованного названия — приставка типа? («пр. математика» → true). */
        fun hasKindPrefix(normalizedSubject: String): Boolean =
            normalizedSubject.split(' ').firstOrNull()?.trimEnd('.') in KIND_TOKENS

        /** Название без приставки типа («пр. математика» → «математика»; без типа — как есть). */
        fun stripKindPrefix(normalizedSubject: String): String {
            val parts = normalizedSubject.split(' ')
            val first = parts.firstOrNull()?.trimEnd('.')
            return if (first in KIND_TOKENS && parts.size > 1) {
                parts.drop(1).joinToString(" ").trim()
            } else {
                normalizedSubject
            }
        }

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
