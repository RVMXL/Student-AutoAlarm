package ru.uniautoalarm.kgeu.ui.ring

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.alarm.AlarmFireReceiver
import ru.uniautoalarm.kgeu.alarm.InAppAlarmScheduler
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.common.timeFormat
import ru.uniautoalarm.kgeu.ui.theme.AutoAlarmTheme
import ru.uniautoalarm.kgeu.ui.theme.resolveDarkTheme

/**
 * Полноэкранный звонок собственного будильника (движок «В приложении»).
 * Поднимается по full-screen intent из звонящего уведомления поверх экрана
 * блокировки (showWhenLocked + turnScreenOn). Кнопка «Отключить» глушит сервис
 * звонка; следующий будильник ставится сервисом сразу после стопа
 * (AL-20: док больше не упоминает удалённый режим «Стандарт»).
 */
class RingActivity : ComponentActivity() {

    /** Язык интерфейса приложения — обёртыванием контекста (как в MainActivity). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // поверх экрана блокировки, с включением экрана (API 27+; на 26 — флаги окна)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // UI-18 (0.11.0): edge-to-edge — статус-бар под контролем темы (ниже)
        enableEdgeToEdge()

        val label = intent.getStringExtra(AlarmFireReceiver.EXTRA_LABEL) ?: ""
        val triggerAtMs = intent.getLongExtra(
            AlarmFireReceiver.EXTRA_TRIGGER_MS, System.currentTimeMillis()
        )
        val wakeAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(triggerAtMs), ZoneId.systemDefault()
        )

        setContent {
            val container = (application as AutoAlarmApp).container
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = resolveDarkTheme(settings)
            AutoAlarmTheme(settings) {
                // UI-18 (0.11.0): статус-бар согласован с темой звонка — светлые иконки
                // на тёмном фоне и наоборот (раньше на тёмном звонке были тёмные иконки);
                // фон статус-бара — плотный цвет фона темы
                val statusBarColor = MaterialTheme.colorScheme.background.copy(alpha = 1f)
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !darkTheme
                    window.statusBarColor = statusBarColor.toArgb()
                }
                // UI-3: системный «назад»/жест на звонке глушит звук и закрывает экран —
                // иначе finish() без stopRinging оставлял звук играть без UI-выключения
                // (при отказанных уведомлениях — вообще без способов, см. AL-10)
                BackHandler {
                    runCatching { startService(InAppAlarmScheduler.stopRingingIntent(this)) }
                    finish()
                }
                RingScreen(
                    label = label,
                    wakeAt = wakeAt,
                    onDismiss = {
                        runCatching { startService(InAppAlarmScheduler.stopRingingIntent(this)) }
                        finish()
                    }
                )
            }
        }
    }

    @Composable
    private fun RingScreen(label: String, wakeAt: LocalDateTime, onDismiss: () -> Unit) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // UI-18: edge-to-edge — отступы под оба системных бара
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.ring_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    wakeAt.format(timeFormat()),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (label.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        stringResource(R.string.ring_dismiss),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
