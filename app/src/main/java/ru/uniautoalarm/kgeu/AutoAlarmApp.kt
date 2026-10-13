package ru.uniautoalarm.kgeu

import android.app.Application
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.alarm.ReminderScheduler
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.work.WorkScheduler

/** Application: создаёт контейнер зависимостей и восстанавливает фоновые задачи. */
class AutoAlarmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Ночной тик на 00:05, напоминание о будильнике и периодический sync WorkManager
        // (всё идемпотентно, планировщики сами читают настройки)
        NightlyScheduler.scheduleNext(this)
        ReminderScheduler.scheduleNext(this)
        WorkScheduler.ensurePeriodic(this)
    }
}
