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
import ru.uniautoalarm.kgeu.data.model.AlarmMode
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
        val MODE = stringPreferencesKey("mode")
        val WEEK_AHEAD_DAYS = intPreferencesKey("week_ahead_days")
        val SEMESTER_MIN = stringPreferencesKey("semester_min")
        val SEMESTER_MAX = stringPreferencesKey("semester_max")
        val LAST_FULL_SYNC_EPOCH_MS = longPreferencesKey("last_full_sync_epoch_ms")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_INDEX = intPreferencesKey("accent_index")
        val CALENDAR_STYLE = stringPreferencesKey("calendar_style")
        val BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        val WEEK_ALARMS_SET_UNTIL = longPreferencesKey("week_alarms_set_until_epoch_day")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")
        val GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
        val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
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

    // --- Чтение ---

    private fun Preferences.toSettings(): Settings = Settings(
        setupComplete = this[Keys.SETUP_COMPLETE] ?: false,
        targetType = this[Keys.TARGET_TYPE]?.let { parseTargetType(it) },
        targetId = if (contains(Keys.TARGET_ID)) this[Keys.TARGET_ID] else null,
        targetName = this[Keys.TARGET_NAME] ?: "",
        wakeOffsetMinutes = this[Keys.WAKE_OFFSET_MINUTES] ?: 90,
        autoAlarmEnabled = this[Keys.AUTO_ALARM_ENABLED] ?: true,
        mode = parseEnum(this[Keys.MODE], AlarmMode.STANDARD),
        weekAheadDays = this[Keys.WEEK_AHEAD_DAYS] ?: 7,
        semesterMin = this[Keys.SEMESTER_MIN]?.let { parseDate(it) },
        semesterMax = this[Keys.SEMESTER_MAX]?.let { parseDate(it) },
        lastFullSyncEpochMs = this[Keys.LAST_FULL_SYNC_EPOCH_MS] ?: 0L,
        themeMode = parseEnum(this[Keys.THEME_MODE], ThemeMode.SYSTEM),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        accentIndex = this[Keys.ACCENT_INDEX] ?: 0,
        calendarStyle = parseEnum(this[Keys.CALENDAR_STYLE], CalendarStyle.STANDARD),
        backgroundImagePath = this[Keys.BACKGROUND_IMAGE_PATH],
        backgroundOpacity = this[Keys.BACKGROUND_OPACITY] ?: 0.35f,
        weekAlarmsSetUntilEpochDay = this[Keys.WEEK_ALARMS_SET_UNTIL] ?: -1L,
        reminderEnabled = this[Keys.REMINDER_ENABLED] ?: false,
        reminderTime = this[Keys.REMINDER_TIME]?.let { parseTime(it) } ?: LocalTime.of(21, 0),
        glassEnabled = this[Keys.GLASS_ENABLED] ?: false,
        glassIntensity = this[Keys.GLASS_INTENSITY] ?: 0.5f
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
        this[Keys.MODE] = s.mode.name
        this[Keys.WEEK_AHEAD_DAYS] = s.weekAheadDays

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
        this[Keys.WEEK_ALARMS_SET_UNTIL] = s.weekAlarmsSetUntilEpochDay

        this[Keys.REMINDER_ENABLED] = s.reminderEnabled
        this[Keys.REMINDER_TIME] = s.reminderTime.toString()
        this[Keys.GLASS_ENABLED] = s.glassEnabled
        this[Keys.GLASS_INTENSITY] = s.glassIntensity
    }

    // --- Утилиты парсинга (битые значения молча заменяются дефолтом) ---

    private fun parseTargetType(name: String): TargetType? =
        runCatching { TargetType.valueOf(name) }.getOrNull()

    private inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
        if (name == null) default
        else runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    private fun parseTime(iso: String): LocalTime? = runCatching { LocalTime.parse(iso) }.getOrNull()
}
