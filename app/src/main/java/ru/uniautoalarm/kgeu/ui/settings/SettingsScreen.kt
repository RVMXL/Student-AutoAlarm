package ru.uniautoalarm.kgeu.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.data.model.AlarmMode
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.CustomEvent
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.model.ThemeMode
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.theme.ACCENT_PALETTES

/** Скругление карточек секций (по ТЗ — 20.dp). */
private val CardShape = RoundedCornerShape(20.dp)

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

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
            item { SectionTitle("Профиль") }
            item {
                val s = settings
                if (s != null) {
                    ProfileCard(settings = s, onChangeTarget = { vm.changeTarget() })
                }
            }

            // --- 2. Будильник ---
            item { SectionTitle("Будильник") }
            item {
                val s = settings
                if (s != null) {
                    AlarmSettingsCard(
                        settings = s,
                        vm = vm,
                        onSnackbar = showMessage
                    )
                }
            }

            // --- 3. Исключения ---
            item { SectionTitle("Исключения") }
            item { ExceptionsCard(exceptions = exceptions, vm = vm) }

            // --- 4. Свои занятия ---
            item { SectionTitle("Свои занятия") }
            item { CustomEventsCard(events = customEvents, vm = vm) }

            // --- 5. Оформление ---
            item { SectionTitle("Оформление") }
            item {
                val s = settings
                if (s != null) {
                    AppearanceCard(settings = s, vm = vm, context = context)
                }
            }

            // --- 6. Система ---
            item { SectionTitle("Система") }
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

            // --- 7. О приложении ---
            item { SectionTitle("О приложении") }
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

// --- 1. Профиль ---

@Composable
private fun ProfileCard(settings: Settings, onChangeTarget: () -> Unit) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
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
                    "Смена цели не сбросит смещение подъёма и настройки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onChangeTarget) { Text("Сменить") }
        }
    }
}

// --- 2. Будильник ---

@Composable
private fun AlarmSettingsCard(
    settings: Settings,
    vm: SettingsViewModel,
    onSnackbar: (String) -> Unit
) {
    var showOffsetDialog by remember { mutableStateOf(false) }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
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
                        onValueChange = { vm.toggleAuto(it) }
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
                    "Авто-будильник",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = settings.autoAlarmEnabled, onCheckedChange = null)
            }
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Подъём за ${formatOffset(settings.wakeOffsetMinutes)} до первой пары",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Первая пара в 08:30 → будильник в " +
                            exampleWake(settings.wakeOffsetMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showOffsetDialog = true }) { Text("Изменить") }
            }
            HorizontalDivider()

            Text("Режим", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.mode == AlarmMode.STANDARD,
                        role = Role.RadioButton,
                        onValueChange = { if (it) vm.setMode(AlarmMode.STANDARD) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.mode == AlarmMode.STANDARD,
                    onClick = null
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Стандарт — на завтра", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Каждую ночь будильник переставляется на завтра",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.mode == AlarmMode.WEEK_AHEAD,
                        role = Role.RadioButton,
                        onValueChange = { if (it) vm.setMode(AlarmMode.WEEK_AHEAD) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.mode == AlarmMode.WEEK_AHEAD,
                    onClick = null
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "На несколько дней вперёд",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Одноразовые будильники сразу на несколько дней (страховка от " +
                            "убийства процессов); минус — копятся в часах",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (settings.mode == AlarmMode.WEEK_AHEAD) {
                var days by remember(settings.weekAheadDays) {
                    mutableIntStateOf(settings.weekAheadDays)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Будильники на $days ${daysWord(days)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = days.toFloat(),
                        onValueChange = { days = it.roundToInt() },
                        onValueChangeFinished = { vm.setWeekAheadDays(days) },
                        valueRange = 3f..14f,
                        steps = 10
                    )
                    Button(onClick = {
                        vm.setWeekAhead(days) { count -> onSnackbar("Поставлено будильников: $count") }
                    }) {
                        Text("Поставить сейчас")
                    }
                }
            }
            HorizontalDivider()

            OutlinedButton(onClick = {
                vm.setTestAlarm { ok ->
                    onSnackbar(
                        if (ok) "Будильник на 2 минуты поставлен — проверь часы"
                        else "Не удалось поставить тестовый будильник — часы не отвечают"
                    )
                }
            }) {
                Text("Проверить будильник")
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableIntStateOf(initialMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подъём до пары") },
        text = {
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
                    onValueChange = { value = it.roundToInt() },
                    valueRange = 15f..240f,
                    steps = 14
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OFFSET_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = value == preset,
                            onClick = { value = preset },
                            label = { Text(formatOffset(preset)) }
                        )
                    }
                }
                Text(
                    "Первая пара в 08:30 → будильник прозвонит в ${exampleWake(value)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// --- 3. Исключения ---

@Composable
private fun ExceptionsCard(exceptions: List<ExceptionRule>, vm: SettingsViewModel) {
    var newText by remember { mutableStateOf("") }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Будильник не ставится перед этими дисциплинами",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (exceptions.isEmpty()) {
                Text(
                    "Пока пусто — добавьте предметы, перед которыми будить не нужно",
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
                            contentDescription = "Удалить «${rule.subject}»"
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
                    label = { Text("Название дисциплины") },
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
                    Icon(Icons.Rounded.Add, contentDescription = "Добавить исключение")
                }
            }
        }
    }
}

// --- 4. Свои занятия ---

@Composable
private fun CustomEventsCard(events: List<CustomEvent>, vm: SettingsViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Свои события с отдельным временем будильника — секции, репетиции, разовые дела",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (events.isEmpty()) {
                Text(
                    "Пока ничего не добавлено",
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
                            contentDescription = "Удалить «${event.name}»"
                        )
                    }
                }
            }
            OutlinedButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить")
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
    var name by remember { mutableStateOf("") }
    var weekly by remember { mutableStateOf(true) }
    var days by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var offset by remember { mutableIntStateOf(45) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && (if (weekly) days.isNotEmpty() else date != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Своё занятие") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = weekly,
                        onClick = { weekly = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("По дням недели")
                    }
                    SegmentedButton(
                        selected = !weekly,
                        onClick = { weekly = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Одна дата")
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
                                label = { Text(ruShortDay(day)) }
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
                        Text(date?.format(DATE) ?: "Выбрать дату")
                    }
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Время: ${time.format(TIME)}")
                }
                Column {
                    Text(
                        "Будить за $offset мин до события",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = offset.toFloat(),
                        onValueChange = { offset = it.roundToInt() },
                        valueRange = 15f..180f,
                        steps = 32
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
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
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
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Время события") },
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
                    Text("ОК")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            }
        )
    }
}

