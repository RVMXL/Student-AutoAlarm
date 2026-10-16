package ru.uniautoalarm.kgeu.di

import java.time.LocalDateTime

/**
 * Инжектируемый источник текущего времени (AL-21, 0.10.0): доменные решения
 * («ближайшее предстоящее пробуждение», деферраль CLOCK) принимаются через
 * [now], а не через статический LocalDateTime.now() — в тестах подменяется
 * фиксированным моментом без теневых потока и sleep'ов.
 */
fun interface AppClock {

    /** Текущий момент в системной зоне. */
    fun now(): LocalDateTime

    companion object {
        /** Системное время — production-реализация по умолчанию. */
        val SYSTEM = AppClock { LocalDateTime.now() }
    }
}
