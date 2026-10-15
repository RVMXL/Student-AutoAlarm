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
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.data.model.AlarmTarget

/**
 * Звонок собственного будильника (движок «В приложении»): foreground-сервис
 * (тип mediaPlayback) с зацикленным системным звуком будильника и вибрацией.
 *
 * Жизненный цикл: AlarmFireReceiver → startForegroundService(ACTION_START по умолчанию)
 * → звонок; кнопка «Отключить» (уведомление / RingActivity) → ACTION_STOP → тишина,
 * «пропущен» при автоглушении, и сразу ставится следующий будильник в «Стандарте».
 */
class AlarmRingingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var label: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging(missed = false, rearm = true)
                return START_NOT_STICKY
            }
            else -> {
                label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
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

        startSound()
        startVibration()

        // автоглушение: нет реакции 10 минут — тише, помечаем «пропущен»
        handler.postDelayed({ stopRinging(missed = true, rearm = true) }, AUTO_SILENCE_MS)
    }

    private fun startSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer().apply {
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
            }.also { player = it }
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
        handler.removeCallbacksAndMessages(null)
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        if (missed) Notifications.alarmMissed(this, label)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (rearm) rearmNext()
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

        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TRIGGER_MS = "extra_trigger_ms"

        private const val NOTIF_ID = 1005
        private const val AUTO_SILENCE_MS = 10L * 60 * 1000
    }
}
