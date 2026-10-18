package ru.uniautoalarm.kgeu.alarm

import java.time.Instant
import java.time.ZoneId

/**
 * «Отложить» собственного будильника (0.22.0, движок «В приложении»).
 *
 * Зачем: на части оболочек (ColorOS/Realme UI, MIUI/HyperOS) локскрин прячет
 * кнопки действий уведомлений и не поднимает RingActivity поверх блокировки —
 * «Отключить» с заблокированного телефона недоступно. Действие «Отложить» в
 * звонящем уведомлении даёт ещё один системно-санкционированный элемент
 * управления там, где действия всё же рендерятся, и вообще — штатную функцию
 * будильника: тишина на [MINUTES] минут, потом звонок по полной цепочке
 * (уведомление → сервис → RingActivity), а не тихий «пропущен».
 *
 * Слот отложенного звона — REQ_SNOOZE (отдельно от REQ_ONE_SHOT: перевзвод
 * следующего будильника не должен затирать отложенный звонок).
 */
object SnoozePolicy {

    /** Фиксированный интервал отложения (минуты). */
    const val MINUTES = 5

    /** Момент повторного звона: [fromMs] + [minutes] минут. */
    fun atMs(fromMs: Long, minutes: Int = MINUTES): Long =
        fromMs + minutes * 60_000L

    /**
     * Маркер постановки (эпоха-минута, кодирование как в AlarmGateway:
     * epochDay * 1440 + hour * 60 + minute) из момента звона. Секунды/миллисекунды
     * отбрасываются — маркер хранит только минуту, карточка «Мои будильники»
     * и сверка ночной логики работают в минутном разрешении.
     */
    fun markerMinute(atMs: Long, zone: ZoneId): Long {
        val ldt = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDateTime()
        return ldt.toLocalDate().toEpochDay() * 1440L + ldt.hour * 60L + ldt.minute
    }
}
