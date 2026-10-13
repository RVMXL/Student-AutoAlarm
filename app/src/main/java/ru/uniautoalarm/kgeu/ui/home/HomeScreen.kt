package ru.uniautoalarm.kgeu.ui.home

import android.Manifest
import android.app.AlarmManager
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
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.data.model.AlarmMode
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer

/** Скругления карточек (по ТЗ — 20.dp, статус-карточка крупнее). */
private val CardShape = RoundedCornerShape(20.dp)
private val StatusCardShape = RoundedCornerShape(24.dp)

private val RU_LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru", "RU"))
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DDMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

/**
 * Главный экран: статус завтрашнего будильника, тумблер, партия «на неделю вперёд»,
 * ближайшие занятия и предупреждения системы. Снекбары — локальный Scaffold.
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

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Системные проверки — прямо в экране (через LocalContext, без утечек в VM)
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val needsExactAlarmPermission =
        Build.VERSION.SDK_INT >= 31 && alarmManager?.canScheduleExactAlarms() == false
    val ignoringBattery =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    // Разрешение уведомлений (Android 13+): спрашиваем один раз при входе на главный экран
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
                    onRefresh = { vm.refresh() }
                )
            }
            item {
                StatusCard(
                    settings = settings,
                    sync = sync,
                    plan = plan,
                    tomorrowLessons = tomorrowLessons,
                    onLoad = { vm.refresh() }
                )
            }
            item {
                ToggleCard(
                    enabled = settings?.autoAlarmEnabled ?: false,
                    onToggle = { vm.toggleAuto(it) }
                )
            }
            item {
                val s = settings
                if (s != null) {
                    WeekAheadCard(
                        settings = s,
                        active = vm.weekAlarmsActive(s),
                        onSet = {
                            vm.setWeekAhead { count ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("Поставлено будильников: $count")
                                }
                            }
                        }
                    )
                }
            }
            item {
                UpcomingCard(todayLessons = todayLessons, tomorrowLessons = tomorrowLessons)
            }
            if (needsExactAlarmPermission) {
                item {
                    WarningCard(
                        title = "Точные будильники запрещены",
                        description = "Без разрешения система не позволит ставить будильник " +
                            "точно в рассчитанное время.",
                        actionLabel = "Разрешить",
                        isError = true,
                        onAction = { openExactAlarmSettings(context) }
                    )
                }
            }
            if (!ignoringBattery) {
                item {
                    WarningCard(
                        title = "Оптимизация батареи может мешать",
                        description = "Агрессивный энергосберегающий режим может снимать " +
                            "ночную перепроверку расписания.",
                        actionLabel = "Исправить",
                        isError = false,
                        onAction = { openBatteryOptimizationSettings(context) }
                    )
                }
            }
            item {
                val s = settings
                if (s != null && vm.dataStale(s)) {
                    WarningCard(
                        title = "Расписание не обновлялось более 3 дней",
                        description = "Расписание вуза часто меняют — подтяните свежие данные.",
                        actionLabel = "Обновить",
                        isError = false,
                        onAction = { vm.refresh() }
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
            "Автобудильник",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить расписание")
            }
        }
        AssistChip(
            onClick = { },
            label = {
                val s = settings
                Text(
                    if (s?.mode == AlarmMode.WEEK_AHEAD) {
                        "На ${s.weekAheadDays} дней"
                    } else {
                        "Стандарт"
                    }
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.AlarmOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
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
                    Text("Загрузка настроек…", style = MaterialTheme.typography.titleMedium)
                }

                !settings.autoAlarmEnabled -> {
                    Text(
                        "Будильник выключен",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Включите тумблером ниже",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
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
                        Text("Обновляем расписание…", style = MaterialTheme.typography.titleMedium)
                    }
                }

                tomorrowLessons.isEmpty() && sync.lastSyncEpochMs == 0L -> {
                    Text(
                        "Расписание ещё не загружено",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Подтяните пары из kabinet.kgeu.ru — здесь появится время подъёма",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onLoad) { Text("Загрузить") }
                }

                else -> {
                    Text("Завтра пар нет", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "будильник не ставится",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanContent(plan: AlarmPlan) {
    Text(
        "Завтра · ${plan.date.format(RU_LONG_DATE)}",
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        "будильник прозвонит в",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    )
    Text(
        plan.wakeAt.format(TIME),
        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold)
    )
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
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
                        text = "замена",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            val details = buildList {
                if (lesson.room.isNotBlank()) add("ауд. ${lesson.room}")
                if (lesson.teacher.isNotBlank()) add(lesson.teacher)
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Text(
                "пара в ${lesson.start.format(TIME)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }

        event != null -> {
            Text(event.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "своё занятие в ${event.time.format(TIME)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }

        else -> Text(plan.label, style = MaterialTheme.typography.titleMedium)
    }
}

// --- Тумблер авто-будильника ---

@Composable
private fun ToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
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
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle),
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = null)
            }
            Text(
                "Ставит будильник в стоковые часы перед первой парой",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
            )
        }
    }
}

// --- «На неделю вперёд» ---

@Composable
private fun WeekAheadCard(settings: Settings, active: Boolean, onSet: () -> Unit) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AlarmAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text("На неделю вперёд", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Одноразовые будильники на ${settings.weekAheadDays} дней — страховка, " +
                    "если система убивает приложение",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onSet) {
                    Text("Поставить на ${settings.weekAheadDays} дней")
                }
                if (active) {
                    val until = LocalDate.ofEpochDay(settings.weekAlarmsSetUntilEpochDay)
                    AssistChip(
                        onClick = { },
                        label = { Text("Активны до ${until.format(DDMM)}") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            Text(
                "Старые одноразовые будильники в часах удаляются вручную",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// --- Ближайшие занятия ---

@Composable
private fun UpcomingCard(todayLessons: List<Lesson>, tomorrowLessons: List<Lesson>) {
    val now = remember { LocalTime.now() }
    val todayUpcoming = todayLessons.filter { it.end > now }

    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Ближайшие занятия", style = MaterialTheme.typography.titleMedium)
            if (todayUpcoming.isEmpty() && tomorrowLessons.isEmpty()) {
                Text(
                    "Пар нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (todayUpcoming.isNotEmpty()) {
                    Text(
                        "Сегодня",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    todayUpcoming.take(4).forEach { lesson -> UpcomingRow(lesson) }
                }
                if (tomorrowLessons.isNotEmpty()) {
                    Text(
                        "Завтра",
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
            "${lesson.start.format(TIME)}–${lesson.end.format(TIME)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(lesson.subject, style = MaterialTheme.typography.bodyLarge)
            val details = buildList {
                if (lesson.room.isNotBlank()) add("ауд. ${lesson.room}")
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
