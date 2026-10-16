package ru.uniautoalarm.kgeu.ui.settings

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.alarm.StockAlarmSetter
import ru.uniautoalarm.kgeu.data.local.LocaleStore
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.CustomEvent
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.model.ThemeMode
import ru.uniautoalarm.kgeu.ui.common.LanguagePicker
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.common.OffsetBounds
import ru.uniautoalarm.kgeu.ui.common.SUPPORTED_LANGUAGES
import ru.uniautoalarm.kgeu.ui.common.bidiSegment
import ru.uniautoalarm.kgeu.ui.common.shortDate
import ru.uniautoalarm.kgeu.ui.common.timeFormat
import ru.uniautoalarm.kgeu.ui.common.dayShortName
import ru.uniautoalarm.kgeu.ui.common.displayName
import ru.uniautoalarm.kgeu.ui.common.exampleWakeTime
import ru.uniautoalarm.kgeu.ui.common.formatOffset
import ru.uniautoalarm.kgeu.ui.common.hhmm
import ru.uniautoalarm.kgeu.ui.theme.ACCENT_PALETTES
import ru.uniautoalarm.kgeu.ui.theme.CUSTOM_ACCENT_INDEX
import ru.uniautoalarm.kgeu.ui.theme.argbToHsv
import ru.uniautoalarm.kgeu.ui.theme.customAccentPalette
import ru.uniautoalarm.kgeu.ui.theme.glassSurface
import ru.uniautoalarm.kgeu.ui.theme.hsvToArgb
import ru.uniautoalarm.kgeu.ui.theme.resolveDarkTheme
import ru.uniautoalarm.kgeu.ui.theme.skin

/** Скругление карточек секций (по ТЗ — 20.dp). */
private val CardShape = RoundedCornerShape(20.dp)

// --- SET-2: сейверы для диалоговых стейтов (поворот не закрывает диалоги и не стирает ввод) ---

/** Set<DayOfWeek> → List<Int> (для rememberSaveable в диалоге «Своё занятие»). */
private val WeekDaysSaver = listSaver<Set<DayOfWeek>, Int>(
    save = { days -> days.map { day -> day.value } },
    restore = { values -> values.map { value -> DayOfWeek.of(value) }.toSet() },
)

/** LocalDate? → List<Long> (эпоха-день; null сохраняется пустым списком). */
private val OptionalDateSaver = listSaver<LocalDate?, Long>(
    save = { d -> if (d == null) emptyList() else listOf(d.toEpochDay()) },
    restore = { list -> if (list.isEmpty()) null else LocalDate.ofEpochDay(list.first()) },
)

/** LocalTime → List<Int> (часы, минуты). */
private val LocalTimeSaver = listSaver<LocalTime, Int>(
    save = { t -> listOf(t.hour, t.minute) },
    restore = { list -> LocalTime.of(list[0], list[1]) },
)

private val WEEK_DAYS: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

private val OFFSET_PRESETS = listOf(30, 45, 60, 90, 120)

/**
 * Экран настроек: профиль, будильник, исключения, свои занятия, оформление,
 * система и «о приложении». Снекбары — локальный Scaffold.
 */
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container) } }
    )

    val settings by vm.settings.collectAsStateWithLifecycle()
    val exceptions by vm.exceptions.collectAsStateWithLifecycle()
    val customEvents by vm.customEvents.collectAsStateWithLifecycle()
    val pairStartTimes by vm.pairStartTimes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Системные проверки — прямо в экране, без утечки контекста в VM
    val alarmManager =
        remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
    val powerManager =
        remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val exactRelevant = Build.VERSION.SDK_INT >= 31
    val exactAllowed = !exactRelevant || alarmManager?.canScheduleExactAlarms() != false
    val ignoringBattery =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    val versionName = remember { appVersionName(context) }
    var showAutostartDialog by remember { mutableStateOf(false) }

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
            // --- 1. Профиль ---
            item { SectionTitle(stringResource(R.string.sec_profile)) }
            item {
                val s = settings
                if (s != null) {
                    ProfileCard(settings = s, onChangeTarget = { vm.changeTarget() })
                }
            }

            // --- 2. Будильник ---
            item { SectionTitle(stringResource(R.string.sec_alarm)) }
            item {
                val s = settings
                if (s != null) {
                    AlarmSettingsCard(
                        settings = s,
                        vm = vm,
                        pairStartTimes = pairStartTimes,
                        context = context,
                        onSnackbar = showMessage
                    )
                }
            }

            // --- 3. Напоминание о будильнике ---
            item { SectionTitle(stringResource(R.string.sec_reminder)) }
            item {
                val s = settings
                if (s != null) {
                    ReminderCard(settings = s, vm = vm)
                }
            }

            // --- 4. Исключения ---
            item { SectionTitle(stringResource(R.string.sec_exceptions)) }
            item { ExceptionsCard(exceptions = exceptions, vm = vm) }

            // --- 5. Свои занятия ---
            item { SectionTitle(stringResource(R.string.sec_custom_events)) }
            item { CustomEventsCard(events = customEvents, vm = vm) }

            // --- 6. Оформление ---
            item { SectionTitle(stringResource(R.string.sec_appearance)) }
            item {
                val s = settings
                if (s != null) {
                    val bgRejected = stringResource(R.string.bg_image_rejected)
                    AppearanceCard(
                        settings = s,
                        vm = vm,
                        context = context,
                        // SEC-11: некондиционный файл — снекбар с локализованной причиной
                        // (строка берётся в композиции — stringResource недоступен в лямбде)
                        onRejected = { showMessage(bgRejected) }
                    )
                }
            }

            // --- 7. Система ---
            item { SectionTitle(stringResource(R.string.sec_system)) }
            item {
                SystemCard(
                    exactRelevant = exactRelevant,
                    exactAllowed = exactAllowed,
                    ignoringBattery = ignoringBattery,
                    onFixExact = { openExactAlarmSettings(context) },
                    onFixBattery = { openBatteryOptimizationSettings(context) },
                    onAutostart = { showAutostartDialog = true }
                )
            }

            // --- 8. О приложении ---
            item { SectionTitle(stringResource(R.string.sec_about)) }
            item { AboutCard(versionName = versionName, context = context) }
        }
    }

    if (showAutostartDialog) {
        AutostartDialog(onDismiss = { showAutostartDialog = false })
    }
}

