package ru.uniautoalarm.kgeu.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.13.0 (Bug F): часы oplus/ColorOS (Realme/Oppo/OnePlus) не снимают будильник
 * по DISMISS_ALARM, а ОТКРЫВАЮТ своё приложение — при переключении движка
 * стоковые часы выпрыгивали на передний план. Решение: в эти пакеты DISMISS
 * не отправляется вовсе. Тест фиксирует чистое решение [StockAlarmSetter.dismissSupported].
 */
class StockDismissSupportTest {

    @Test
    fun `oplus clock opens the app on dismiss - do not call`() {
        assertEquals(false, StockAlarmSetter.dismissSupported("com.oplus.alarmclock"))
    }

    @Test
    fun `legacy coloros clock opens the app on dismiss - do not call`() {
        assertEquals(false, StockAlarmSetter.dismissSupported("com.coloros.alarmclock"))
    }

    @Test
    fun `aosp and samsung clocks keep dismiss`() {
        assertEquals(true, StockAlarmSetter.dismissSupported("com.google.android.deskclock"))
        assertEquals(true, StockAlarmSetter.dismissSupported("com.sec.android.app.clockpackage"))
    }

    @Test
    fun `unknown or undetected package - try dismiss anyway`() {
        assertEquals(true, StockAlarmSetter.dismissSupported(null))
        assertEquals(true, StockAlarmSetter.dismissSupported("com.unknown.clock"))
    }
}
