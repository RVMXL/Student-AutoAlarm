package ru.uniautoalarm.kgeu.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository
import ru.uniautoalarm.kgeu.domain.AlarmPlanner
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/** Итог попытки снять будильники приложения. */
data class DismissResult(
    /** Сколько команд DISMISS_ALARM удалось отправить (startActivity не бросил). */
    val sent: Int,
    /** Общее число попыток (метки «Авто: Пн…Вс» + метка последнего разового). */
    val total: Int,
    /** Активен движок «В приложении» — собственные будильники сняты гарантированно (cancel). */
    val inAppCancelled: Boolean = false,
)

/**
 * 0.10.0 (баг пользователя): приложение больше НИКОГДА не открывает стоковые часы
 * само по себе. Раньше при отказе оболочки принимать DISMISS_ALARM (ColorOS/Realme UI
 * делают это всегда) срабатывал автоматический фолбэк SHOW_ALARMS — часы внезапно
 * выпрыгивали на передний план, в том числе посреди смены движка на «В приложении».
 * Теперь неуспех снятия объясняется снекбаром/уведомлением, а список будильников часов
 * открывается ТОЛЬКО явной кнопкой пользователя — [openAlarmList].
 */

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
     *
     * Логика 0.8.0 («одна ночь вперёд»): только РАЗОВЫЕ будильники (без EXTRA_DAYS) —
     * никаких повторов на день недели, которые потом приходилось выключать руками.
     * Одноразовый SET_ALARM звенит в ближайшее наступление времени подъёма, поэтому:
     *  — сегодня (подъём ещё впереди): безопасно — сработает сегодня;
     *  — завтра: безопасно только если время подъёма уже прошло сегодня (иначе
     *    разовый зазвенел бы СЕГОДНЯ — баг 0.6.0/0.7.0); до наступления этого
     *    времени постановку откладываем до ночной сверки 00:05 (skip_deferred_to_night);
     *  — дальше завтра не ставим (горизонт задаёт AlarmGateway.findNextUpcoming).
     *
     * @return план, если будильник реально передан часам; null — пар нет / постановка
     *         отложена до ночи / часы не приняли команду.
     */
    suspend fun setForDate(
        date: LocalDate,
        notify: Boolean = true,
        /** AL-17 (0.11.0): ночная сверка глушит «отложено до ночи» — иначе тик 00:05 и
         *  страховка 06:00 заваливали шторку ежедневным спамом в ожидании постановки. */
        notifyDeferred: Boolean = true,
    ): AlarmPlan? =
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

            val now = LocalDateTime.now()

            // Решение доставки — чистая функция (см. [deliveryDecision]): юнит-тест
            // фиксирует правило «сегодня/безопасное завтра/откладка до ночи» (0.8.0)
            // и spill-случай своего события в 00:30 (wakeAt вчера — AL-6).
            val delivered = when (deliveryDecision(now, plan.wakeAt)) {
                Delivery.PAST -> {
                    // AL-13 (0.11.0): молчаливый null больше не бывает — логируем и объясняем
                    // (уведомления нет: PAST — штатный spill-случай своего события в 00:30,
                    // а не авария; статус виден в карточке «Мои будильники»)
                    Log.i(TAG, "Момент подъёма $plan.wakeAt уже прошёл — SET_ALARM не отправляю (AL-13)")
                    false
                }
                Delivery.TOO_FAR -> {
                    if (notify) {
                        Notifications.alarmSkipped(
                            context, date, LocaleHelper.string(context, R.string.skip_too_far)
                        )
                    }
                    false
                }
                Delivery.DEFER_TO_NIGHT -> {
                    // разовый будильник на завтра, поставленный сейчас, зазвенел бы СЕГОДНЯ:
                    // время подъёма ещё не наступило. Откладываем до ночной сверки 00:05 —
                    // она поставит его в правильный день (логика 0.8.0)
                    if (notify && notifyDeferred) {
                        Notifications.alarmSkipped(
                            context, date,
                            LocaleHelper.string(context, R.string.skip_deferred_to_night)
                        )
                    }
                    false
                }
                Delivery.DELIVER -> deliver(plan.wakeAt.hour, plan.wakeAt.minute, plan.label, s.alarmShowClockUi)
            }

            if (delivered) {
                if (notify) Notifications.alarmSet(context, plan)
                // маркеры для дедупликации мгновенной постановки (см. setNextUpcoming):
                // без них тумблер днём + ночная сверка 00:05 ставили бы два одинаковых будильника;
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
                null
            }
        }

    /**
     * Снять будильники приложения в стоковых часах: официальный канал DISMISS_ALARM
     * по точным меткам — метка последнего разового («Авто: дисциплина») и, для очистки
     * наследия 0.3.1…0.7.x, недельные «Авто: Пн…Вс» (режим «на несколько дней вперёд» удалён в 0.8.0).
     *
     * Поиск по метке в часах — ТОЧНОЕ совпадение (AOSP DeskClock: WHERE label = ?),
     * поэтому каждая метка отправляется отдельным интентом (баг 0.3.1: метка «Авто»
     * не совпадала ни с чем; исправлено в 0.4.0, см. RELEASE-0.4.0).
     *
     * 0.10.0: автоматический фолбэк SHOW_ALARMS УДАЛЁН (баг пользователя: приложение
     * само выпрыгивало в стоковые часы, когда оболочка не принимала DISMISS —
     * ColorOS/Realme UI не принимают его никогда). Неуспех теперь означает только
     * sent = 0 — итог объясняется снекбаром/уведомлением, а в часах пользователь
     * попадает только по собственной кнопке ([openAlarmList]).
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

        // 0.10.0: никаких авто-переходов в часы при отказе оболочки — итог объясняется
        // снекбаром/уведомлением; список часов открывает только явная кнопка [openAlarmList]

        // маркеры сбрасываем в любом исходе: после команды DISMISS (или после того, как
        // пользователь снял будильники вручную) старые маркеры уже не соответствуют
        // реальности, и «уже стоит» было бы ложью
        settings.update {
            it.copy(
                lastScheduledWakeEpochMinute = -1L,
                lastScheduledLabel = ""
            )
        }
        DismissResult(sent, labels.size)
    }

    /**
     * ЯВНО открыть список будильников стоковых часов (SHOW_ALARMS) — только по
     * нажатию пользователя (карточка «Мои будильники»/настройки). 0.10.0: это
     * единственный путь из приложения в часы — автоматических переходов больше нет.
     * @return true, если часы открылись.
     */
    fun openAlarmList(): Boolean {
        val clockPackage = detectClockPackage()
        val intents = buildList {
            if (clockPackage != null) add(Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage(clockPackage))
            add(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }
        return intents.any { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Тихо снять ОДИН будильник по точной метке (ночная сверка 00:05: будильник
     * на завтра отменили/перенесли замены — снимаем старый, не трогая маркеры партии).
     * Никаких фолбэков и UI: ночью нельзя выскакивать на передний план.
     * @return true, если интент принят часами (о фактическом снятии контракт не сообщает).
     */
    suspend fun dismissSingleLabel(label: String): Boolean = withContext(Dispatchers.Default) {
        if (label.isBlank()) return@withContext false
        val clockPackage = detectClockPackage()
        try {
            context.startActivity(
                Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    if (clockPackage != null) setPackage(clockPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            Log.i(TAG, "DISMISS по метке не принят: ${e.message}")
            false
        }
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
    ): Boolean {
        val target = detectClockPackage()
        val base = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, !showClockUi)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            // 0.8.0: EXTRA_DAYS (повтор по дням недели) больше не используется —
            // только разовые будильники в пределах сегодня/завтра (логика «одна ночь вперёд»)
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
         * Чистая функция решения доставки: пригодна для юнит-тестов без Android.
         * Правило 0.8.0: сегодня — разовый; завтра — только когда время подъёма
         * сегодня УЖЕ прошло (иначе одноразовый зазвенел бы сегодня); дальше — никогда.
         */
        fun deliveryDecision(now: LocalDateTime, wakeAt: LocalDateTime): Delivery {
            if (!wakeAt.isAfter(now)) return Delivery.PAST
            val daysAhead = ChronoUnit.DAYS.between(now.toLocalDate(), wakeAt.toLocalDate())
            return when {
                daysAhead > 1L -> Delivery.TOO_FAR
                daysAhead == 1L && wakeAt.toLocalTime().isAfter(now.toLocalTime()) ->
                    Delivery.DEFER_TO_NIGHT
                else -> Delivery.DELIVER
            }
        }

        /**
         * Метки недельных будильников прежнего режима «на несколько дней вперёд»
         * (удалён в 0.8.0) — нужны ТОЛЬКО для очистки наследия при обновлении:
         * единый источник с тем, что было поставлено в 0.3.1…0.7.x (DISMISS_ALARM
         * ищет ТОЧНОЕ совпадение: AOSP DeskClock: WHERE label = ?). В 0.3.1 кнопка
         * отправляла метку «Авто», под которую не подходило ни «Авто: Пн», ни
         * что-либо ещё — часы молча ничего не отключали (см. RELEASE-0.4.0).
         *
         * Метки намеренно не локализуются: они пишутся в стоковые часы и
         * сопоставляются байт-в-байт при DISMISS — смена языка приложения
         * не должна ломать совпадение SET/DISMISS.
         */
        val WEEK_AHEAD_LABELS: List<String> =
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").map { "Авто: $it" }
    }
}

/**
 * Решение доставки разового SET_ALARM (0.8.0, логика «одна ночь вперёд»),
 * возвращаемое [StockAlarmSetter.deliveryDecision].
 */
enum class Delivery {
    /** Ставим прямо сейчас (сегодня либо безопасное завтра). */
    DELIVER,

    /** Завтрашняя пара, время подъёма сегодня ещё не прошло: одноразовый
     *  звенел бы СЕГОДНЯ — откладываем до ночной сверки 00:05. */
    DEFER_TO_NIGHT,

    /** Пара дальше завтра: SET_ALARM не умеет в дату — не ставим. */
    TOO_FAR,

    /** Момент уже прошёл (в т.ч. spill 00:30 с офсетом в прошлый день). */
    PAST,
}
