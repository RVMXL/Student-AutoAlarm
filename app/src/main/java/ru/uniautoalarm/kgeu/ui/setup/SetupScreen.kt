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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.roundToInt
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.local.LocaleStore
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.model.TargetType
import ru.uniautoalarm.kgeu.data.remote.KgeuGroup
import ru.uniautoalarm.kgeu.data.remote.KgeuTeacher
import ru.uniautoalarm.kgeu.ui.common.LanguagePicker
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.common.exampleWakeTime
import ru.uniautoalarm.kgeu.ui.common.formatOffset
import ru.uniautoalarm.kgeu.ui.common.hhmm

/** Скругление карточек мастера (по ТЗ — 20.dp). */
private val ResultCardShape = RoundedCornerShape(20.dp)

/** Максимум элементов списка поиска, показываемых за раз. */
private const val MAX_RESULTS = 60

/** Всего шагов мастера, включая шаг выбора языка. */
private const val TOTAL_STEPS = 6

/**
 * Мастер первичной настройки (6 шагов, state step: 0..5):
 *  0 язык → 1 приветствие → 2 роль → 3 поиск группы/преподавателя → 4 подъём → 5 финиш.
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
                    Text(stringResource(R.string.action_back))
                }
            }
        }

        Text(
            stringResource(R.string.setup_step_of, step + 1, TOTAL_STEPS),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (step + 1) / TOTAL_STEPS.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> StepLanguage(onNext = { step = 1 }, modifier = Modifier.weight(1f))

            1 -> StepWelcome(onStart = { step = 2 }, modifier = Modifier.weight(1f))

            2 -> StepRole(
                selected = role,
                onPick = { type ->
                    vm.role.value = type
                    step = 3
                },
                modifier = Modifier.weight(1f)
            )

            3 -> StepSearch(
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
                    step = 4
                },
                onPickTeacher = { teacher ->
                    vm.selectTeacher(teacher)
                    step = 4
                },
                modifier = Modifier.weight(1f)
            )

            4 -> StepWake(
                offsetMinutes = offsetMinutes,
                onOffsetChange = { vm.setOffset(it) },
                onNext = { step = 5 },
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

// --- Шаг 0: язык ---

@Composable
private fun StepLanguage(onNext: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LocaleStore.language(context)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.lang_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.lang_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Card(shape = ResultCardShape, modifier = Modifier.fillMaxWidth()) {
                // Внутри вертикального скролла высоту ленивого списка ограничиваем,
                // иначе LazyColumn получит бесконечную высоту и упадёт.
                LanguagePicker(
                    selected = selected,
                    onSelect = { tag ->
                        if (tag != selected) {
                            selected = tag
                            LocaleStore.setLanguage(context, tag)
                            // recreate() мгновенно применяет локаль; рестарт вернёт на шаг 0
                            (context as? android.app.Activity)?.recreate()
                        }
                    },
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .heightIn(max = 420.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.action_next), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// --- Шаг 1: приветствие ---

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
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.setup_welcome_subtitle),
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
                            stringResource(R.string.setup_university),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                stringResource(R.string.setup_official_api),
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
            Text(stringResource(R.string.action_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// --- Шаг 2: роль ---

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
            stringResource(R.string.setup_who_are_you),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        RoleCard(
            title = stringResource(R.string.role_student),
            subtitle = stringResource(R.string.role_student_subtitle),
            icon = Icons.Rounded.School,
            selected = selected == TargetType.STUDENT,
            onClick = { onPick(TargetType.STUDENT) }
        )
        RoleCard(
            title = stringResource(R.string.role_teacher),
            subtitle = stringResource(R.string.role_teacher_subtitle),
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

// --- Шаг 3: поиск группы / преподавателя ---

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
            placeholder = { Text(stringResource(R.string.setup_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (found > MAX_RESULTS) {
                stringResource(R.string.setup_found_capped, found, MAX_RESULTS)
            } else {
                stringResource(R.string.setup_found, found)
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
                message = error ?: stringResource(R.string.setup_load_failed),
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

            else -> EmptyHint(stringResource(R.string.setup_pick_role_first), Modifier.weight(1f))
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
        EmptyHint(stringResource(R.string.setup_nothing_found), modifier)
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
                        group.kurs?.let { stringResource(R.string.setup_course, it) }
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
        EmptyHint(stringResource(R.string.setup_nothing_found), modifier)
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
                    stringResource(R.string.setup_load_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
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

// --- Шаг 4: время подъёма ---

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
                stringResource(R.string.setup_wake_question),
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
                        label = { Text(stringResource(R.string.fmt_offset_m, preset)) }
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
                        stringResource(
                            R.string.setup_example,
                            exampleWakeTime(offsetMinutes).hhmm()
                        ),
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
            Text(stringResource(R.string.action_next), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// --- Шаг 5: финиш ---

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
            Text(
                stringResource(R.string.setup_done_all),
                style = MaterialTheme.typography.headlineSmall
            )
            SummaryCard(target = target, offsetMinutes = offsetMinutes)
            Text(
                stringResource(R.string.setup_finish_mode_note),
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
            Text(stringResource(R.string.action_done), style = MaterialTheme.typography.titleMedium)
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
                    stringResource(R.string.setup_no_target),
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
                            if (target.type == TargetType.STUDENT) {
                                stringResource(R.string.summary_group)
                            } else {
                                stringResource(R.string.summary_teacher)
                            },
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
                            stringResource(R.string.summary_wake_offset),
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
