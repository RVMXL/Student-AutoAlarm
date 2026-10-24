package ru.uniautoalarm.kgeu.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.db.ExceptionEntity
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.common.refreshErrorMessage
import ru.uniautoalarm.kgeu.ui.common.refreshMessage

/**
 * ViewModel экрана расписания: выбранная неделя (понедельник), выбранный день,
 * пары всех 7 дней недели одним map'ом и список исключений.
 *
 * Пары недели — flatMapLatest на combine из семи lessonsFlow: при смене недели
 * старые потоки отменяются, новые подхватываются автоматически.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    /** Снимок настроек (стиль «бумажного календаря» и пр.). */
    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Состояние синхронизации (busy — показываем полосу загрузки). */
    val sync: StateFlow<SyncInfo> = container.scheduleRepository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncInfo())

    /** Понедельник выбранной недели; при создании — текущая неделя. */
    private val _weekMonday = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val weekMonday: StateFlow<LocalDate> = _weekMonday.asStateFlow()

    /** Выбранный день недели. */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * Пары ОКНА ИЗ ТРЁХ НЕДЕЛЬ (предыдущая / выбранная / следующая) одним map'ом (0.25.0).
     * Окно шире видимой недели, чтобы страницы пейджера ЗА границей недели
     * (воскресенье → понедельник и обратно) уже во время свайпа показывали
     * настоящий день, а не пустую заглушку. flatMapLatest: при смене недели
     * старые потоки отменяются, новые подхватываются автоматически.
     */
    val weekLessons: StateFlow<Map<LocalDate, List<Lesson>>> = _weekMonday
        .flatMapLatest { monday ->
            val window = listOf(monday.minusDays(7), monday, monday.plusDays(7))
            combine(
                window.flatMap { m ->
                    (0..6).map { offset ->
                        container.scheduleRepository.lessonsFlow(m.plusDays(offset.toLong()))
                    }
                }
            ) { lists ->
                buildMap {
                    window.forEachIndexed { weekIdx, m ->
                        repeat(7) { offset ->
                            put(
                                m.plusDays(offset.toLong()),
                                lists[weekIdx * 7 + offset].sortedBy { it.start }
                            )
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Исключения (реактивно из Room). */
    val exceptions: StateFlow<List<ExceptionRule>> = container.database.exceptionDao().all()
        .map { list ->
            list.map { e ->
                ExceptionRule(id = e.id, subject = e.subject, enabled = e.enabled)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Выбрать день недели (тап по чипу — день всегда из текущей недели). */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * Зафиксировать день по факту установки страницы пейджера (0.25.0): свайп
     * или программная анимация довели до нового дня. Если день из другой недели —
     * неделя сдвигается следом и тихо подтягивается из API (как у стрелок недель).
     */
    fun settleOn(date: LocalDate, onError: (String) -> Unit = {}) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        val newMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (newMonday != _weekMonday.value) {
            _weekMonday.value = newMonday
            viewModelScope.launch { refreshQuietly(newMonday, onError) }
        }
    }

    /**
     * Предыдущая неделя: сдвигаем и понедельник, и выбранный день (тот же день
     * недели). 0.25.0: тихая подгрузка недели из API — как у «вперёд» и свайпа
     * через границу недели (settleOn): теперь все три пути в прошлое ведут себя
     * одинаково (свежая установка → кэш прошлой недели мог и не существовать).
     */
    fun prevWeek(onError: (String) -> Unit = {}) {
        val newMonday = _weekMonday.value.minusDays(7)
        _weekMonday.value = newMonday
        _selectedDate.value = newMonday.plusDays(weekdayOffset(_selectedDate.value))
        viewModelScope.launch { refreshQuietly(newMonday, onError) }
    }

    /**
     * Вернуться к текущей неделе и выбрать сегодня (кнопка «сегодня» в шапке календаря).
     * 0.25.2: неделя тихо подтягивается из API — как у стрелок и свайпа через
     * границу: все пути смены недели ведут себя одинаково.
     */
    fun goToToday(onError: (String) -> Unit = {}) {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _weekMonday.value = monday
        _selectedDate.value = LocalDate.now()
        viewModelScope.launch { refreshQuietly(monday, onError) }
    }

    /**
     * Следующая неделя: сдвиг плюс тихая подгрузка новой недели из API
     * (если цели нет — просто показываем кэш). onError — только при сбое загрузки:
     * успех после перелистывания не значит ничего нового, а вот пустая неделя из-за
     * отвалившейся сети должна объясниться.
     */
    fun nextWeek(onError: (String) -> Unit = {}) {
        val newMonday = _weekMonday.value.plusDays(7)
        _weekMonday.value = newMonday
        _selectedDate.value = newMonday.plusDays(weekdayOffset(_selectedDate.value))
        viewModelScope.launch { refreshQuietly(newMonday, onError) }
    }

    /**
     * Тихая подгрузка недели из API (цели нет — просто работаем с кэшем).
     * 0.25.2: дедупликация — повторный вызов для ТАКОЙ ЖЕ недели, пока прошлый
     * ещё не завершился, не запускает второй запрос: быстрые флинги через
     * границу недели и многократные пересечения середины currentPage больше
     * не штормят API (ошибка всё равно долетит до снекбара один раз).
     */
    private var quietRefreshMonday: LocalDate? = null

    private suspend fun refreshQuietly(monday: LocalDate, onError: (String) -> Unit) {
        if (quietRefreshMonday == monday) return
        quietRefreshMonday = monday
        try {
            val target = container.settings.current().toTarget() ?: return
            container.scheduleRepository.refreshWeek(target, monday)
                .onFailure { e -> onError(refreshErrorMessage(container.appContext, e)) }
        } finally {
            if (quietRefreshMonday == monday) quietRefreshMonday = null
        }
    }

    /** Обновить выбранную неделю из API; onResult — текст для снекбара (успех/причина). */
    fun refreshCurrent(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val target = container.settings.current().toTarget()
            if (target == null) {
                onResult(LocaleHelper.string(container.appContext, R.string.err_no_target))
                return@launch
            }
            onResult(
                refreshMessage(
                    container.appContext,
                    container.scheduleRepository.refreshWeek(target, _weekMonday.value)
                )
            )
        }
    }

    /**
     * Переключить исключение для пары (иконка в расписании).
     *
     * С 0.7.0 правило привязано к ТОЧНОМУ полному названию пары — вместе с приставкой
     * типа: тап по «л. материаловедение» гасит будильник ТОЛЬКО перед лекцией,
     * «пр. материаловедение» продолжит звенеть (тип уже написан в названии пары,
     * отдельные тумблеры типов не нужны — убраны).
     * Правило с таким названием уже есть → удаляется (иконка гаснет), нет → добавляется.
     *
     * UI-9 (0.10.0): read-then-write сериализован мьютексом — двойной тап больше
     * не успевает прочитать «правила ещё нет» дважды и создать дубликат правила.
     */
    private val exceptionGate = Mutex()

    fun toggleException(subject: String) {
        viewModelScope.launch {
            exceptionGate.withLock {
                val dao = container.database.exceptionDao()
                val normalized = ScheduleRepository.normalizeForMatch(subject)
                val matching = dao.allOnce().filter {
                    ScheduleRepository.normalizeForMatch(it.subject) == normalized
                }
                if (matching.isNotEmpty()) {
                    matching.forEach { dao.delete(it.id) }
                } else {
                    dao.upsert(ExceptionEntity(subject = subject.trim()))
                }
            }
        }
    }

    /** Пара попадает под активное исключение (точное полное название)? */
    fun isExceptional(subject: String, exceptions: List<ExceptionRule>): Boolean =
        exceptions.any {
            it.enabled &&
                ScheduleRepository.normalizeForMatch(it.subject) ==
                ScheduleRepository.normalizeForMatch(subject)
        }

    /** Смещение дня недели относительно понедельника (0..6). */
    private fun weekdayOffset(date: LocalDate): Long = (date.dayOfWeek.value - 1).toLong()

    /** Цель расписания из снимка настроек; не выбрана → null. */
    private fun Settings.toTarget(): Target? {
        val type = targetType ?: return null
        val id = targetId ?: return null
        return Target(type, id, targetName)
    }
}
