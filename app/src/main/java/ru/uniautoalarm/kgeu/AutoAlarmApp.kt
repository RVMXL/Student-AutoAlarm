package ru.uniautoalarm.kgeu

import android.app.Application
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.alarm.ReminderScheduler
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.work.WorkScheduler

/** Application: создаёт контейнер зависимостей и восстанавливает фоновые задачи. */
class AutoAlarmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // SET-12 (0.11.0): фиксируем системную локаль ДО любых обёртываний —
        // иначе честный возврат на «язык системы» невозможен
        LocaleHelper.captureSystemLocale()
        container = AppContainer(this)
        // DATA-14 (0.10.0): миграции — в appScope контейнера, а не в ad-hoc scope:
        // общий SupervisorJob, и нет гонки отмирания анонимного scope с SyncWorker
        container.appScope.launch {
            // 0.7.1: одноразовый переезд на движок «В приложении» — SET_ALARM стоковых
            // часов не умеет «на дату», DISMISS на части оболочек молча игнорируется.
            runCatching { container.settings.migrate071AppEngine() }
            // 0.8.0: единая логика «одна ночь вперёд». Одноразовая очистка наследия
            // режима «на несколько дней вперёд»: недельные будильники «Авто: Пн…Вс»
            // в часах (DISMISS по меткам, оболочка может проигнорировать — тогда
            // остатки удаляются вручную) и слоты AlarmManager на даты партии.
            runCatching {
                val legacyUntil = container.settings.migrate080SingleNight()
                if (legacyUntil != null) {
                    if (legacyUntil >= 0) {
                        container.inAppAlarms.cancelRange(legacyUntil - 20, legacyUntil + 20)
                    }
                    container.alarm.dismissAll()
                    // после снятия наследия — ставим ближайшее пробуждение заново,
                    // чтобы обновившийся пользователь без тумблера не остался без будильника
                    val s = container.settings.current()
                    if (s.setupComplete && s.autoAlarmEnabled) {
                        container.alarm.setNextUpcoming(notify = false)
                    }
                }
            }
            // 0.9.0 (DATA-3): правила-исключения из 0.6.0 без приставки типа после
            // перехода на точную семантику молча сузились — расширяем их на все
            // типы той же дисциплины по кэшу расписания (одноразово, атомарный флаг)
            runCatching {
                if (container.settings.claimRulesMigration090()) {
                    container.scheduleRepository.migrateExceptionRulesToExactNames()
                }
            }
        }
        // Ночная сверка (00:05 + страховки 01:00–03:00 ночи), напоминание о будильнике и
        // периодический sync WorkManager (всё идемпотентно, планировщики сами читают настройки)
        NightlyScheduler.scheduleNext(this)
        ReminderScheduler.scheduleNext(this)
        WorkScheduler.ensurePeriodic(this)
    }
}