// --- 5. Оформление ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(settings: Settings, vm: SettingsViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var opacity by remember(settings.backgroundOpacity) {
        mutableFloatStateOf(settings.backgroundOpacity)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val file = File(context.filesDir, "background.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    vm.setBackground(file.absolutePath)
                } catch (_: Exception) {
                    // Не удалось скопировать картинку — фон просто не меняется
                }
            }
        }
    }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Тема", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Системная")
                }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("Светлая")
                }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { vm.setThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Тёмная")
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
                    "Material You (цвет системы)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = settings.dynamicColor, onCheckedChange = null)
            }

            Text("Акцент", style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ACCENT_PALETTES.forEachIndexed { index, palette ->
                    val color = Color(palette.primary)
                    val selected = settings.accentIndex == index
                    AccentDot(
                        color = color,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.primary,
                        outlineColor = MaterialTheme.colorScheme.outlineVariant,
                        onSelect = { vm.setAccent(index) },
                        description = palette.name
                    )
                }
            }

            Text("Стиль расписания", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.calendarStyle == CalendarStyle.STANDARD,
                    onClick = { vm.setCalendarStyle(CalendarStyle.STANDARD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Стандарт")
                }
                SegmentedButton(
                    selected = settings.calendarStyle == CalendarStyle.PAPER,
                    onClick = { vm.setCalendarStyle(CalendarStyle.PAPER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Бумажный календарь")
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
                Text("Фон", style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                    Text("Выбрать картинку")
                }
                TextButton(
                    onClick = { vm.setBackground(null) },
                    enabled = settings.backgroundImagePath != null
                ) {
                    Text("Убрать")
                }
            }
            Text(
                "Прозрачность подложки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { vm.setBackgroundOpacity(opacity) },
                valueRange = 0f..1f
            )
        }
    }
}

@Composable
private fun AccentDot(
    color: Color,
    selected: Boolean,
    borderColor: Color,
    outlineColor: Color,
    onSelect: () -> Unit,
    description: String
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(2.dp, borderColor, shape)
                } else {
                    Modifier.border(1.dp, outlineColor, shape)
                }
            )
            .clickable(onClickLabel = description, onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// --- 6. Система ---

@Composable
private fun SystemCard(
    exactRelevant: Boolean,
    exactAllowed: Boolean,
    ignoringBattery: Boolean,
    onFixExact: () -> Unit,
    onFixBattery: () -> Unit,
    onAutostart: () -> Unit
) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
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
                            "Точные будильники разрешены",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    WarningBlock(
                        title = "Точные будильники отключены системой",
                        description = "Ночной планировщик сможет ставить будильник только " +
                            "с погрешностью ±10 минут.",
                        actionLabel = "Разрешить",
                        isError = true,
                        onAction = onFixExact
                    )
                }
            }
            if (!ignoringBattery) {
                WarningBlock(
                    title = "Оптимизация батареи может мешать",
                    description = "Исключите приложение из оптимизации, чтобы ночная " +
                        "перепроверка расписания не срывалась.",
                    actionLabel = "Отключить оптимизацию",
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
                        "Автозапуск и агрессивные оболочки",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "MIUI, HarmonyOS и другие оболочки убивают фоновые приложения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onAutostart) { Text("Инструкция") }
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
    val instructions = remember {
        when {
            manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") ->
                "Xiaomi / Redmi / POCO:\nБезопасность → Приложения → Автозапуск: включи " +
                    "Автобудильник; в настройках батареи приложения выбери «нет ограничений»."

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "Huawei / Honor:\nНастройки → Батарея → Запуск приложений → Автобудильник → " +
                    "Управлять вручную: включи все переключатели."

            manufacturer.contains("oppo") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("oneplus") ->
                "OPPO / Realme / OnePlus:\nНастройки → Батарея → Ещё настройки → Оптимизация " +
                    "потребления → запрети оптимизацию для приложения; Менеджер приложений → " +
                    "Автозапуск."

            manufacturer.contains("samsung") ->
                "Samsung:\nНастройки → Батарея → Ограничения в фоне → Не спящие приложения → " +
                    "добавь Автобудильник."

            else ->
                "Другая оболочка:\nДобавь приложение в автозапуск и сними оптимизации батареи " +
                    "в настройках системы."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автозапуск и агрессивные оболочки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(instructions, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Режим «Стандарт» переставляет будильник каждую ночь — приложению нужен " +
                        "запуск в фоне в 00:05.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Понятно") }
        }
    )
}

