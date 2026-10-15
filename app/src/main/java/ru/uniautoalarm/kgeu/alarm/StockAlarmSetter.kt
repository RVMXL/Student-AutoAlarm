package ru.uniautoalarm.kgeu.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.WeekAheadResult
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository
import ru.uniautoalarm.kgeu.domain.AlarmPlanner
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/** Итог попытки снять будильники приложения в стоковых часах. */
data class DismissResult(
    /** Сколько команд DISMISS_ALARM удалось отправить (startActivity не бросил). */
    val sent: Int,
    /** Общее число попыток (метки «Авто: Пн…Вс» + метка последнего разового). */
    val total: Int,
    /** Команды не приняты — открыт список будильников часов (SHOW_ALARMS) как фолбэк. */
    val openedList: Boolean,
)

/**
 * Постановка будильника в СТОКОВОЕ приложение часов через публичный intent
 * android.intent.action.SET_ALARM. Своих будильников нет — сигнал звенит там,
 * где пользователь привык.
 *
 * Тактика против оболочек, молча глушащих неявный интент (ColorOS/Realme UI,
 * часть MIUI — см. разбор Gemini в docs):
 * 1) автопоиск установленного пакета часов по списку известных OEM-пакетов;
 * 2) явный setPackage(найденный) — система ОБЯЗАНА доставить интент по адресу;
 * 3) EXTRA_SKIP_UI = false, если включён тумблер «показывать часы» (фон-постановка
 *    с SKIP_UI=true на ColorOS считается подозрительной и блокируется молча);
 * 4) сбой явного запуска → откат к неявному интенту → сбой и его → уведомление
 *    с подсказкой включить «Напоминание о будильнике».
 */
