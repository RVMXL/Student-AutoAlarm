package ru.uniautoalarm.kgeu.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.uniautoalarm.kgeu.ui.home.HomeViewModel

/**
 * 0.23.0 (Bug A): строка будильника в карточке «Мои будильники» прячется,
 * когда момент пробуждения ушёл в прошлое глубже грейс-окна.
 *
 * Сценарий пользователя: будильник снят через шторку, постановка следующего
 * не случилась (нет пар сегодня+завтра) — маркер оставался, и карточка
 * продолжала показывать прозвеневший будильник. Видимость = чистая функция
 * [HomeViewModel.wakeRowVisible] с минутным разрешением (как у маркера).
 */
class HomeWakeRowTest {

    /** Точка отсчёта: 20 000 минут эпохи (произвольный «сейчас»). */
    private val now = 20_000L

    @Test
    fun `future wake is visible`() {
        // завтра: +1440 минут
        assertTrue(HomeViewModel.wakeRowVisible(now + 1440, now))
    }

    @Test
    fun `just fired wake is visible`() {
        // прозвенел минуту назад — грейс-окно держит строку (живой звонок/первые минуты)
        assertTrue(HomeViewModel.wakeRowVisible(now - 1, now))
    }

    @Test
    fun `wake at grace boundary minute is visible`() {
        // ровно на границе окна (14 минут после) — ещё видим
        assertTrue(HomeViewModel.wakeRowVisible(now - 14, now))
    }

    @Test
    fun `wake past grace window is hidden`() {
        // 15 минут после пробуждения — строка устарела
        assertFalse(HomeViewModel.wakeRowVisible(now - 15, now))
    }

    @Test
    fun `wake from yesterday is hidden`() {
        // вчерашний маркер, который не успели почистить (отказ постановки/ребут)
        assertFalse(HomeViewModel.wakeRowVisible(now - 1440, now))
    }

    @Test
    fun `custom grace window widens visibility`() {
        // кастомное окно: 30 минут — строка на 20 минут старше ещё видна
        assertTrue(HomeViewModel.wakeRowVisible(now - 20, now, graceMinutes = 30))
        assertFalse(HomeViewModel.wakeRowVisible(now - 31, now, graceMinutes = 30))
    }
}