// --- 7. О приложении ---

@Composable
private fun AboutCard(versionName: String?, context: Context) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                if (versionName != null) "Автобудильник КГЭУ · v$versionName"
                else "Автобудильник КГЭУ",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "© 2026 RVMXL · Apache License 2.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Данные расписания: официальный API kabinet.kgeu.ru. " +
                    "Приложение не аффилировано с КГЭУ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/rvmxl/UniAutoAlarm-KGEU")
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
                Text("Репозиторий на GitHub")
            }
        }
    }
}

// --- Утилиты ---

/** «90 мин» / «1 ч» / «1 ч 30 мин». */
private fun formatOffset(minutes: Int): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0 -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

/** Русское слово «дня/дней» для чисел 3..14. */
private fun daysWord(n: Int): String =
    if (n % 10 in 2..4 && n % 100 !in 12..14) "дня" else "дней"

/** Пример: будильник при первой паре в 08:30 и смещении [offsetMinutes]. */
private fun exampleWake(offsetMinutes: Int): String =
    LocalTime.of(8, 30).minusMinutes(offsetMinutes.toLong()).format(TIME)

/** «Студент · А-1-22» / «Преподаватель · Иванов И.И.» */
private fun profileLine(settings: Settings): String {
    val role = when (settings.targetType) {
        TargetType.STUDENT -> "Студент"
        TargetType.TEACHER -> "Преподаватель"
        null -> "Цель не выбрана"
    }
    return if (settings.targetName.isBlank()) role else "$role · ${settings.targetName}"
}

/** Подпись своего события: «Пн 07:30 · за 45 мин» или «14.10 09:00 · за 30 мин». */
private fun describeCustomEvent(event: CustomEvent): String {
    val moment = if (event.dayOfWeek != null) {
        ruShortDay(event.dayOfWeek)
    } else {
        event.date?.format(DATE) ?: ""
    }
    return "$moment ${event.time.format(TIME)} · за ${event.offsetMinutes} мин"
}

/** Короткое русское имя дня недели: Пн, Вт, … Вс. */
private fun ruShortDay(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Пн"
    DayOfWeek.TUESDAY -> "Вт"
    DayOfWeek.WEDNESDAY -> "Ср"
    DayOfWeek.THURSDAY -> "Чт"
    DayOfWeek.FRIDAY -> "Пт"
    DayOfWeek.SATURDAY -> "Сб"
    DayOfWeek.SUNDAY -> "Вс"
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
