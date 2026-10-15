package ru.uniautoalarm.kgeu.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.remote.KgeuGroup
import ru.uniautoalarm.kgeu.data.remote.KgeuTeacher
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/**
 * ViewModel мастера первичной настройки: роль → загрузка справочников КГЭУ →
 * выбор группы/преподавателя → смещение подъёма → сохранение настроек.
 *
 * Сеть выполняется в Dispatchers.IO внутри KgeuApi; здесь только оркестрация состояний.
 */
class SetupViewModel(private val container: AppContainer) : ViewModel() {

    /** Шаг 2: выбранная роль (студент/преподаватель); до выбора — null. */
    val role = MutableStateFlow<TargetType?>(null)

    /** Поисковый запрос на шаге 2 (фильтрация списка по подстроке имени). */
    val query = MutableStateFlow("")

    /** Идёт загрузка справочника. */
    val loading = MutableStateFlow(false)

    /** Человекочитаемая ошибка загрузки (null — ошибки нет). */
    val error = MutableStateFlow<String?>(null)

    /** Шаг 4: за сколько минут до первой пары будить (дефолт 90). */
    val offsetMinutes = MutableStateFlow(DEFAULT_OFFSET_MINUTES)

    private val _groups = MutableStateFlow<List<KgeuGroup>>(emptyList())
    val groups: StateFlow<List<KgeuGroup>> = _groups.asStateFlow()

    private val _teachers = MutableStateFlow<List<KgeuTeacher>>(emptyList())
    val teachers: StateFlow<List<KgeuTeacher>> = _teachers.asStateFlow()

    /** Выбранная цель (группа или преподаватель) — обязательна для завершения. */
    private val _selectedTarget = MutableStateFlow<Target?>(null)
    val selectedTarget: StateFlow<Target?> = _selectedTarget.asStateFlow()

    /** Защита от двойного тапа по «Готово». */
    private var completing = false

    init {
        // Повторный запуск мастера (кнопка «Сменить» в настройках): подтягиваем прежнее
        // смещение подъёма, чтобы «Готово» не сбрасывало его на дефолт.
        viewModelScope.launch {
            val saved = container.settings.current().wakeOffsetMinutes
            offsetMinutes.value = saved.coerceIn(MIN_OFFSET_MINUTES, MAX_OFFSET_MINUTES)
        }
    }

    /**
     * Загрузить справочник для [role] (вызов перезаписывает this.role).
     * Повторный вызов при уже загруженном/грузящемся списке — no-op,
     * поэтому безопасен и из LaunchedEffect, и из кнопки «Повторить».
     */
    fun loadLists(role: TargetType) {
        this.role.value = role
        if (loading.value) return
        val alreadyLoaded = when (role) {
            TargetType.STUDENT -> _groups.value.isNotEmpty()
            TargetType.TEACHER -> _teachers.value.isNotEmpty()
        }
        if (alreadyLoaded) return

        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                when (role) {
                    TargetType.STUDENT -> _groups.value = container.kgeuApi.groups()
                    TargetType.TEACHER -> _teachers.value = container.kgeuApi.teachers()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value =
                    LocaleHelper.string(container.appContext, R.string.setup_load_failed) + " " +
                    LocaleHelper.string(container.appContext, R.string.setup_check_internet)
            } finally {
                loading.value = false
            }
        }
    }

    /** Шаг 2: выбор группы (студент). */
    fun selectGroup(group: KgeuGroup) {
        _selectedTarget.value = Target(TargetType.STUDENT, group.id, group.name)
    }

    /** Шаг 2: выбор преподавателя. */
    fun selectTeacher(teacher: KgeuTeacher) {
        _selectedTarget.value = Target(TargetType.TEACHER, teacher.id, teacher.name)
    }

    /** Шаг 4: смещение подъёма в минутах (защищаем диапазоном слайдера). */
    fun setOffset(minutes: Int) {
        offsetMinutes.value = minutes.coerceIn(MIN_OFFSET_MINUTES, MAX_OFFSET_MINUTES)
    }

    /**
     * Завершить настройку. Цель обязательна: без неё — no-op.
     * Порядок: цель в настройки → setupComplete/wakeOffset/autoAlarmEnabled →
     * в scope движка: неделя на ближайшие дни → будильник на ближайшее утро сразу
     * (не дожидаясь ночного тика 00:05) → ночной планировщик → полный семестр;
     * UI сеть не ждёт → onDone().
     */
    fun completeSetup(onDone: () -> Unit) {
        if (completing) return
        val target = _selectedTarget.value ?: return
        completing = true
        viewModelScope.launch {
            val offset = offsetMinutes.value
            container.settings.applyTarget(target.type, target.id, target.name)
            container.settings.update {
                it.copy(
                    setupComplete = true,
                    wakeOffsetMinutes = offset,
                    autoAlarmEnabled = true
                )
            }
            container.appScope.launch {
                // неделя на ближайшие дни — чтобы сразу поставить будильник на ближайшее утро
                runCatching { container.scheduleRepository.refreshWeek(target, LocalDate.now()) }
                runCatching { container.scheduleRepository.refreshWeek(target, LocalDate.now().plusDays(1)) }
                // будильник сразу после онбординга, не дожидаясь ночного тика 00:05
                runCatching { container.alarm.setNextUpcoming() }
                NightlyScheduler.scheduleNext(container.appContext)
                // полный семестр — фоном, без блокировки онбординга
                runCatching { container.scheduleRepository.refreshFull(target) }
            }
            onDone()
        }
    }

    companion object {
        const val DEFAULT_OFFSET_MINUTES = 90
        const val MIN_OFFSET_MINUTES = 15
        const val MAX_OFFSET_MINUTES = 240
        const val OFFSET_STEP_MINUTES = 15

        /** Пресеты шага «Подъём» (минуты). */
        val OFFSET_PRESETS = listOf(30, 45, 60, 90, 120)
    }
}
