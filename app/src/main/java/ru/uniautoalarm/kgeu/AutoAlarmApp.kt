package ru.uniautoalarm.kgeu

import android.app.Application
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.work.WorkScheduler

/** Application: создаёт контейнер зависимостей и восстанавливает фоновые задачи. */
class AutoAlarmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Ночной тик на 00:05 + периодическая синхронизация WorkManager (оба идемпотентны)
        NightlyScheduler.scheduleNext(this)
        WorkScheduler.ensurePeriodic(this)
    }
}
