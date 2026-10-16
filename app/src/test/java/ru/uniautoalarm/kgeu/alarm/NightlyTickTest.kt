package ru.uniautoalarm.kgeu.alarm

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.12.0 (AL-23): страховочные тики ночной сверки перенесены с 06:00 на
 * 01:00/02:00/03:00 ночи — у живущих далеко от корпуса подъём бывает в 05:00–06:00
 * (к паре в 08:00), и старая страховка 06:00 срабатывала уже ПОСЛЕ пробуждения.
 *
 * Тесты фиксируют состав тиков (документируют намерение) и граничные случаи
 * чистого вычисления следующего момента [NightlyScheduler.nextTick] — именно
 * эта функция отвечает за «сегодня/завтра» при перепостановке цепочки тиков.
 */
class NightlyTickTest {

    // ── состав тиков ────────────────────────────────────────────────────────────

    @Test
    fun `backup ticks are 1 2 and 3 oclock at night`() {
        assertEquals(listOf(1 to 0, 2 to 0, 3 to 0), NightlyScheduler.BACKUP_TICKS)
    }

    @Test
    fun `main tick is 00-05`() {
        assertEquals(0, NightlyScheduler.NIGHT_HOUR)
        assertEquals(5, NightlyScheduler.NIGHT_MINUTE)
    }

    // ── nextTick: ближайшее наступление HH:MM ───────────────────────────────────

    @Test
    fun `time not passed yet - tick today`() {
        // 00:10 ночи: основной тик 00:05 уже прошёл (→ завтра), но страховка
        // 01:00 — ещё впереди и остаётся САМЫМ РАННИМ шансом наверстать ночь
        val now = LocalDateTime.parse("2026-01-15T00:10:00")
        assertEquals(
            LocalDateTime.parse("2026-01-15T01:00:00"),
            NightlyScheduler.nextTick(now, 1, 0)
        )
    }

    @Test
    fun `time already passed - tick tomorrow`() {
        // 03:10: все ночные тики сегодняшней ночи прошли — вся цепочка на завтра
        val now = LocalDateTime.parse("2026-01-15T03:10:00")
        assertEquals(
            LocalDateTime.parse("2026-01-16T01:00:00"),
            NightlyScheduler.nextTick(now, 1, 0)
        )
        assertEquals(
            LocalDateTime.parse("2026-01-16T03:00:00"),
            NightlyScheduler.nextTick(now, 3, 0)
        )
    }

    @Test
    fun `exact moment counts as passed - tick tomorrow`() {
        // «сейчас» == момент тика: уходит на завтра, мгновенного ре-тика нет
        val now = LocalDateTime.parse("2026-01-15T02:00:00")
        assertEquals(
            LocalDateTime.parse("2026-01-16T02:00:00"),
            NightlyScheduler.nextTick(now, 2, 0)
        )
    }

    @Test
    fun `late evening - main and backup both tomorrow`() {
        val now = LocalDateTime.parse("2026-01-15T23:50:00")
        assertEquals(
            LocalDateTime.parse("2026-01-16T00:05:00"),
            NightlyScheduler.nextTick(
                now, NightlyScheduler.NIGHT_HOUR, NightlyScheduler.NIGHT_MINUTE
            )
        )
        assertEquals(
            LocalDateTime.parse("2026-01-16T01:00:00"),
            NightlyScheduler.nextTick(now, 1, 0)
        )
    }

    @Test
    fun `just after midnight - later backup ticks still today`() {
        // 00:10: основной тик (00:05) ушёл на завтра, страховки 02:00 и 03:00
        // сегодняшней ночи ещё впереди — пропуск основного тика компенсируется
        assertEquals(
            LocalDateTime.parse("2026-01-15T02:00:00"),
            NightlyScheduler.nextTick(now = LocalDateTime.parse("2026-01-15T00:10:00"), hour = 2, minute = 0)
        )
        assertEquals(
            LocalDateTime.parse("2026-01-15T03:00:00"),
            NightlyScheduler.nextTick(now = LocalDateTime.parse("2026-01-15T00:10:00"), hour = 3, minute = 0)
        )
    }
}