// --- Заголовок секции ---

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/**
 * Метка сегмента пилюли (SegmentedButton): строго одна строка с многоточием при
 * переполнении. Баг 0.5.0: в языках с длинными словами («Системалы», «Tous les types»)
 * метка переносилась на вторую строку — сегмент растал в высоту, пилюля становилась
 * «кривой» (одна секция толще других). Одна строка держит все сегменты одной высоты.
 */
@Composable
private fun PillLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// --- 1. Профиль ---

@Composable
private fun ProfileCard(settings: Settings, onChangeTarget: () -> Unit) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profileLine(settings), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.profile_change_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onChangeTarget) { Text(stringResource(R.string.action_change)) }
        }
    }
}

// --- 2. Будильник ---

@Composable
private fun AlarmSettingsCard(
    settings: Settings,
    vm: SettingsViewModel,
    pairStartTimes: List<LocalTime>,
    context: Context,
    onSnackbar: (String) -> Unit
) {
    var showOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var editPairStart by rememberSaveable { mutableStateOf<Int?>(null) }
    // UI-11: пока engine-работа тумблера/смены движка идёт, переключатели заблокированы
    val toggling by vm.toggling.collectAsStateWithLifecycle()

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.autoAlarmEnabled,
                        role = Role.Switch,
                        enabled = !toggling,
                        onValueChange = { enabled ->
                            vm.toggleAuto(enabled) { msg ->
                                // выключение отчитывается снекбаром (у него нет уведомлений)
                                if (msg != null) onSnackbar(msg)
                            }
                        }
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
                    stringResource(R.string.set_auto_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (toggling) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Switch(checked = settings.autoAlarmEnabled, onCheckedChange = null)
                }
            }
            HorizontalDivider()

            // --- Куда ставится будильник (0.7.0): часы телефона или приложение ---
            Text(
                stringResource(R.string.alarm_target_title),
                style = MaterialTheme.typography.bodyLarge
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.alarmTarget == AlarmTarget.CLOCK,
                    onClick = { vm.setAlarmTarget(AlarmTarget.CLOCK) },
                    enabled = !toggling,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    PillLabel(stringResource(R.string.alarm_target_clock))
                }
                SegmentedButton(
                    selected = settings.alarmTarget == AlarmTarget.APP,
                    onClick = { vm.setAlarmTarget(AlarmTarget.APP) },
                    enabled = !toggling,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    PillLabel(stringResource(R.string.alarm_target_app))
                }
            }
            Text(
                stringResource(R.string.alarm_target_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            // 0.14.0: если движку «В приложении» не выдан full-screen intent (Android 14+,
            // пользователь мог отозвать) — звонок не включает экран и не разворачивается
            // поверх блокировки. Честная подсказка с кнопкой в настройки разрешения.
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val fsiGranted = nm?.canUseFullScreenIntent() ?: true
                if (!fsiGranted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                                        ).setData(
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    // редкие оболочки без этой активности — общие настройки приложения
                                    runCatching {
                                        context.startActivity(
                                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(Uri.fromParts("package", context.packageName, null))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.set_fsi_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                stringResource(R.string.set_fsi_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (settings.alarmTarget == AlarmTarget.CLOCK) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = settings.alarmShowClockUi,
                            role = Role.Switch,
                            onValueChange = { vm.setAlarmShowClockUi(it) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.set_show_clock),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.set_show_clock_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.alarmShowClockUi, onCheckedChange = null)
                }
                HorizontalDivider()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            R.string.set_wake_offset, formatOffset(settings.wakeOffsetMinutes)
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(
                            R.string.set_offset_example,
                            exampleWakeTime(settings.wakeOffsetMinutes).hhmm()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showOffsetDialog = true }) {
                    Text(stringResource(R.string.action_edit))
                }
            }
            HorizontalDivider()

            // --- Подъём под каждую пару: свой запас на сборы для каждого времени первой пары ---
            Text(
                stringResource(R.string.set_per_pair),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(R.string.set_per_pair_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pairStartTimes.isEmpty()) {
                Text(
                    stringResource(R.string.set_pairs_after_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pairStartTimes.forEach { pairStart ->
                    val startMinutes = pairStart.hour * 60 + pairStart.minute
                    val custom = settings.firstLessonOffsets[startMinutes]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.pair_at, pairStart.format(timeFormat())),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (custom != null) {
                                    stringResource(
                                        R.string.pair_custom_offset,
                                        formatOffset(custom),
                                        pairStart.minusMinutes(custom.toLong()).hhmm()
                                    )
                                } else {
                                    stringResource(
                                        R.string.pair_common_offset,
                                        formatOffset(settings.wakeOffsetMinutes)
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (custom != null) {
                            TextButton(onClick = { vm.setPairOffset(startMinutes, null) }) {
                                Text(stringResource(R.string.action_reset))
                            }
                        }
                        TextButton(onClick = { editPairStart = startMinutes }) {
                            Text(stringResource(R.string.action_edit))
                        }
                    }
                }
            }
            HorizontalDivider()

            // Кнопка гигиены: снять будильники приложения в стоковых часах по меткам
            // «Авто: …» (оболочка может проигнорировать DISMISS — тогда список часов
            // откроется для ручного удаления; движок «В приложении» снимается cancel'ом)
            OutlinedButton(
                onClick = { vm.dismissAlarms { onSnackbar(it) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_disable_app_alarms))
            }
            Text(
                stringResource(R.string.disable_alarms_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.setTestAlarm { ok ->
                            onSnackbar(
                                LocaleHelper.string(
                                    context,
                                    if (ok) R.string.test_alarm_ok else R.string.test_alarm_fail
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_test_alarm))
                }
                OutlinedButton(
                    onClick = { openStockAlarms(context) { onSnackbar(it) } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_alarm_list))
                }
            }
        }
    }

    if (showOffsetDialog) {
        OffsetDialog(
            initialMinutes = settings.wakeOffsetMinutes,
            onDismiss = { showOffsetDialog = false },
            onConfirm = { value ->
                vm.setWakeOffset(value)
                showOffsetDialog = false
            }
        )
    }

    editPairStart?.let { startMinutes ->
        PairOffsetDialog(
            pairStart = LocalTime.of(startMinutes / 60, startMinutes % 60),
            initialMinutes = settings.firstLessonOffsets[startMinutes]
                ?: settings.wakeOffsetMinutes,
            isCustom = settings.firstLessonOffsets.containsKey(startMinutes),
            onDismiss = { editPairStart = null },
            onSave = { value ->
                vm.setPairOffset(startMinutes, value)
                editPairStart = null
            },
            onReset = {
                vm.setPairOffset(startMinutes, null)
                editPairStart = null
            }
        )
    }
}

/**
 * SET-10 (0.11.0): общее тело диалогов смещения — крупное значение, слайдер,
 * пресеты. Раньше OffsetDialog и PairOffsetDialog дублировали эти ~55 строк —
 * при смене границ/пресетов править приходилось в двух местах (SET-13).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetSliderBody(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    example: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            formatOffset(value),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = (valueRange.endInclusive - valueRange.start).toInt() /
                OffsetBounds.STEP - 1
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OFFSET_PRESETS.forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick = { onValueChange(preset) },
                    label = { Text(formatOffset(preset)) }
                )
            }
        }
        example()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    // SET-2: значение слайдера переживает поворот
    var value by rememberSaveable { mutableIntStateOf(initialMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_offset_title)) },
        text = {
            // SET-10: тело слайдера/пресетов — общее с PairOffsetDialog
            OffsetSliderBody(
                value = value,
                onValueChange = { value = it },
                valueRange = OffsetBounds.WAKE_RANGE,
                example = {
                    Text(
                        stringResource(R.string.dialog_offset_example, exampleWakeTime(value).hhmm()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Диалог своего подъёма для одной пары («Подъём под каждую пару»).
 * Сброс удаляет индивидуальную запись — пара снова использует общий подъём.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PairOffsetDialog(
    pairStart: LocalTime,
    initialMinutes: Int,
    isCustom: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: () -> Unit
) {
    // SET-2: значение слайдера переживает поворот
    var value by rememberSaveable { mutableIntStateOf(initialMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_pair_title, pairStart.format(timeFormat()))) },
        text = {
            // SET-10: тело слайдера/пресетов — общее с OffsetDialog
            OffsetSliderBody(
                value = value,
                onValueChange = { value = it },
                valueRange = OffsetBounds.WAKE_RANGE,
                example = {
                    Text(
                        stringResource(
                            R.string.dialog_pair_example,
                            pairStart.format(timeFormat()),
                            pairStart.minusMinutes(value.toLong()).hhmm()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (isCustom) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.action_reset))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

// --- Напоминание о будильнике ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(settings: Settings, vm: SettingsViewModel) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.reminderEnabled,
                        role = Role.Switch,
                        onValueChange = { vm.setReminderEnabled(it) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sec_reminder),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.set_reminder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.reminderEnabled, onCheckedChange = null)
            }
            Text(
                stringResource(R.string.set_reminder_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.reminderEnabled) {
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.reminder_time,
                            "%02d:%02d".format(
                                settings.reminderTime.hour, settings.reminderTime.minute
                            )
                        )
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        // SET-14 (0.11.0): 12/24-часовой пикер — по системной настройке пользователя,
        // а не «всегда 24»: en-US видел пикер 24h, а слайдер примеров — AM/PM
        val context = LocalContext.current
        val is24h = AndroidDateFormat.is24HourFormat(context)
        val timeState = rememberTimePickerState(
            initialHour = settings.reminderTime.hour,
            initialMinute = settings.reminderTime.minute,
            is24Hour = is24h
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.dialog_reminder_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setReminderTime(LocalTime.of(timeState.hour, timeState.minute))
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// --- 4. Исключения ---
// (с 0.7.0: правило = точное полное название пары, включая приставку типа
// «л./пр./лаб.» — отдельные чипы типов убраны, т.к. тип уже написан в названии)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExceptionsCard(exceptions: List<ExceptionRule>, vm: SettingsViewModel) {
    // SET-2: переживает поворот — введённое название не стирается
    var newText by rememberSaveable { mutableStateOf("") }

    // Автодополнение по дисциплинам из кэша расписания: ввёл «ма» — подсказывает
    // «материаловедение». Живёт только пока текст не совпадает с подсказкой.
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val query = newText.trim()
    val suggestions = if (query.isEmpty()) {
        emptyList()
    } else {
        subjects
            .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
            .take(5)
    }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.exceptions_title_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (exceptions.isEmpty()) {
                Text(
                    stringResource(R.string.exceptions_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            exceptions.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        rule.subject,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { vm.setExceptionEnabled(rule, it) }
                    )
                    IconButton(onClick = { vm.deleteException(rule.id) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.cd_delete_named, rule.subject)
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text(stringResource(R.string.hint_subject_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        vm.addException(newText)
                        newText = ""
                    },
                    enabled = newText.isNotBlank()
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_exception)
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                Text(
                    stringResource(R.string.exceptions_found),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEach { subject ->
                    TextButton(
                        onClick = {
                            vm.addException(subject)
                            newText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            subject,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}

// --- 5. Свои занятия ---

@Composable
private fun CustomEventsCard(events: List<CustomEvent>, vm: SettingsViewModel) {
    // SET-2: переживает поворот — открытый диалог не закрывается
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.custom_events_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.custom_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            events.forEach { event ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(event.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            describeCustomEvent(event),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = event.enabled,
                        onCheckedChange = { vm.setCustomEventEnabled(event.id, it) }
                    )
                    IconButton(onClick = { vm.deleteCustomEvent(event.id) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.cd_delete_named, event.name)
                        )
                    }
                }
            }
            OutlinedButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_add))
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            onDismiss = { showAddDialog = false },
            onSaveWeekly = { name, days, time, offset ->
                vm.addWeeklyEvent(name, days, time, offset)
            },
            onSaveDated = { name, date, time, offset ->
                vm.addDatedEvent(name, date, time, offset)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onSaveWeekly: (String, Set<DayOfWeek>, LocalTime, Int) -> Unit,
    onSaveDated: (String, LocalDate, LocalTime, Int) -> Unit
) {
    // SET-2: весь стейт диалога — rememberSaveable (поворот сохраняет ввод)
    var name by rememberSaveable { mutableStateOf("") }
    var weekly by rememberSaveable { mutableStateOf(true) }
    var days by rememberSaveable(stateSaver = WeekDaysSaver) { mutableStateOf(setOf<DayOfWeek>()) }
    var date by rememberSaveable(stateSaver = OptionalDateSaver) { mutableStateOf<LocalDate?>(null) }
    var time by rememberSaveable(stateSaver = LocalTimeSaver) { mutableStateOf(LocalTime.of(8, 0)) }
    var offset by rememberSaveable { mutableIntStateOf(45) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val canSave = name.isNotBlank() && (if (weekly) days.isNotEmpty() else date != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_event_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hint_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = weekly,
                        onClick = { weekly = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        PillLabel(stringResource(R.string.event_days_mode))
                    }
                    SegmentedButton(
                        selected = !weekly,
                        onClick = { weekly = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        PillLabel(stringResource(R.string.event_date_mode))
                    }
                }
                if (weekly) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WEEK_DAYS.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                onClick = {
                                    days = if (day in days) days - day else days + day
                                },
                                label = { Text(dayShortName(day)) }
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Event, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(date?.format(shortDate()) ?: stringResource(R.string.btn_pick_date))
                    }
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.event_time, time.format(timeFormat())))
                }
                Column {
                    Text(
                        stringResource(R.string.event_wake_before, formatOffset(offset)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = offset.toFloat(),
                        onValueChange = { offset = it.roundToInt() },
                        // SET-13: границы своего занятия — из единого источника
                        valueRange = OffsetBounds.EVENT_RANGE,
                        steps = (OffsetBounds.EVENT_MAX - OffsetBounds.EVENT_MIN) /
                            OffsetBounds.STEP - 1
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (weekly) {
                        onSaveWeekly(name.trim(), days, time, offset)
                        onDismiss()
                    } else {
                        val picked = date
                        if (picked != null) {
                            onSaveDated(name.trim(), picked, time, offset)
                            onDismiss()
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToMillis(date ?: LocalDate.now())
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = millisToLocalDate(it) }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        // SET-14: 12/24 — по системной настройке пользователя
        val context = LocalContext.current
        val is24h = AndroidDateFormat.is24HourFormat(context)
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = is24h
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.dialog_event_time_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// --- 6. Оформление ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    settings: Settings,
    vm: SettingsViewModel,
    context: Context,
    onRejected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // --- Язык интерфейса (новое в 0.5.0): первая строка секции «Оформление» ---
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    // язык читаем один раз: выбор применяется через recreate() Activity
    val currentLanguageTag = remember { LocaleStore.language(context) }
    val currentLanguage = SUPPORTED_LANGUAGES.firstOrNull { it.tag == currentLanguageTag }
    // SET-2: положение слайдеров переживает поворот (синхронизируется с настройками)
    var opacity by rememberSaveable(settings.backgroundOpacity) {
        mutableFloatStateOf(settings.backgroundOpacity)
    }
    // Стекло 2.0 (0.14.0): два ползунка — матовость (размытие + плотность заливки)
    // и искажение («линзовое» искривление за стеклом). Видны только при включённом стекле.
    var glassBlur by rememberSaveable(settings.glassBlur) {
        mutableFloatStateOf(settings.glassBlur)
    }
    var glassDistortion by rememberSaveable(settings.glassDistortion) {
        mutableFloatStateOf(settings.glassDistortion)
    }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    // FIX 0.14.0: кружки акцентов показывали только светлое primary всех палитр —
    // в тёмной теме реальный акцент другой (primaryDark), и цвет кружка не совпадал
    // с фактическим. Теперь кружок — тот цвет, который реально применён темой.
    val darkTheme = resolveDarkTheme(settings)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                // SEC-11 (0.11.0): файл проверяется, а не копируется вслепую:
                // тип (реально декодируется как картинка), размер (≤ 20 МБ — не убить
                // хранилище), плюс даунскейл до 2048px по большей стороне (не убить
                // память при декодировании на слабых устройствах). Некондиция —
                // отказ с внятным снекбаром, а не молчаливый «фон не меняется»
                val ok = copyValidatedBackground(context, uri)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        vm.setBackground(File(context.filesDir, "background.jpg").absolutePath)
                    } else {
                        onRejected()
                    }
                }
            }
        }
    }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Язык интерфейса ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.set_language),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (currentLanguage != null) currentLanguage.displayName()
                        else stringResource(R.string.lang_system),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()

            Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    PillLabel(stringResource(R.string.theme_system))
                }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    PillLabel(stringResource(R.string.theme_light))
                }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { vm.setThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    PillLabel(stringResource(R.string.theme_dark))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.dynamicColor,
                        role = Role.Switch,
                        onValueChange = { vm.setDynamicColor(it) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = settings.dynamicColor, onCheckedChange = null)
            }

            Text(stringResource(R.string.set_accent), style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ACCENT_PALETTES.forEachIndexed { index, palette ->
                    // кружок = реальный акцент текущей темы (в тёмной — primaryDark)
                    val color = if (darkTheme) Color(palette.primaryDark) else Color(palette.primary)
                    val selected = settings.accentIndex == index
                    AccentDot(
                        color = color,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.primary,
                        outlineColor = MaterialTheme.colorScheme.outlineVariant,
                        onSelect = { vm.setAccent(index) },
                        description = accentName(palette.id)
                    )
                }
                // «Свой цвет»: радужный кружок с пипеткой, открывает палитру выбора
                AccentDot(
                    color = settings.customAccentColor?.let { Color(it) }
                        ?: MaterialTheme.colorScheme.primary,
                    selected = settings.accentIndex == CUSTOM_ACCENT_INDEX &&
                        settings.customAccentColor != null,
                    borderColor = MaterialTheme.colorScheme.primary,
                    outlineColor = MaterialTheme.colorScheme.outlineVariant,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047),
                            Color(0xFF00ACC1), Color(0xFF8E24AA), Color(0xFFE53935)
                        )
                    ),
                    icon = Icons.Rounded.ColorLens,
                    onSelect = { showColorPicker = true },
                    description = stringResource(R.string.accent_custom)
                )
            }

            Text(
                stringResource(R.string.schedule_style),
                style = MaterialTheme.typography.bodyLarge
            )
            // 0.14.0: стилей стало 7 — горизонтальная лента плиток-миниатюр
            // вместо сегментных кнопок (туда больше не влезть)
            val styles = remember { CalendarStyle.entries }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(styles.size) { i ->
                    val style = styles[i]
                    StyleTile(
                        style = style,
                        selected = settings.calendarStyle == style,
                        onSelect = { vm.setCalendarStyle(style) }
                    )
                }
            }
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.set_background), style = MaterialTheme.typography.bodyLarge)
            }
                    BackgroundPickerRow(
                        onPick = { imagePicker.launch(arrayOf("image/*")) },
                        onRemove = { vm.setBackground(null) },
                        removeEnabled = settings.backgroundImagePath != null
                    )
            Text(
                stringResource(R.string.bg_opacity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { vm.setBackgroundOpacity(opacity) },
                valueRange = 0f..1f
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.glassEnabled,
                        role = Role.Switch,
                        onValueChange = { vm.setGlassEnabled(it) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.set_glass),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.set_glass_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.glassEnabled, onCheckedChange = null)
            }
            if (settings.glassEnabled) {
                Text(
                    stringResource(R.string.glass_matteness),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = glassBlur,
                    onValueChange = { glassBlur = it },
                    onValueChangeFinished = { vm.setGlassBlur(glassBlur) },
                    valueRange = 0f..1f
                )
                Text(
                    stringResource(R.string.glass_distortion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = glassDistortion,
                    onValueChange = { glassDistortion = it },
                    onValueChangeFinished = { vm.setGlassDistortion(glassDistortion) },
                    valueRange = 0f..1f
                )
                Text(
                    if (settings.backgroundImagePath == null) {
                        stringResource(R.string.glass_tip)
                    } else {
                        stringResource(R.string.glass_active)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.set_language)) },
            text = {
                LanguagePicker(
                    selected = currentLanguageTag,
                    onSelect = { tag ->
                        showLanguageDialog = false
                        LocaleStore.setLanguage(context, tag)
                        // пересоздание Activity применяет язык немедленно
                        (context as? Activity)?.recreate()
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }

    // Палитра «своего цвета» (0.14.0): квадрат насыщенность/яркость + ползунок тона
    if (showColorPicker) {
        AccentColorPickerDialog(
            initialColor = settings.customAccentColor?.let { Color(it) }
                ?: Color(0xFFB45309),
            onConfirm = { argb ->
                vm.setCustomAccent(argb)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun AccentDot(
    color: Color,
    selected: Boolean,
    borderColor: Color,
    outlineColor: Color,
    onSelect: () -> Unit,
    description: String,
    brush: Brush? = null,
    icon: ImageVector? = null
) {
    val shape = CircleShape
    // SET-15 (0.11.0): визуальный кружок 36dp внутри тач-таргета 48dp (было 36dp
    // целиком — ниже минимума Material/A11y), роль RadioButton для скринридера
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                onClickLabel = description,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(brush = brush ?: SolidColor(color))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, borderColor, shape)
                    } else {
                        Modifier.border(1.dp, outlineColor, shape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                // кружок «свой цвет»: интуитивный значок пипетки поверх радуги
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        // тень под иконкой, чтобы читалась на любом цвете радуги
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(3.dp)
                )
                selected -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Миниатюра стиля расписания в ленте выбора (0.14.0). */
@Composable
private fun StyleTile(style: CalendarStyle, selected: Boolean, onSelect: () -> Unit) {
    val skin = style.skin()
    val bg = skin?.background ?: MaterialTheme.colorScheme.surfaceVariant
    val ink = skin?.ink ?: MaterialTheme.colorScheme.onSurface
    val accent = skin?.accent ?: MaterialTheme.colorScheme.primary
    val name = stringResource(
        when (style) {
            CalendarStyle.STANDARD -> R.string.style_standard
            CalendarStyle.PAPER -> R.string.style_paper
            CalendarStyle.CHALK -> R.string.style_chalk
            CalendarStyle.NEON -> R.string.style_neon
            CalendarStyle.TERMINAL -> R.string.style_terminal
            CalendarStyle.AQUA -> R.string.style_aqua
            CalendarStyle.RETRO -> R.string.style_retro
        }
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClickLabel = name, role = Role.RadioButton, onClick = onSelect)
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).background(accent, CircleShape))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(ink.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                        .background(ink.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Палитра «своего цвета» (0.14.0): квадрат насыщенность (X) / яркость (Y),
 * ползунок тона (радуга) и превью результата. Подтверждение — «Готово».
 */
@Composable
private fun AccentColorPickerDialog(
    initialColor: Color,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // стартовые HSV — из текущего цвета (или дефолта), чтобы диалог открывался «на месте»
    val initialHsv = remember(initialColor) { argbToHsv(initialColor.toArgb()) }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var sat by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val preview = Color(hsvToArgb(hue, sat, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accent_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // --- квадрат S/V ---
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(hue) {
                            detectTapGestures { off ->
                                sat = (off.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (off.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    val hueColor = Color(hsvToArgb(hue, 1f, 1f))
                    drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    // маркер выбора
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = Offset(sat * size.width, (1f - value) * size.height),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = 9.dp.toPx(),
                        center = Offset(sat * size.width, (1f - value) * size.height),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // --- ползунок тона ---
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { off ->
                                hue = (off.x / size.width).coerceIn(0f, 1f) * 360f
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            }
                        }
                ) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                                Color(0xFFFF0000)
                            )
                        ),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 11.dp.toPx(),
                        center = Offset(hue / 360f * size.width, size.height / 2f),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
                // --- превью ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(preview)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Text(
                        stringResource(R.string.accent_custom_preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(preview.toArgb()) }) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Локализованное имя палитры акцента (метка клика для скринридеров). */
@Composable
private fun accentName(id: Int): String = stringResource(
    when (id) {
        0 -> R.string.accent_amber
        1 -> R.string.accent_orange
        2 -> R.string.accent_red
        3 -> R.string.accent_green
        4 -> R.string.accent_pink
        else -> R.string.accent_purple
    }
)

// --- 7. Система ---

@Composable
private fun SystemCard(
    exactRelevant: Boolean,
    exactAllowed: Boolean,
    ignoringBattery: Boolean,
    onFixExact: () -> Unit,
    onFixBattery: () -> Unit,
    onAutostart: () -> Unit
) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (exactRelevant) {
                if (exactAllowed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.status_exact_allowed),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    WarningBlock(
                        title = stringResource(R.string.dialog_exact_off_title),
                        description = stringResource(R.string.dialog_exact_off_desc),
                        actionLabel = stringResource(R.string.action_allow),
                        isError = true,
                        onAction = onFixExact
                    )
                }
            }
            if (!ignoringBattery) {
                WarningBlock(
                    title = stringResource(R.string.dialog_battery2_title),
                    description = stringResource(R.string.dialog_battery2_desc),
                    actionLabel = stringResource(R.string.action_disable_optimization),
                    isError = false,
                    onAction = onFixBattery
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.autostart_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.autostart_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onAutostart) {
                    Text(stringResource(R.string.btn_instructions))
                }
            }
        }
    }
}

@Composable
private fun WarningBlock(
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (isError) Icons.Rounded.AlarmOff else Icons.Rounded.BatteryAlert,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = content,
                    contentColor = container
                )
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AutostartDialog(onDismiss: () -> Unit) {
    val manufacturer = remember { Build.MANUFACTURER.lowercase() }
    // ресурс инструкции выбираем по производителю, строку достаём stringResource'ом
    // (stringResource нельзя звать внутри remember{} — там нет composable-контекста)
    val instructionsRes = when {
        manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") ->
            R.string.autostart_xiaomi

        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            R.string.autostart_huawei

        manufacturer.contains("oppo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("oneplus") ->
            R.string.autostart_oppo

        manufacturer.contains("samsung") ->
            R.string.autostart_samsung

        else -> R.string.autostart_other
    }
    val instructions = stringResource(instructionsRes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autostart_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(instructions, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.autostart_standard_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_understood)) }
        }
    )
}

