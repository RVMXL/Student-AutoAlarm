package ru.uniautoalarm.kgeu.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalTime
import kotlin.math.roundToInt
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.remote.KgeuGroup
import ru.uniautoalarm.kgeu.data.remote.KgeuTeacher
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer

/** Скругление карточек мастера (по ТЗ — 20.dp). */
private val ResultCardShape = RoundedCornerShape(20.dp)

/** Максимум элементов списка поиска, показываемых за раз. */
private const val MAX_RESULTS = 60

/**
 * Мастер первичной настройки (5 шагов, state step: 0..4):
 *  0 приветствие → 1 роль → 2 поиск группы/преподавателя → 3 подъём → 4 финиш.
 * По завершении DataStore отдаёт setupComplete=true — Root в MainActivity сам
 * переключится на главный каркас; onFinished специально пустой.
 */
@Composable
fun SetupScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SetupViewModel = viewModel(
        factory = viewModelFactory { initializer { SetupViewModel(container) } }
    )

    val role by vm.role.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val teachers by vm.teachers.collectAsStateWithLifecycle()
    val selectedTarget by vm.selectedTarget.collectAsStateWithLifecycle()
    val offsetMinutes by vm.offsetMinutes.collectAsStateWithLifecycle()

    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Назад")
                }
            }
        }

        Text(
            "Шаг ${step + 1} из 5",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (step + 1) / 5f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> StepWelcome(onStart = { step = 1 }, modifier = Modifier.weight(1f))

            1 -> StepRole(
                selected = role,
                onPick = { type ->
                    vm.role.value = type
                    step = 2
                },
                modifier = Modifier.weight(1f)
            )

            2 -> StepSearch(
                role = role,
                query = query,
                onQueryChange = { vm.query.value = it },
                loading = loading,
                error = error,
                groups = groups,
                teachers = teachers,
                onLoadLists = { vm.loadLists(it) },
                onPickGroup = { group ->
                    vm.selectGroup(group)
                    step = 3
                },
                onPickTeacher = { teacher ->
                    vm.selectTeacher(teacher)
                    step = 3
                },
                modifier = Modifier.weight(1f)
            )

            3 -> StepWake(
                offsetMinutes = offsetMinutes,
                onOffsetChange = { vm.setOffset(it) },
                onNext = { step = 4 },
                modifier = Modifier.weight(1f)
            )

            else -> StepFinish(
                target = selectedTarget,
                offsetMinutes = offsetMinutes,
                onDone = { vm.completeSetup(onFinished) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

// --- Шаг 0: приветствие ---

@Composable
private fun StepWelcome(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp)
                )
            }
            Text(
                "Автобудильник КГЭУ",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Ставит будильник в стоковые часы перед первой парой — " +
                    "по расписанию университета. Настроим за минуту.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Card(shape = ResultCardShape, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Казанский государственный энергетический университет (КГЭУ)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "официальный API kabinet.kgeu.ru",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Начать", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// --- Шаг 1: роль ---

@Composable
private fun StepRole(
    selected: TargetType?,
    onPick: (TargetType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Кто вы?",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        RoleCard(
            title = "Студент",
            subtitle = "Будильник по расписанию моей группы",
            icon = Icons.Rounded.School,
            selected = selected == TargetType.STUDENT,
            onClick = { onPick(TargetType.STUDENT) }
        )
        RoleCard(
            title = "Преподаватель",
            subtitle = "Будильник по расписанию моих пар",
            icon = Icons.Rounded.Person,
            selected = selected == TargetType.TEACHER,
            onClick = { onPick(TargetType.TEACHER) }
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = ResultCardShape,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Шаг 2: поиск группы / преподавателя ---

@Composable
private fun StepSearch(
    role: TargetType?,
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    groups: List<KgeuGroup>,
    teachers: List<KgeuTeacher>,
    onLoadLists: (TargetType) -> Unit,
    onPickGroup: (KgeuGroup) -> Unit,
    onPickTeacher: (KgeuTeacher) -> Unit,
    modifier: Modifier = Modifier
) {
    // Список грузится при входе на шаг (и повторно — по кнопке «Повторить»)
    LaunchedEffect(role) { role?.let(onLoadLists) }

    val filteredGroups = remember(groups, query) {
        groups.filter { it.name.contains(query, ignoreCase = true) }
    }
    val filteredTeachers = remember(teachers, query) {
        teachers.filter { it.name.contains(query, ignoreCase = true) }
    }
    val found = if (role == TargetType.TEACHER) filteredTeachers.size else filteredGroups.size

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Начните вводить: А-1-23 …") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (found > MAX_RESULTS) {
                "Найдено: $found (показаны первые $MAX_RESULTS)"
            } else {
                "Найдено: $found"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            error != null -> LoadErrorCard(
                message = error ?: "Не удалось загрузить список",
                onRetry = { role?.let(onLoadLists) },
                modifier = Modifier.weight(1f)
            )

            role == TargetType.STUDENT -> GroupResults(
                groups = filteredGroups,
                onPick = onPickGroup,
                modifier = Modifier.weight(1f)
            )

            role == TargetType.TEACHER -> TeacherResults(
                teachers = filteredTeachers,
                onPick = onPickTeacher,
                modifier = Modifier.weight(1f)
            )

            else -> EmptyHint("Сначала выберите, кто вы", Modifier.weight(1f))
        }
    }
}

@Composable
private fun GroupResults(
    groups: List<KgeuGroup>,
    onPick: (KgeuGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = groups.take(MAX_RESULTS)
    if (shown.isEmpty()) {
        EmptyHint("Ничего не найдено", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shown, key = { it.id }) { group ->
            Card(
                onClick = { onPick(group) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    val subtitle = listOfNotNull(
                        group.facul?.takeIf { it.isNotBlank() },
                        group.kurs?.let { "курс $it" }
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherResults(
    teachers: List<KgeuTeacher>,
    onPick: (KgeuTeacher) -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = teachers.take(MAX_RESULTS)
    if (shown.isEmpty()) {
        EmptyHint("Ничего не найдено", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shown, key = { it.id }) { teacher ->
            Card(
                onClick = { onPick(teacher) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                    val kaf = teacher.kaf?.takeIf { it.isNotBlank() }
                    if (kaf != null) {
                        Text(
                            kaf,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = ResultCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Не удалось загрузить список",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- Шаг 3: время подъёма ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepWake(
    offsetMinutes: Int,
    onOffsetChange: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "За сколько вы обычно встаёте до первой пары?",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                formatOffset(offsetMinutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = offsetMinutes.toFloat(),
                onValueChange = { raw ->
                    // слайдер со steps сам щёлкает по делениям; округление — для надёжности
                    onOffsetChange(
                        (raw / SetupViewModel.OFFSET_STEP_MINUTES).roundToInt() *
                            SetupViewModel.OFFSET_STEP_MINUTES
                    )
                },
                valueRange = SetupViewModel.MIN_OFFSET_MINUTES.toFloat()..
                    SetupViewModel.MAX_OFFSET_MINUTES.toFloat(),
                steps = (SetupViewModel.MAX_OFFSET_MINUTES - SetupViewModel.MIN_OFFSET_MINUTES) /
                    SetupViewModel.OFFSET_STEP_MINUTES - 1,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in SetupViewModel.OFFSET_PRESETS) {
                    FilterChip(
                        selected = offsetMinutes == preset,
                        onClick = { onOffsetChange(preset) },
                        label = { Text("$preset мин") }
                    )
                }
            }
            Card(shape = ResultCardShape, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        exampleText(offsetMinutes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Далее", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// --- Шаг 4: финиш ---

@Composable
private fun StepFinish(
    target: Target?,
    offsetMinutes: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Всё готово!", style = MaterialTheme.typography.headlineSmall)
            SummaryCard(target = target, offsetMinutes = offsetMinutes)
            Text(
                "Режим „Стандарт“: каждую ночь будильник на завтра. " +
                    "Потом можно сменить в настройках.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDone,
            enabled = target != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Готово", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SummaryCard(target: Target?, offsetMinutes: Int) {
    Card(shape = ResultCardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (target == null) {
                Text(
                    "Цель не выбрана — вернитесь назад и выберите группу или преподавателя",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (target.type == TargetType.STUDENT) {
                            Icons.Rounded.School
                        } else {
                            Icons.Rounded.Person
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (target.type == TargetType.STUDENT) "Группа" else "Преподаватель",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(target.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Подъём до первой пары",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatOffset(offsetMinutes),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

// --- Локальные помощники форматирования ---

/** 90 → «1 ч 30 мин», 60 → «1 ч», 45 → «45 мин». */
private fun formatOffset(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "$hours ч $rest мин"
        hours > 0 -> "$hours ч"
        else -> "$rest мин"
    }
}

/** Пример-подпись: первая пара условно в 08:30 → время будильника. */
private fun exampleText(offsetMinutes: Int): String {
    val wake = LocalTime.of(8, 30).minusMinutes(offsetMinutes.toLong())
    return "Первая пара в 08:30 → будильник на %02d:%02d".format(wake.hour, wake.minute)
}
