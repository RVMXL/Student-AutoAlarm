package ru.uniautoalarm.kgeu.ui

import ru.uniautoalarm.kgeu.ui.common.SUPPORTED_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SET-11 (0.11.0): порядок языков в пикере — сначала топ-3 КГЭУ (ru, tt, en),
 * дальше по алфавиту родных имён. Раньше «Oʻzbekcha» (O) стояла после
 * «Тоҷикӣ»/«Türkçe» (Т) — список не соответствовал собственному комментарию.
 * 0.21.0: +4 языка (армянский, азербайджанский, грузинский, монгольский).
 */
class LanguageOptionsTest {

    @Test
    fun `топ-3 — русский, татарский, английский`() {
        assertEquals(listOf("ru", "tt", "en"), SUPPORTED_LANGUAGES.take(3).map { it.tag })
    }

    @Test
    fun `хвост списка — по алфавиту родных имён (SET-11)`() {
        // Порядок курируемый (0.21.0): азербайджанский — в начало латинского
        // блока (A), монгольский — между кириллическими К и O; армянский и
        // грузинский (нелатиница/некириллица) — перед иероглифами, в конце
        assertEquals(
            listOf(
                "ar", "az", "ba", "es", "fr", "kk", "ky", "mn", "uz", "tg",
                "tr", "tk", "cv", "hy", "ka", "zh"
            ),
            SUPPORTED_LANGUAGES.drop(3).map { it.tag }
        )
    }

    @Test
    fun `языков ровно 19 и теги уникальны`() {
        assertEquals(19, SUPPORTED_LANGUAGES.size)
        assertEquals(SUPPORTED_LANGUAGES.size, SUPPORTED_LANGUAGES.map { it.tag }.toSet().size)
        assertTrue(SUPPORTED_LANGUAGES.none { it.tag.isBlank() })
    }

    @Test
    fun `новые языки 0-21-0 присутствуют в списке`() {
        val tags = SUPPORTED_LANGUAGES.map { it.tag }.toSet()
        assertTrue(tags.containsAll(setOf("hy", "az", "ka", "mn")))
    }
}
