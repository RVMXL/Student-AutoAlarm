package ru.uniautoalarm.kgeu.ui.common

import android.content.Context
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.remote.KgeuApiException

/**
 * Тексты результата обновления расписания для снекбаров.
 * Раньше обновление молчало и в успехе, и в ошибке — не было понятно,
 * сработала ли кнопка вообще и почему данные не изменились.
 *
 * Строки берутся из ресурсов с учётом языка приложения (обёртывание контекста),
 * поэтому функции принимают Context вместо чистых строк.
 */

fun refreshSuccessMessage(context: Context, count: Int): String =
    if (count == 0) {
        LocaleHelper.string(context, R.string.refresh_empty_week)
    } else {
        LocaleHelper.string(context, R.string.refresh_ok_count, count)
    }

/** Причина по типу исключения: сеть / сервер / прочее (текст из исключения). */
fun refreshErrorMessage(context: Context, e: Throwable): String = when (e) {
    is UnknownHostException, is SocketTimeoutException ->
        LocaleHelper.string(context, R.string.refresh_no_connection)
    is IOException ->
        LocaleHelper.string(context, R.string.refresh_network)
    else ->
        LocaleHelper.string(
            context, R.string.refresh_error,
            e.message ?: LocaleHelper.string(context, R.string.err_unknown_cause)
        )
}

fun refreshMessage(context: Context, result: Result<Int>): String =
    result.fold(
        onSuccess = { refreshSuccessMessage(context, it) },
        onFailure = { refreshErrorMessage(context, it) }
    )
