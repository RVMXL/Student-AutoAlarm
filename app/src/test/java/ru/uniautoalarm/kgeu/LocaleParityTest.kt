package ru.uniautoalarm.kgeu

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.9.0 (аудит, разделы 8.3 и 10.4): регрессионная защита паритета локалей —
 * скриптовая сверка аудита (15 файлов: ключи/плейсхолдеры/пустые) как юнит-тест.
 * Ломается при добавлении строки в один файл и забытой другой — ровно тот класс
 * ошибок, который иначе нашёл бы только пользователь на своём языке.
 */
class LocaleParityTest {

    private val resDirs = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull { it.isDirectory }

    private fun locales(): List<Pair<String, File>> {
        val base = resDirs ?: throw AssertionError("не найден каталог res")
        val dirs = base.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        return dirs.map { it.name.removePrefix("values").removePrefix("-") to File(it, "strings.xml") }
            .filter { it.second.isFile }
    }

    private fun parse(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = HashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as org.w3c.dom.Element
            map[el.getAttribute("name")] = el.textContent
        }
        return map
    }

    private fun placeholders(value: String): List<String> =
        Regex("%\\d+\\$[sd]").findAll(value).map { it.value }.sorted().toList()

    @Test
    fun `all locales have identical key sets`() {
        val files = locales()
        assertTrue("ожидались 15 локалей, найдено ${files.size}", files.size >= 15)
        val (baseName, baseFile) = files.first { it.first == "" || it.first == "default" || it.first.isEmpty() }
        val baseKeys = parse(baseFile).keys
        for ((name, file) in files) {
            if (name == baseName) continue
            val keys = parse(file).keys
            val missing = baseKeys - keys
            val extra = keys - baseKeys
            assertTrue(
                "локаль $name: отсутствуют ключи $missing, лишние $extra",
                missing.isEmpty() && extra.isEmpty()
            )
        }
    }

    @Test
    fun `no empty strings anywhere`() {
        for ((name, file) in locales()) {
            for ((key, value) in parse(file)) {
                assertTrue(
                    "локаль $name: пустое значение у '$key'",
                    value.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `placeholders match by type and order in every locale`() {
        val files = locales()
        val (baseName, baseFile) = files.first { it.first.isEmpty() }
        val base = parse(baseFile)
        for ((name, file) in files) {
            if (name == baseName) continue
            val mine = parse(file)
            for ((key, value) in base) {
                assertEquals(
                    "локаль $name, ключ '$key': расхождение плейсхолдеров",
                    placeholders(value), placeholders(mine.getValue(key))
                )
            }
        }
    }

    @Test
    fun `data-1 notification strings exist in every locale`() {
        for ((name, file) in locales()) {
            val strings = parse(file)
            assertTrue("локаль $name: нет notif_group_check_title", strings.containsKey("notif_group_check_title"))
            assertTrue("локаль $name: нет notif_group_check_text", strings.containsKey("notif_group_check_text"))
        }
    }
}
