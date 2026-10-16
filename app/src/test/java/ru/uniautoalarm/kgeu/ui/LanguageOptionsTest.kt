package ru.uniautoalarm.kgeu.ui

import ru.uniautoalarm.kgeu.ui.common.SUPPORTED_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SET-11 (0.11.0): порядок языков в пикере — сначала топ-3 КГЭУ (ru, tt, en),
 * дальше по алфавиту родных имён. Раньше «Oʻzbekcha» (O) стояла после
 * «Тоҷикӣ»/«Türkçe» (Т) — список не соответствовал собственному комментарию.
 */
class LanguageOptionsTest {

    @Test
    fun `топ-3 — русский, татарский, английский`() {
        assertEquals(listOf("ru", "tt", "en"), SUPPORTED_LANGUAGES.take(3).map { it.tag })
    }

    @Test
    fun `хвост списка — по алфавиту родных имён (SET-11)`() {
        // Порядок курируемый (скрипты идут группами: арабица → кириллица → латиница → иероглифы),
        // как и задумано; фиксирует именно баг SET-11: «Oʻzbekcha» (O) стояла среди «Т…»
        assertEquals(
            listOf("ar", "ba", "es", "fr", "kk", "ky", "uz", "tg", "tr", "tk", "cv", "zh"),
            SUPPORTED_LANGUAGES.drop(3).map { it.tag }
        )
    }

    @Test
    fun `языков ровно 15 и теги уникальны`() {
        assertEquals(15, SUPPORTED_LANGUAGES.size)
        assertEquals(SUPPORTED_LANGUAGES.size, SUPPORTED_LANGUAGES.map { it.tag }.toSet().size)
        assertTrue(SUPPORTED_LANGUAGES.none { it.tag.isBlank() })
    }
}