class StockAlarmSetter(
    private val context: Context,
    private val planner: AlarmPlanner,
    private val settings: SettingsRepository,
) {

    /**
     * Известные пакеты стоковых часов (в порядке приоритета). Видимость на Android 11+
     * обеспечена <queries><package> в манифесте — без них getPackageInfo() рапортует
     * «не установлено» даже при наличии часов.
     */
    private val knownClockPackages = listOf(
        "com.oplus.alarmclock",             // Oppo / OnePlus / Realme — ColorOS 13+, Realme UI
        "com.coloros.alarmclock",           // старые ColorOS
        "com.sec.android.app.clockpackage", // Samsung One UI
        "com.google.android.deskclock",     // Pixel / Motorola / стоковый AOSP
        "com.android.deskclock",            // MIUI/HyperOS, EMUI, FuntouchOS (стандартное имя)
        "com.huawei.deskclock",             // Huawei (отдельный пакет)
        "com.transsion.deskclock"           // Tecno / Infinix / Itel
    )

    /** Пакет установленных часов из известного списка или null. */
    fun detectClockPackage(): String? {
        val pm = context.packageManager
        return knownClockPackages.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                Log.w(TAG, "probe $pkg: ${e.message}")
                false
            }
        }
    }

    /** Есть ли на устройстве часы: явный пакет из списка ИЛИ декларирование SET_ALARM. */
    fun canSet(): Boolean {
        if (detectClockPackage() != null) return true
        return try {
            val pm = context.packageManager
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(alarmIntent(), PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(alarmIntent(), 0)
            }
            resolved != null
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось проверить обработчик SET_ALARM: ${e.message}")
            false
        }
    }

    /**
     * Поставить будильник на [date] по плану планировщика.
     * @return план, если будильник реально передан часам;
     *         null — пар нет / часы не приняли команду / сбой запуска.
     */
    suspend fun setForDate(date: LocalDate, notify: Boolean = true): AlarmPlan? =
        withContext(Dispatchers.Default) {
            val s = settings.current()
            // resolveActivity ненадёжен на части оболочек: отрицательный ответ — НЕ повод
            // отказываться, пробуем ставить (пакетный таргетинг выше по надёжности).
            if (!canSet()) {
                Log.w(TAG, "Обработчик SET_ALARM не обнаружен — пробуем поставить всё равно")
            }

            val plan = planner.planFor(date)
            if (plan == null) {
                if (notify) {
                    Notifications.alarmSkipped(
                        context, date, LocaleHelper.string(context, R.string.skip_no_lessons)
                    )
                }
                return@withContext null
            }

            if (deliver(plan.wakeAt.hour, plan.wakeAt.minute, plan.label, s.alarmShowClockUi)) {
                if (notify) Notifications.alarmSet(context, plan)
                // маркеры для дедупликации мгновенной постановки (см. setNextUpcoming):
                // без них тумблер днём + ночной тик 00:05 ставили бы два одинаковых будильника;
                // метка нужна каналу DISMISS — снять разовый будильник по точному совпадению
                settings.update {
                    it.copy(
                        lastScheduledWakeEpochMinute =
                            plan.wakeAt.toLocalDate().toEpochDay() * 1440L +
                                plan.wakeAt.hour * 60L + plan.wakeAt.minute,
                        lastScheduledLabel = plan.label
                    )
                }
                plan
            } else {
                if (notify) {
                    Notifications.alarmSkipped(
                        context, date,
                        LocaleHelper.string(context, R.string.skip_clock_refused)
                    )
                }
                null
            }
        }

    /**
     * Мгновенная постановка: ближайшее ПРЕДСТОЯЩЕЕ пробуждение — сегодня, если время
     * подъёма ещё не прошло, иначе завтра и далее в пределах кэша расписания.
     * Вызывается при включении тумблера и смене режима: раньше будильник на ближайшее
     * утро появлялся только после ночного тика 00:05 — при включении днём он «вообще не ставился».
     * Повторная постановка того же момента блокируется маркером lastScheduledWakeEpochMinute:
     * SET_ALARM каждый раз создаёт НОВЫЙ будильник, а не обновляет существующий.
     * @return план, если будильник стоит (поставлен сейчас или уже стоял); null — пар нет / часы отказали.
     */
    suspend fun setNextUpcoming(notify: Boolean = true): AlarmPlan? {
        val now = LocalDateTime.now()
        val s = settings.current()
        for (i in 0..13) { // две недели — горизонт кэша расписания
            val date = now.toLocalDate().plusDays(i.toLong())
            val plan = planner.planFor(date) ?: continue
            if (!plan.wakeAt.isAfter(now)) continue
            val wakeEpochMinute = plan.wakeAt.toLocalDate().toEpochDay() * 1440L +
                plan.wakeAt.hour * 60L + plan.wakeAt.minute
            if (wakeEpochMinute == s.lastScheduledWakeEpochMinute) {
                // будильник на этот момент уже стоит — дубликат не плодим, только подтверждаем
                if (notify) Notifications.alarmSet(context, plan)
                return plan
            }
            return setForDate(date, notify)
        }
        if (notify) {
            Notifications.alarmSkipped(
                context, now.toLocalDate(),
                LocaleHelper.string(context, R.string.skip_no_upcoming)
            )
        }
        return null
    }

    /**
     * Режим «на несколько дней вперёд»: ПОВТОРЯЮЩИЕСЯ будильники по дням недели.
     *
     * Через ACTION_SET_ALARM нельзя поставить будильник «на дату»: интент всегда ставит
     * на ближайшее срабатывание (все дни партии сваливались на завтра — баг 0.3.0).
     * Единственный честный многодневный формат контракта — EXTRA_DAYS: один
     * повторяющийся еженедельно будильник на каждый учебный день недели, время подъёма —
     * из актуального расписания (с учётом «подъёма под каждую пару»).
     *
     * Дедупликация по сигнатуре «день:время» в настройках: фоновые перезапуски (синк,
     * перезагрузка) не плодят дубликаты; изменение расписания переставляет партию.
     * Метка «Авто: Пн» — под кнопку «Отключить будильники приложения» (DISMISS по метке).
     *
     * @param force — пропустить сигнатуру (кнопка «Поставить сейчас»: пользователь
     *              мог вручную убрать будильники в часах и просит поставить заново).
     */
    suspend fun setWeekAhead(
        fromDate: LocalDate = LocalDate.now(),
        notify: Boolean = true,
        force: Boolean = false,
    ): WeekAheadResult {
        val s = settings.current()
        // окно не короче недели — иначе короткий горизонт теряет часть учебных дней
        val horizon = maxOf(s.weekAheadDays, 7)

        // 1) пробуждения по дням недели из актуального расписания (первый кандидат в окне)
        data class DayWake(val dow: DayOfWeek, val wakeMinutes: Int)
        val byDow = LinkedHashMap<Int, DayWake>()
        for (i in 0 until horizon) {
            val date = fromDate.plusDays(i.toLong())
            val plan = planner.planFor(date) ?: continue
            if (!plan.wakeAt.isAfter(LocalDateTime.now())) continue
            byDow.putIfAbsent(
                date.dayOfWeek.value,
                DayWake(date.dayOfWeek, plan.wakeAt.hour * 60 + plan.wakeAt.minute)
            )
        }
        val until = fromDate.plusDays(s.weekAheadDays.toLong())

        if (byDow.isEmpty()) {
            if (notify) {
                Notifications.alarmSkipped(
                    context, fromDate,
                    LocaleHelper.string(context, R.string.skip_no_upcoming)
                )
            }
            return WeekAheadResult(0, alreadyUpToDate = false)
        }

        // 2) сигнатура — защита от дубликатов при повторных запусках
        val signature = byDow.entries.joinToString(";") { "${it.key}:${it.value.wakeMinutes}" }
        if (!force && signature == s.weekAlarmsSignature) {
            // уже стоят и актуальны; чип UI обновляем без переустановки
            settings.update { it.copy(weekAlarmsSetUntilEpochDay = until.toEpochDay()) }
            if (notify) Notifications.weekAlreadySet(context)
            return WeekAheadResult(0, alreadyUpToDate = true)
        }

        // 3) повторяющийся будильник на каждый учебный день недели
        var count = 0
        for ((_, dw) in byDow) {
            if (deliver(
                    hour = dw.wakeMinutes / 60,
                    minute = dw.wakeMinutes % 60,
                    // метка из единого источника: DISMISS_ALARM по метке ищет ТОЧНОЕ
                    // совпадение — метки SET и кнопки «Отключить» обязаны совпадать байт в байт
                    label = WEEK_AHEAD_LABELS[dw.dow.value - 1],
                    showClockUi = s.alarmShowClockUi,
                    repeatCalendarDays = listOf(calendarDayOf(dw.dow))
                )
            ) count++
        }
        if (count > 0) {
            settings.update {
                it.copy(
                    weekAlarmsSignature = signature,
                    weekAlarmsSetUntilEpochDay = until.toEpochDay()
                )
            }
        }
        if (notify) Notifications.weekSet(context, count, fromDate, until)
        return WeekAheadResult(count, alreadyUpToDate = false)
    }

    /** java.time.DayOfWeek → константа java.util.Calendar для EXTRA_DAYS (Sun=1 .. Sat=7). */
    private fun calendarDayOf(dow: DayOfWeek): Int =
        if (dow == DayOfWeek.SUNDAY) Calendar.SUNDAY else dow.value + 1

    /**
     * Снять будильники приложения в стоковых часах: официальный канал DISMISS_ALARM
     * по точным меткам — партия «Авто: Пн…Вс» и метка последнего разового («Авто: дисциплина»).
     *
     * Поиск по метке в часах — ТОЧНОЕ совпадение (AOSP DeskClock: WHERE label = ?),
     * поэтому каждая метка отправляется отдельным интентом (баг 0.3.1: метка «Авто»
     * не совпадала ни с чем; исправлено в 0.4.0, см. RELEASE-0.4.0).
     *
     * Ограничение системы: контракт не возвращает результат («сколько отключено»),
     * а часть OEM-часов (Realme UI/ColorOS, старые MIUI) интент DISMISS_ALARM вообще
     * не принимают — startActivity бросит исключение. Тогда честно открываем список
     * будильников часов (SHOW_ALARMS), чтобы пользователь снял метки «Авто:» вручную.
     *
     * После успешной отправки команд маркеры постановки (эпоха-минута, метка, сигнатура
     * партии) сбрасываются: следующий тумблер/ночной тик поставит будильники заново,
     * а не «промолчит», считая их уже стоящими (баг 0.5.x: выключенный тумблер не
     * снимал будильники, а включённый заново — не ставил их после ручного удаления).
     */
    suspend fun dismissAppAlarms(): DismissResult = withContext(Dispatchers.Default) {
        val s = settings.current()
        val clockPackage = detectClockPackage()
        val labels = buildList {
            addAll(WEEK_AHEAD_LABELS)
            if (s.lastScheduledLabel.isNotBlank() && s.lastScheduledLabel !in this) {
                add(s.lastScheduledLabel)
            }
        }

        var sent = 0
        for (label in labels) {
            sent += try {
                context.startActivity(
                    Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        // тихо, без UI на каждый будильник: это фоновая гигиена, не кнопка
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        if (clockPackage != null) setPackage(clockPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                1
            } catch (_: Exception) {
                0
            }
        }

        val result = if (sent > 0) {
            DismissResult(sent, labels.size, openedList = false)
        } else {
            // Часы не приняли ни одной команды — ограничение оболочки; открываем список вручную
            val intents = buildList {
                if (clockPackage != null) add(Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage(clockPackage))
                add(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            }
            val opened = intents.any { intent ->
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (_: Exception) {
                    false
                }
            }
            DismissResult(0, labels.size, openedList = opened)
        }

        // маркеры сбрасываем в любом исходе: после команды DISMISS (или после того, как
        // пользователь снял будильники в открытом списке) старые маркеры уже не соответствуют
        // реальности, и «уже стоит» было бы ложью
        settings.update {
            it.copy(
                lastScheduledWakeEpochMinute = -1L,
                lastScheduledLabel = "",
                weekAlarmsSignature = "",
                weekAlarmsSetUntilEpochDay = -1L
            )
        }
        result
    }

    /**
     * Тестовый будильник через [minutesFromNow] минут (кнопка «Проверить будильник» в настройках).
     * Ставится напрямую, без планировщика, с меткой «Тест».
     * @return true, если будильник передан часам.
     */
    suspend fun setTestAlarm(minutesFromNow: Int = 2): Boolean {
        val at = LocalDateTime.now().plusMinutes(minutesFromNow.toLong())
        val showUi = settings.current().alarmShowClockUi
        return deliver(
            at.hour, at.minute,
            LocaleHelper.string(context, R.string.test_alarm_label), showUi
        )
    }

    /**
     * Доставка интента SET_ALARM: явный пакет → неявный фолбэк.
     * @return true, если стартовал хотя бы один вариант (исключений нет).
     */
    private fun deliver(
        hour: Int,
        minute: Int,
        label: String,
        showClockUi: Boolean,
        repeatCalendarDays: List<Int>? = null,
    ): Boolean {
        val target = detectClockPackage()
        val base = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, !showClockUi)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            if (repeatCalendarDays != null) {
                // повтор по дням недели: EXTRA_DAYS — ArrayList констант Calendar
                putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatCalendarDays))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // контекст приложения — метка обязательна
        }

        // 1) явный таргетинг найденного пакета — ColorOS/OEM обязаны принять по адресу
        if (target != null) {
            try {
                context.startActivity(base.apply { setPackage(target) })
                Log.i(TAG, "Будильник передан часам $target")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Явный запуск $target не удался: ${e.message} — пробую неявный")
            }
        }

        // 2) фолбэк: неявный интент (сработает на стоковом Android и часах с intent-filter)
        return try {
            context.startActivity(base.apply { setPackage(null) })
            Log.i(TAG, "Будильник передан часам по неявному интенту")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Часы не приняли SET_ALARM даже неявно: ${e.message}")
            false
        }
    }

    private fun alarmIntent(): Intent = Intent(AlarmClock.ACTION_SET_ALARM)

    companion object {
        private const val TAG = "StockAlarmSetter"

        /**
         * Метки повторяющихся будильников режима «на несколько дней вперёд» —
         * единый источник для SET_ALARM и кнопки «Отключить будильники приложения»
         * (DISMISS_ALARM): поиск по метке в часах — ТОЧНОЕ совпадение (AOSP DeskClock:
         * WHERE label = ?), а не «начинается с». В 0.3.1 кнопка отправляла метку
         * «Авто», под которую не подходило ни «Авто: Пн», ни что-либо ещё — часы
         * молча ничего не отключали (см. RELEASE-0.4.0).
         *
         * Метки намеренно не локализуются: они пишутся в стоковые часы и
         * сопоставляются байт-в-байт при DISMISS — смена языка приложения
         * не должна ломать совпадение SET/DISMISS.
         */
        val WEEK_AHEAD_LABELS: List<String> =
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").map { "Авто: $it" }
    }
}
