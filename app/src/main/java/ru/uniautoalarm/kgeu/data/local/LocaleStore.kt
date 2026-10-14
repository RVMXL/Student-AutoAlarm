package ru.uniautoalarm.kgeu.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Хранилище выбранного языка интерфейса.
 *
 * Отдельно от DataStore (SettingsRepository) потому, что язык нужен СИНХРОННО
 * в Activity.attachBaseContext до первого кадра — DataStore асинхронный.
 * Пустая строка = «язык системы» (обёртывание контекста не применяется).
 */
object LocaleStore {

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    /** Тег языка («ru», «tt», «en», …) или пустая строка — язык системы. */
    fun language(context: Context): String {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TAG, "") ?: ""
    }

    /** Сохранить выбор языка; пустая строка — вернуть на язык системы. */
    fun setLanguage(context: Context, tag: String) {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TAG, tag).apply()
    }
}