// --- 8. О приложении ---

@Composable
private fun AboutCard(versionName: String?, context: Context) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().glassSurface(CardShape)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                if (versionName != null) stringResource(R.string.about_version, versionName)
                else stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium
            )
            // SET-16 (0.11.0): копирайт — из ресурсов, а не константа в коде
            // (весь текст UI обязан жить в strings.xml — единый контракт i18n)
            Text(
                stringResource(R.string.copyright_line),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.about_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/RVMXL/Student-AutoAlarm")
                        )
                    )
                } catch (_: Exception) {
                    // Браузера нет — ссылку открыть нечем, молча игнорируем
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_github))
            }
        }
    }
}

// --- Утилиты ---

/** Кнопки выбора/удаления фоновой картинки (вынесено из AppearanceCard). */
@Composable
private fun BackgroundPickerRow(onPick: () -> Unit, onRemove: () -> Unit, removeEnabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPick) {
            Text(stringResource(R.string.btn_pick_image))
        }
        TextButton(onClick = onRemove, enabled = removeEnabled) {
            Text(stringResource(R.string.btn_remove))
        }
    }
}

/**
 * SEC-11 (0.11.0): скопировать выбранное изображение в filesDir/background.jpg
 * С ВАЛИДАЦИЕЙ: реально декодируется как картинка (фильтр «image» в пикере
 * можно обойти), не больше 20 МБ, даунскейл до 2048px по большей стороне
 * (inSampleSize — декодирование не съедает сотни МБ на слабых устройствах).
 * @return успех копирования.
 */
