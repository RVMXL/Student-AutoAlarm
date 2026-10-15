package ru.uniautoalarm.kgeu.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.model.ThemeMode

/** Единый DataStore настроек приложения (синглтон на процесс по имени файла). */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "uni_autoalarm_settings")

/**
 * Хранилище настроек на DataStore-preferences.
 * Все поля Settings сериализуются явно: enum — строкой name(), LocalDate — ISO-строкой,
 * nullable-поля — через contains()/remove().
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val TARGET_TYPE = stringPreferencesKey("target_type")
        val TARGET_ID = intPreferencesKey("target_id")
        val TARGET_NAME = stringPreferencesKey("target_name")
        val WAKE_OFFSET_MINUTES = intPreferencesKey("wake_offset_minutes")
        val AUTO_ALARM_ENABLED = booleanPreferencesKey("auto_alarm_enabled")
        val SEMESTER_MIN = stringPreferencesKey("semester_min")
        val SEMESTER_MAX = stringPreferencesKey("semester_max")
        val LAST_FULL_SYNC_EPOCH_MS = longPreferencesKey("last_full_sync_epoch_ms")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_INDEX = intPreferencesKey("accent_index")
        val CALENDAR_STYLE = stringPreferencesKey("calendar_style")
        val BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")
        val ALARM_SHOW_CLOCK_UI = booleanPreferencesKey("alarm_show_clock_ui")
        val ALARM_TARGET = stringPreferencesKey("alarm_target")
        val FIRST_LESSON_OFFSETS = stringPreferencesKey("first_lesson_offsets")
        val GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
        val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
        val LAST_SCHEDULED_WAKE = longPreferencesKey("last_scheduled_wake_epoch_minute")
        val LAST_SCHEDULED_LABEL = stringPreferencesKey("last_scheduled_label")
        val MIGRATED_APP_ENGINE = booleanPreferencesKey("migrated_app_engine_071")
        val MIGRATED_SINGLE_NIGHT = booleanPreferencesKey("migrated_single_night_080")
        val MIGRATED_RULES_090 = booleanPreferencesKey("migrated_exception_rules_090")
        // legacy-ключи режимов «на несколько дней вперёд» — читаются только миграцией 0.8.0
        val LEGACY_MODE = stringPreferencesKey("mode")
        val LEGACY_WEEK_ALARMS_SET_UNTIL = longPreferencesKey("week_alarms_set_until_epoch_day")
    }

    /** Поток настроек: перевыпускается при каждом изменении; сбой чтения → значения по умолчанию. */
    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    /** Текущий срез настроек (одиночное чтение). */
    suspend fun current(): Settings = settings.first()

    /**
     * Атомарное обновление: читаем текущее, применяем [transform],
     * записываем ВСЕ ключи (nullable — через remove, чтобы не оставлять устаревших значений).
     */
    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            prefs.writeSettings(transform(prefs.toSettings()))
        }
    }

    /** Цель расписания (группа/преподаватель) пишется одним блоком — три поля не должны разъехаться. */
    suspend fun applyTarget(type: TargetType, id: Int, name: String) {
        update { it.copy(targetType = type, targetId = id, targetName = name) }
    }

    /**
     * Одноразовая миграция 0.7.0 → 0.7.1: движком будильника по умолчанию становится
     * «В приложении». Причины:
     *  1) SET_ALARM стоковых часов не умеет «на дату»: одноразовый будильник звенит
     *     в ближайшее наступление времени подъёма — при паре через два дня звенел
     *     ЗАВТРА («раз через раз» — совпадение, когда пара и правда завтра);
     *  2) DISMISS_ALARM на части оболочек (ColorOS/Realme UI) молча игнорируется —
     *     выключение тумблера не снимало будильники.
     * Собственный AlarmManager.setAlarmClock лишён обеих проблем (точная дата,
     * гарантированный cancel). Пользователь может вернуть часы в настройках;
     * миграция применяется ровно один раз (флаг MIGRATED_APP_ENGINE).
     */
    suspend fun migrate071AppEngine() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.MIGRATED_APP_ENGINE] != true) {
                prefs[Keys.ALARM_TARGET] = AlarmTarget.APP.name
                prefs[Keys.MIGRATED_APP_ENGINE] = true
            }
        }
    }

    /**
     * Одноразовая миграция 0.7.x → 0.8.0: логика авто-будильника стала ОДНОЙ —
     * «одна ночь вперёд» (тумблер ставит максимум на завтра; ночная сверка 00:05/06:00
     * ставит/снимает по факту). Режим «на несколько дней вперёд» удалён: он ставил
     * ПОВТОРЯЮЩИЕСЯ будильники на день недели (часы — EXTRA_DAYS, приложение — слот
     * на каждую дату), которые при смене расписания/выключении тумблера приходилось
     * выключать руками — ровно то, от чего ушла новая логика.
     *
     * @return эпоха-день конца прежней партии будильников (для снятия слотов AlarmManager),
     *         или -1, если партий не было; null — миграция уже выполнялась.
     */
    suspend fun migrate080SingleNight(): Long? {
        var result: Long? = null
        context.dataStore.edit { prefs ->
            if (prefs[Keys.MIGRATED_SINGLE_NIGHT] != true) {
                // конец прежней партии слотов «на несколько дней вперёд» (для cancelRange);
                // недельные будильники в часах снимает dismissAll при очистке в AutoAlarmApp
                result = prefs[Keys.LEGACY_WEEK_ALARMS_SET_UNTIL] ?: -1L
                prefs.remove(Keys.LEGACY_MODE)
                prefs.remove(Keys.LEGACY_WEEK_ALARMS_SET_UNTIL)
                prefs[Keys.MIGRATED_SINGLE_NIGHT] = true
            }
        }
        return result
    }

    /**
     * Одноразовый «захват» миграции правил-исключений 0.9.0 (DATA-3): 0.6.0-правила
     * без приставки типа после перехода на точную семантику молча сузились.
     * @return true — миграцию нужно выполнить (флаг взят атомарно, повторный запуск
     * процесса миграцию не задублирует); false — уже выполнена.
     */
    suspend fun claimRulesMigration090(): Boolean {
        var claim = false
        context.dataStore.edit { prefs ->
            if (prefs[Keys.MIGRATED_RULES_090] != true) {
                prefs[Keys.MIGRATED_RULES_090] = true
                claim = true
            }
        }
        return claim
    }

    // --- Чтение ---

    private fun Preferences.toSettings(): Settings = Settings(
        setupComplete = this[Keys.SETUP_COMPLETE] ?: false,
        targetType = this[Keys.TARGET_TYPE]?.let { parseTargetType(it) },
        targetId = if (contains(Keys.TARGET_ID)) this[Keys.TARGET_ID] else null,
        targetName = this[Keys.TARGET_NAME] ?: "",
        wakeOffsetMinutes = this[Keys.WAKE_OFFSET_MINUTES] ?: 90,
        autoAlarmEnabled = this[Keys.AUTO_ALARM_ENABLED] ?: true,
        semesterMin = this[Keys.SEMESTER_MIN]?.let { parseDate(it) },
        semesterMax = this[Keys.SEMESTER_MAX]?.let { parseDate(it) },
        lastFullSyncEpochMs = this[Keys.LAST_FULL_SYNC_EPOCH_MS] ?: 0L,
        themeMode = parseEnum(this[Keys.THEME_MODE], ThemeMode.SYSTEM),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        accentIndex = this[Keys.ACCENT_INDEX] ?: 0,
        calendarStyle = parseEnum(this[Keys.CALENDAR_STYLE], CalendarStyle.STANDARD),
        backgroundImagePath = this[Keys.BACKGROUND_IMAGE_PATH],
        backgroundOpacity = this[Keys.BACKGROUND_OPACITY] ?: 0.35f,
        reminderEnabled = this[Keys.REMINDER_ENABLED] ?: false,
        reminderTime = this[Keys.REMINDER_TIME]?.let { parseTime(it) } ?: LocalTime.of(21, 0),
        alarmShowClockUi = this[Keys.ALARM_SHOW_CLOCK_UI] ?: false,
        alarmTarget = parseEnum(this[Keys.ALARM_TARGET], AlarmTarget.APP),
        firstLessonOffsets = this[Keys.FIRST_LESSON_OFFSETS]?.let { parseOffsets(it) } ?: emptyMap(),
        glassEnabled = this[Keys.GLASS_ENABLED] ?: false,
        glassIntensity = this[Keys.GLASS_INTENSITY] ?: 0.5f,
        lastScheduledWakeEpochMinute = this[Keys.LAST_SCHEDULED_WAKE] ?: -1L,
        lastScheduledLabel = this[Keys.LAST_SCHEDULED_LABEL] ?: ""
    )

    // --- Запись: setXxx для каждого поля Settings ---

    private fun MutablePreferences.writeSettings(s: Settings) {
        this[Keys.SETUP_COMPLETE] = s.setupComplete

        val type = s.targetType
        if (type != null) this[Keys.TARGET_TYPE] = type.name else remove(Keys.TARGET_TYPE)
        val id = s.targetId
        if (id != null) this[Keys.TARGET_ID] = id else remove(Keys.TARGET_ID)
        this[Keys.TARGET_NAME] = s.targetName

        this[Keys.WAKE_OFFSET_MINUTES] = s.wakeOffsetMinutes
        this[Keys.AUTO_ALARM_ENABLED] = s.autoAlarmEnabled

        val min = s.semesterMin
        if (min != null) this[Keys.SEMESTER_MIN] = min.toString() else remove(Keys.SEMESTER_MIN)
        val max = s.semesterMax
        if (max != null) this[Keys.SEMESTER_MAX] = max.toString() else remove(Keys.SEMESTER_MAX)

        this[Keys.LAST_FULL_SYNC_EPOCH_MS] = s.lastFullSyncEpochMs

        this[Keys.THEME_MODE] = s.themeMode.name
        this[Keys.DYNAMIC_COLOR] = s.dynamicColor
        this[Keys.ACCENT_INDEX] = s.accentIndex
        this[Keys.CALENDAR_STYLE] = s.calendarStyle.name

        val path = s.backgroundImagePath
        if (path != null) this[Keys.BACKGROUND_IMAGE_PATH] = path else remove(Keys.BACKGROUND_IMAGE_PATH)
        this[Keys.BACKGROUND_OPACITY] = s.backgroundOpacity

        this[Keys.REMINDER_ENABLED] = s.reminderEnabled
        this[Keys.REMINDER_TIME] = s.reminderTime.toString()
        this[Keys.ALARM_SHOW_CLOCK_UI] = s.alarmShowClockUi
        this[Keys.ALARM_TARGET] = s.alarmTarget.name
        this[Keys.FIRST_LESSON_OFFSETS] = serializeOffsets(s.firstLessonOffsets)
        this[Keys.GLASS_ENABLED] = s.glassEnabled
        this[Keys.GLASS_INTENSITY] = s.glassIntensity
        this[Keys.LAST_SCHEDULED_WAKE] = s.lastScheduledWakeEpochMinute
        this[Keys.LAST_SCHEDULED_LABEL] = s.lastScheduledLabel
    }

    // --- Утилиты парсинга (битые значения молча заменяются дефолтом) ---

    private fun parseTargetType(name: String): TargetType? =
        runCatching { TargetType.valueOf(name) }.getOrNull()

    private inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
        if (name == null) default
        else runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    private fun parseTime(iso: String): LocalTime? = runCatching { LocalTime.parse(iso) }.getOrNull()

    // --- Сериализация firstLessonOffsets: «480:60;580:90» (порядок не важен) ---

    private fun serializeOffsets(map: Map<Int, Int>): String =
        map.entries.joinToString(";") { "${it.key}:${it.value}" }

    /** Битые записи молча выбрасываются; офсеты зажимаются в разумные границы. */
    private fun parseOffsets(raw: String): Map<Int, Int> = raw.split(';')
        .mapNotNull {
            val parts = it.split(':')
            if (parts.size != 2) return@mapNotNull null
            val start = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val offset = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (start !in 0..24 * 60 - 1) return@mapNotNull null
            start to offset.coerceIn(15, 240)
        }
        .toMap()
}
