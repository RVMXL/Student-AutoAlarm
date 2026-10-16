package ru.uniautoalarm.kgeu.ui.home

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalTime
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.common.longDate
import ru.uniautoalarm.kgeu.ui.common.timeFormat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import ru.uniautoalarm.kgeu.ui.theme.customAccentPalette

/** Скругления карточек (по ТЗ — 20.dp, статус-карточка крупнее). */
private val CardShape = RoundedCornerShape(20.dp)
private val StatusCardShape = RoundedCornerShape(24.dp)

/**
 * Главный экран: статус завтрашнего будильника, тумблер, карточка «Мои будильники»
 * (0.10.0 — Bug E: поставленные будильники теперь видны в приложении), ближайшие
 * занятия и предупреждения системы. Снекбары — локальный Scaffold.
 */
@Composable
fun HomeScreen() {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(container) } }
    )

    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val todayLessons by vm.todayLessons.collectAsStateWithLifecycle()
    val tomorrowLessons by vm.tomorrowLessons.collectAsStateWithLifecycle()
    // UI-11: пока engine-работа тумблера идёт (до 12 с), переключатель заблокирован
    val toggling by vm.toggling.collectAsStateWithLifecycle()
    // 0.10.0 (Bug E): поставленный будильник — для карточки «Мои будильники»
    val nextAlarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val testAlarm by vm.testAlarm.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Системные проверки — состояние перечитывается на КАЖДОМ возврате экрана
    // на передний план (UI-6): раньше вычислялись один раз в композиции и не
    // обновлялись после изменения разрешений в настройках системы
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var needsExactAlarmPermission by remember { mutableStateOf(false) }
    var ignoringBattery by remember { mutableStateOf(true) }
    // AL-11 (0.10.0): Android 14+ может не выдать full-screen intent свежей установке —
    // тогда звонок деградирует до тихого heads-up; предупреждаем с кнопкой исправления
    var fsiDenied by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        needsExactAlarmPermission =
            Build.VERSION.SDK_INT >= 31 && alarmManager?.canScheduleExactAlarms() == false
        ignoringBattery =
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        fsiDenied =
            Build.VERSION.SDK_INT >= 34 &&
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.canUseFullScreenIntent() == false
        // UI-6: проба слота будильника тоже вне композиции — перечитать
        vm.onScreenResume()
        onPauseOrDispose { }
    }

    // Разрешение уведомлений (Android 13+, UI-4): спрашиваем РОВНО один раз за сессию
    // — раньше диалог перезапускался при каждом входе на главный экран
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var notifRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!notifRequested && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifRequested = true
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Ошибка синхронизации — показать один раз (не повторять при возврате на вкладку)
    var shownError by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sync.lastError) {
        val error = sync.lastError
        if (error != null && error != shownError) {
            shownError = error
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeaderRow(
                    settings = settings,
                    busy = sync.busy,
                    onRefresh = {
                        vm.refresh { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
            }
            item {
                StatusCard(
                    settings = settings,
                    sync = sync,
                    plan = plan,
                    tomorrowLessons = tomorrowLessons,
                    onLoad = {
                        vm.refresh { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
            }
            item {
                ToggleCard(
                    enabled = settings?.autoAlarmEnabled ?: false,
                    busy = toggling,
                    onToggle = { enabled ->
                        vm.toggleAuto(enabled) { msg ->
                            // выключение отчитывается снекбаром (у него нет уведомлений)
                            if (msg != null) scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
            }
            item {
                // 0.10.0 (Bug E): поставленные будильники видны в приложении;
                // часы открываются только явной кнопкой в движке «В часах»
                AlarmsCard(
                    alarm = nextAlarm,
                    test = testAlarm,
                    target = settings?.alarmTarget,
                    onDismiss = {
                        vm.dismissAlarmsFromCard { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onCancelTest = { vm.cancelTestAlarm() },
                    onOpenClock = {
                        vm.openClockList { msg ->
                            if (msg != null) scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
            }
            item {
                UpcomingCard(todayLessons = todayLessons, tomorrowLessons = tomorrowLessons)
            }
            if (fsiDenied) {
                item {
                    WarningCard(
                        title = stringResource(R.string.dialog_fsi_title),
                        description = stringResource(R.string.dialog_fsi_desc),
                        actionLabel = stringResource(R.string.action_allow),
                        isError = false,
                        onAction = { openFullScreenIntentSettings(context) }
                    )
                }
            }
            if (needsExactAlarmPermission) {
                item {
                    WarningCard(
                        title = stringResource(R.string.dialog_exact_alarms_title),
                        description = stringResource(R.string.dialog_exact_alarms_desc),
                        actionLabel = stringResource(R.string.action_allow),
                        isError = true,
                        onAction = { openExactAlarmSettings(context) }
                    )
                }
            }
            if (!ignoringBattery) {
                item {
                    WarningCard(
                        title = stringResource(R.string.dialog_battery_title),
                        description = stringResource(R.string.dialog_battery_desc),
                        actionLabel = stringResource(R.string.action_fix),
                        isError = false,
                        onAction = { openBatteryOptimizationSettings(context) }
                    )
                }
            }
            item {
                val s = settings
                if (s != null && vm.dataStale(s)) {
                    WarningCard(
                        title = stringResource(R.string.dialog_stale_title),
                        description = stringResource(R.string.dialog_stale_desc),
                        actionLabel = stringResource(R.string.action_refresh),
                        isError = false,
                        onAction = {
                            vm.refresh { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    )
                }
            }
        }
    }
}

// --- Заголовок ---

@Composable
private fun HeaderRow(settings: Settings?, busy: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_refresh_schedule))
            }
        }
        // UI-19 (0.11.0): мёртвый AssistChip «Стандарт» удалён (onClick = {} ни на что
        // не реагировал; режим один — «одна ночь вперёд», см. CHANGELOG 0.8.0),
        // ключ mode_standard_chip вычищен из всех 15 локалей (SET-8-хвост)
    }
}

// --- Главная статус-карточка ---

@Composable
private fun StatusCard(
    settings: Settings?,
    sync: SyncInfo,
    plan: AlarmPlan?,
    tomorrowLessons: List<Lesson>,
    onLoad: () -> Unit
) {
    // FIX 0.18.0: карточки снова плотные и видимые (при прозрачных панелях часть
    // карточек слилась с фоном страницы); обычная Card прежних цветов
    Card(
        shape = StatusCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                settings == null -> {
                    Text(stringResource(R.string.loading_settings), style = MaterialTheme.typography.titleMedium)
                }

                !settings.autoAlarmEnabled -> {
                    Text(
                        stringResource(R.string.alarm_off_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        stringResource(R.string.alarm_off_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                plan != null -> PlanContent(plan)

                sync.busy -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(stringResource(R.string.refreshing), style = MaterialTheme.typography.titleMedium)
                    }
                }

                tomorrowLessons.isEmpty() && sync.lastSyncEpochMs == 0L -> {
                    Text(
                        stringResource(R.string.not_loaded_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        stringResource(R.string.not_loaded_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onLoad) { Text(stringResource(R.string.action_load)) }
                }

                else -> {
                    Text(stringResource(R.string.tomorrow_free_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.tomorrow_free_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanContent(plan: AlarmPlan) {
    Text(
        stringResource(R.string.tomorrow_label, plan.date.format(longDate())),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        stringResource(R.string.fmt_time_alarm_will_ring),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Text(
        plan.wakeAt.format(timeFormat()),
        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold)
    )
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
    val lesson = plan.lesson
    val event = plan.customEvent
    when {
        lesson != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (lesson.isReplacement) {
                    Spacer(Modifier.width(8.dp))
                    SmallBadge(
                        text = stringResource(R.string.badge_replacement),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            val details = buildList {
                if (lesson.room.isNotBlank()) add(stringResource(R.string.room_prefix, lesson.room))
                if (lesson.teacher.isNotBlank()) add(lesson.teacher)
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                stringResource(R.string.lesson_at, lesson.start.format(timeFormat())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        event != null -> {
            Text(event.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.own_event_at, event.time.format(timeFormat())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        else -> Text(plan.label, style = MaterialTheme.typography.titleMedium)
    }
}

// --- Тумблер авто-будильника ---

@Composable
private fun ToggleCard(enabled: Boolean, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // UI-11: busy — тумблер нельзя щёлкать посреди включения/выключения,
                    // иначе можно «выключить» будильник посреди ещё идущей постановки
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        enabled = !busy,
                        onValueChange = onToggle
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_auto_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Switch(checked = enabled, onCheckedChange = null)
                }
            }
            Text(
                stringResource(R.string.home_auto_subtitle),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
            )
        }
    }
}

// --- Карточка «Мои будильники» (0.10.0, Bug E) ---

/**
 * Поставленные будильники теперь видны в приложении: время звонка, дата, метка,
 * движок и живой ли слот. Кнопка «Снять» гасит будильники обоих движков; в движке
 * «В часах» доступна ЯВНАЯ кнопка списка часов — приложение само часы не открывает.
 * С 0.13.0 (Bug G) карточка показывает и тестовый будильник — в движке «В приложении»
 * с кнопкой снятия до звона (раньше тест был виден только в шторке после срабатывания).
 */
@Composable
private fun AlarmsCard(
    alarm: ScheduledAlarm?,
    test: TestAlarmUi?,
    target: AlarmTarget?,
    onDismiss: () -> Unit,
    onCancelTest: () -> Unit,
    onOpenClock: () -> Unit,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AlarmOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.alarms_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            if (alarm == null) {
                Text(
                    stringResource(R.string.alarms_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                // список часов уместен ТОЛЬКО в движке «В часах»: там могут остаться
                // будильники, которых приложение не видит (оболочка игнорирует DISMISS)
                if (target == AlarmTarget.CLOCK) {
                    TextButton(onClick = onOpenClock) {
                        Text(stringResource(R.string.btn_alarm_list))
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        alarm.wakeAt.format(timeFormat()),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column {
                        Text(
                            alarm.wakeAt.format(longDate()),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(
                                if (alarm.target == AlarmTarget.APP) R.string.alarms_engine_app
                                else R.string.alarms_engine_clock
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (alarm.label.isNotBlank()) {
                    Text(
                        alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (alarm.slotAlive) Icons.Rounded.Check else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (alarm.slotAlive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(
                            if (alarm.slotAlive) R.string.alarms_active
                            else R.string.alarms_slot_missing
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_dismiss_alarms))
                    }
                }
                if (alarm.target == AlarmTarget.CLOCK) {
                    TextButton(onClick = onOpenClock) {
                        Text(stringResource(R.string.btn_alarm_list))
                    }
                }
            }
            // Bug G (0.13.0): тестовый будильник — отдельной строкой, в обоих движках;
            // кнопка снятия — только в движке «В приложении» (там снятие гарантированное;
            // в часах тест снимается в самих часах — явная кнопка списка выше)
            if (test != null) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.alarms_test_row),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            test.triggerAt.format(timeFormat()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (test.target == AlarmTarget.APP) {
                        TextButton(onClick = onCancelTest) {
                            Text(stringResource(R.string.alarms_test_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(todayLessons: List<Lesson>, tomorrowLessons: List<Lesson>) {
    // UI-13 (0.11.0): «сейчас» — состояние, обновляемое на каждом возврате экрана,
    // а не замороженное на входе в композицию: фильтр «ближайших» жил вчерашним временем
    var now by remember { mutableStateOf(LocalTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = LocalTime.now()
        onPauseOrDispose { }
    }
    val todayUpcoming = todayLessons.filter { it.end > now }

    // FIX 0.18.0: обычная Card (цвета по умолчанию — surfaceContainer, видна на фоне)
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.home_upcoming_title), style = MaterialTheme.typography.titleMedium)
            if (todayUpcoming.isEmpty() && tomorrowLessons.isEmpty()) {
                Text(
                    stringResource(R.string.home_no_pairs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (todayUpcoming.isNotEmpty()) {
                    Text(
                        stringResource(R.string.today_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    todayUpcoming.take(4).forEach { lesson -> UpcomingRow(lesson) }
                }
                if (tomorrowLessons.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tomorrow_label_short),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    tomorrowLessons.take(4).forEach { lesson -> UpcomingRow(lesson) }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(lesson: Lesson) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${lesson.start.format(timeFormat())}–${lesson.end.format(timeFormat())}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(lesson.subject, style = MaterialTheme.typography.bodyLarge)
            val details = buildList {
                if (lesson.room.isNotBlank()) add(stringResource(R.string.room_prefix, lesson.room))
                if (lesson.teacher.isNotBlank()) add(lesson.teacher)
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Предупреждения ---

@Composable
private fun WarningCard(
    title: String,
    description: String,
    actionLabel: String,
    isError: Boolean,
    onAction: () -> Unit
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SmallBadge(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// --- Системные интенты ---

/** Открыть системный экран разрешения точных будильников (Android 12+). */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    try {
        context.startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    } catch (_: Exception) {
        // Экран может отсутствовать на нестандартных прошивках — молча игнорируем
    }
}

/** Диалог исключения приложения из оптимизации батареи. */
private fun openBatteryOptimizationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    } catch (_: Exception) {
    }
}

/** AL-11: экран разрешения «Полноэкранные уведомления» (Android 14+ отказал в FSI). */
private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < 30) return
    try {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    } catch (_: Exception) {
    }
}