private fun copyValidatedBackground(context: Context, uri: Uri): Boolean = try {
    val resolver = context.contentResolver
    val sizeOk = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length <= 20L * 1024 * 1024 }
        ?: false
    if (!sizeOk) {
        false
    } else {
        // 1) проверка «это вообще картинка» + размеры (без выделения памяти под пиксели)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            false
        } else {
            // 2) даунскейл до 2048px по большей стороне
            var sample = 1
            var maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / (sample * 2) >= 2048) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return false
            val ok = bitmap != null
            if (ok) {
                val file = File(context.filesDir, "background.jpg")
                file.outputStream().use { out ->
                    bitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            bitmap?.recycle()
            ok
        }
    }
} catch (_: Exception) {
    false
}

// Локальные formatOffset / daysWord / exampleWake / wakeExample удалены:
// строки смещений собираются через ui.common.formatOffset, времена — через
// exampleWakeTime(...).hhmm() и LocalTime.hhmm() из ui.common.Formats.

/** «Студент · А-1-22» / «Преподаватель · Иванов И.И.» */
@Composable
private fun profileLine(settings: Settings): String {
    val role = when (settings.targetType) {
        TargetType.STUDENT -> stringResource(R.string.role_student)
        TargetType.TEACHER -> stringResource(R.string.role_teacher)
        null -> stringResource(R.string.profile_no_target)
    }
    return if (settings.targetName.isBlank()) role else "$role · ${settings.targetName}"
}

/** Подпись своего события: «Пн 07:30 · за 45 мин» или «14 окт 09:00 · за 30 мин».
 *  SET-14: дата — коротким локализованным форматом (не жёсткое dd.MM), сегменты
 *  обёрнуты в bidi-изоляты — при арабском интерфейсе порядок «день/время/офсет»
 *  не плавает. */
@Composable
private fun describeCustomEvent(event: CustomEvent): String {
    val moment = if (event.dayOfWeek != null) {
        dayShortName(event.dayOfWeek)
    } else {
        event.date?.format(shortDate()) ?: ""
    }
    return bidiSegment(moment) + " " + bidiSegment(event.time.format(timeFormat())) + " " +
        stringResource(R.string.event_offset_suffix, formatOffset(event.offsetMinutes))
}

/** DatePicker отдаёт millis в UTC → LocalDate без сдвига суток. */
private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun localDateToMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (_: Exception) {
    null
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

/** Открыть список будильников стоковых часов: удалить лишний одноразовый можно только там. */
private fun openStockAlarms(context: Context, onError: (String) -> Unit) {
    try {
        context.startActivity(
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        onError(LocaleHelper.string(context, R.string.alarms_list_open_failed))
    }
}
