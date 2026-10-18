package ru.uniautoalarm.kgeu.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.data.model.AlarmTarget

/**
 * Звонок собственного будильника (движок «В приложении»): foreground-сервис
 * (тип mediaPlayback) с зацикленным системным звуком будильника и вибрацией.
 *
 * Жизненный цикл: AlarmFireReceiver → startForegroundService(ACTION_START по умолчанию)
 * → звонок; кнопка «Отключить» (уведомление / RingActivity) → ACTION_STOP → тишина,
 * «пропущен» при автоглушении, и сразу ставится следующий будильник (AL-20: упоминание
 * удалённого режима «Стандарт» убрано — режим один: «одна ночь вперёд» с 0.8.0).
 *
 * 0.22.0 (отложенный звонок): «Отложить» (действие уведомления / кнопка RingActivity)
 * → ACTION_SNOOZE → тишина без «пропущен», ставится слот REQ_SNOOZE на +5 минут
 * ([SnoozePolicy]) с ПОЛНОЙ цепочкой срабатывания, маркер lastScheduledWake переносится
 * на отложенный момент (карточка «Мои будильники» показывает правду); если система
 * не приняла слот — тихо перевзводится обычное ближайшее пробуждение.
 *
 * 0.10.0 (AL-18): prepare() медиаплеера и подбор звука — на фоновом потоке
 * (главный больше не блокируется чтением кодека в момент звонка), а цепочка
 * источников расширена: системный звук будильника → звонок → уведомление.
 */
class AlarmRingingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var label: String = ""

    /** Одиночный фоновый исполнитель звука: prepare() на главном потоке тормозил звонок. */
    private val soundExecutor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging(missed = false, rearm = true)
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                // 0.22.0: метка может прийти в интенте действия (см. snoozeRingingIntent);
                // если сервис-инстанс уже звонил — остаётся прежняя
                intent.getStringExtra(EXTRA_LABEL)?.let { label = it }
                snoozeRinging()
                return START_NOT_STICKY
            }
            else -> {
                label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
                // AL-4: повторный ACTION_START (тестовый звонок пересёкся с реальным)
                // не должен плодить второй MediaPlayer: прежний плеер/вибрация глушатся
                // ДО нового старта, иначе первый экземпляр становится неостанавливаемым
                // (кнопка «Отключить» глушит только последний).
                stopSoundAndVibration()
                startRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        val notification = Notifications.ringing(this, label)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }

        soundExecutor.execute { startSound() }
        startVibration()

        // автоглушение: нет реакции 10 минут — тише, помечаем «пропущен»
        handler.postDelayed({ stopRinging(missed = true, rearm = true) }, AUTO_SILENCE_MS)
    }

    /**
     * Цепочка источников (AL-18): TYPE_ALARM → TYPE_RINGTONE → TYPE_NOTIFICATION.
     * Раньше при null обоих первых URI звонок молчал: добавлен последний фолбэк,
     * а сборка/prepare выполняются на [soundExecutor], не на главном потоке.
     * Вызывается ТОЛЬКО на [soundExecutor] (см. startRinging).
     */
    private fun startSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val p = MediaPlayer().apply {
                setDataSource(this@AlarmRingingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            player = p
        }.onFailure { Log.w(TAG, "Звук будильника недоступен: ${it.message}") }
    }

    private fun startVibration() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching {
            // 0.5 c вибрации / 0.7 c пауза, повтор с начала паттерна
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 700), 0))
            vibrator = v
        }.onFailure { Log.w(TAG, "Вибрация недоступна: ${it.message}") }
    }

    private fun stopRinging(missed: Boolean, rearm: Boolean) {
        stopSoundAndVibration()
        if (missed) Notifications.alarmMissed(this, label)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (rearm) rearmNext()
        // страховочная гигиена (0.22.0): отложенного звона при остановке звонка быть
        // не должно (слот либо уже сработал, либо его ставит snoozeRinging)
        runCatching { (application as? AutoAlarmApp)?.container?.inAppAlarms?.cancelSnooze() }
    }

    /**
     * «Отложить» (0.22.0): тишина БЕЗ «пропущен» и без перевзвода REQ_ONE_SHOT —
     * вместо этого ставится отдельный слот отложенного звона на +5 минут
     * ([SnoozePolicy]); маркер постановки переносится на отложенный момент,
     * чтобы карточка «Мои будильники» и ночная сверка видели правду.
     * Систему не приняла слот (практически не бывает) — тихо перевзводим
     * обычное ближайшее пробуждение, движок не остаётся пустым.
     */
    private fun snoozeRinging() {
        stopSoundAndVibration()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        val container = (application as? AutoAlarmApp)?.container
        val atMs = SnoozePolicy.atMs(System.currentTimeMillis())
        val ok = container?.inAppAlarms?.scheduleSnooze(atMs, label) == true
        if (ok) {
            container?.appScope?.launch {
                runCatching {
                    container.settings.update {
                        it.copy(
                            lastScheduledWakeEpochMinute =
                                SnoozePolicy.markerMinute(atMs, ZoneId.systemDefault()),
                            lastScheduledLabel = label
                        )
                    }
                }
            }
            runCatching { Notifications.snoozed(this, atMs) }
        } else {
            Log.w(TAG, "Слот отложенного звона не принят — перевзвод обычного будильника")
            rearmNext()
        }
    }

    /** Глушит звук и вибрацию (без уведомлений/stopSelf/rearm) — общий для STOP и повторного старта. */
    private fun stopSoundAndVibration() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    /**
     * Сразу после звонка — следующий будильник: в движке приложения больше не стоит
     * ни одного слота, ночная сверка 00:05 была бы слишком поздно — ставим ближайшее
     * предстоящее (сегодня, если есть ещё, иначе завтра) сразу.
     */
    private fun rearmNext() {
        val container = (application as? AutoAlarmApp)?.container ?: return
        container.appScope.launch {
            runCatching {
                val s = container.settings.current()
                if (s.setupComplete && s.autoAlarmEnabled && s.alarmTarget == AlarmTarget.APP) {
                    container.alarm.setNextUpcoming(notify = false)
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        soundExecutor.shutdownNow()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRinging"

        /** Остановить звонок (кнопка «Отключить»). */
        const val ACTION_STOP = "ru.uniautoalarm.kgeu.alarm.STOP_RINGING"

        /** Отложить звонок на [SnoozePolicy.MINUTES] минут (0.22.0). */
        const val ACTION_SNOOZE = "ru.uniautoalarm.kgeu.alarm.SNOOZE_RINGING"

        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TRIGGER_MS = "extra_trigger_ms"

        private const val NOTIF_ID = 1005
        private const val AUTO_SILENCE_MS = 10L * 60 * 1000
    }
}
