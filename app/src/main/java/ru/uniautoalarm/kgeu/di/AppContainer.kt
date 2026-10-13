package ru.uniautoalarm.kgeu.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.uniautoalarm.kgeu.alarm.StockAlarmSetter
import ru.uniautoalarm.kgeu.data.db.AppDatabase
import ru.uniautoalarm.kgeu.data.remote.KgeuApi
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository
import ru.uniautoalarm.kgeu.domain.AlarmPlanner

/** Ручной DI: один контейнер зависимостей на процесс, живёт в AutoAlarmApp. */
class AppContainer(context: Context) {

    /** Application-контекст: для планировщиков/репозиториев вне экранов. */
    val appContext: Context = context.applicationContext

    /** Глобальный scope движка: переживает экраны, ошибка одного ребёнка не роняет остальных. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getInstance(context) }
    val settings by lazy { SettingsRepository(context) }
    val kgeuApi by lazy { KgeuApi() }
    val scheduleRepository by lazy { ScheduleRepository(database, kgeuApi, settings, appScope) }
    val planner by lazy { AlarmPlanner(settings, scheduleRepository, database.customEventDao(), database.exceptionDao()) }
    val stockAlarms by lazy { StockAlarmSetter(context.applicationContext, planner, settings) }
}
