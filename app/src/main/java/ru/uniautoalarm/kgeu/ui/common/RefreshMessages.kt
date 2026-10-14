package ru.uniautoalarm.kgeu.ui.common

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Тексты результата обновления расписания для снекбаров.
 * Раньше обновление молчало и в успехе, и в ошибке — не было понятно,
 * сработала ли кнопка вообще и почему данные не изменились.
 */
fun refreshSuccessMessage(count: Int): String =
    if (count == 0) "Обновлено: пар на этой неделе нет"
    else "Расписание обновлено — пар на неделе: $count"

/** Причина по типу исключения: сеть / сервер / прочее (текст из исключения). */
fun refreshErrorMessage(e: Throwable): String = when (e) {
    is UnknownHostException, is SocketTimeoutException ->
        "Нет связи с kabinet.kgeu.ru — показан сохранённый кэш"
    is IOException ->
        "Сеть недоступна — показан сохранённый кэш"
    else ->
        "Ошибка обновления: ${e.message ?: "неизвестная причина"}"
}

fun refreshMessage(result: Result<Int>): String =
    result.fold(::refreshSuccessMessage, ::refreshErrorMessage)
