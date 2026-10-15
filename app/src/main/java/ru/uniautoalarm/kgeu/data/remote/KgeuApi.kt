package ru.uniautoalarm.kgeu.data.remote

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.model.TargetType

/** Ошибка API КГЭУ: HTTP не 2xx либо state != 1 (сервер присылает ошибки с HTTP 200). */
class KgeuApiException(message: String) : Exception(message)

/**
 * Клиент публичного JSON-API расписания КГЭУ (kabinet.kgeu.ru, платформа DSTU WebApp).
 * Все запросы — синхронный OkHttp внутри withContext(Dispatchers.IO).
 * Контракт задокументирован в docs/research/kgeu-api.md; поля ответов на русском.
 */
class KgeuApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = JSON

    /** Список групп; [year] — учебный год «YYYY-YYYY», без него — текущий. */
    suspend fun groups(year: String? = null): List<KgeuGroup> = withContext(Dispatchers.IO) {
        val raw = fetch(buildUrl("/api/raspGrouplist", "year" to year))
        val env = json.decodeFromString<KgeuListEnvelope<KgeuGroup>>(raw)
        requireState(env.state, env.msg)
        env.data
    }

    /** Список преподавателей; [year] — учебный год «YYYY-YYYY». */
    suspend fun teachers(year: String? = null): List<KgeuTeacher> = withContext(Dispatchers.IO) {
        val raw = fetch(buildUrl("/api/raspTeacherlist", "year" to year))
        val env = json.decodeFromString<KgeuListEnvelope<KgeuTeacher>>(raw)
        requireState(env.state, env.msg)
        env.data
    }

    /** Учебные годы («2026-2027», …). Эндпоинт отдаёт «голый» массив — поддержим и конверт. */
    suspend fun years(): List<String> = withContext(Dispatchers.IO) {
        val raw = fetch(buildUrl("/api/Rasp/ListYears"))
        try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: SerializationException) {
            val env = json.decodeFromString<KgeuListEnvelope<String>>(raw)
            requireState(env.state, env.msg)
            env.data
        }
    }

    /**
     * Записи расписания цели (группа/преподаватель).
     * [sdate] == null → весь семестр одним ответом; иначе — неделя (пн–вс), содержащая дату.
     */
    suspend fun rasp(target: Target, sdate: LocalDate?): List<KgeuRaspItem> = withContext(Dispatchers.IO) {
        val idParam = idQueryParam(target)
        val raw = fetch(buildUrl("/api/Rasp", idParam, "sdate" to sdate?.toString()))
        val env = json.decodeFromString<KgeuObjectEnvelope<KgeuRaspData>>(raw)
        requireState(env.state, env.msg)
        env.data?.rasp ?: emptyList()
    }

    /** Границы семестра (minDate/maxDate) и все даты с занятиями. */
    suspend fun raspDates(target: Target): KgeuDatesData = withContext(Dispatchers.IO) {
        val raw = fetch(buildUrl("/api/GetRaspDates", idQueryParam(target)))
        val env = json.decodeFromString<KgeuObjectEnvelope<KgeuDatesData>>(raw)
        requireState(env.state, env.msg)
        env.data ?: KgeuDatesData()
    }

    // --- Внутреннее ---

    /** Параметр цели: idGroup для студента/группы, idTeacher для преподавателя. */
    private fun idQueryParam(target: Target): Pair<String, String> = when (target.type) {
        TargetType.STUDENT -> "idGroup" to target.id.toString()
        TargetType.TEACHER -> "idTeacher" to target.id.toString()
    }

    /** GET с браузероподобным User-Agent; use() гарантирует закрытие Response. */
    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KgeuApiException("HTTP ${response.code} ${response.message}")
            // Технические тексты здесь на английском: они попадают в снекбар
            // «Ошибка обновления: …» при ЛЮБОМ языке интерфейса (i18n 0.5.0)
            return response.body?.string()
                ?: throw KgeuApiException("Empty response from server")
        }
    }

    private fun buildUrl(path: String, vararg params: Pair<String, String?>): String {
        val builder = (BASE_URL + path).toHttpUrl().newBuilder()
        for ((name, value) in params) if (value != null) builder.addQueryParameter(name, value)
        return builder.toString()
    }

    /** Успех = state:1; иначе человекочитаемая ошибка сервера в msg. */
    private fun requireState(state: Int, msg: String?) = Companion.requireState(state, msg)

    companion object {
        const val BASE_URL = "https://kabinet.kgeu.ru"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) UniAutoAlarm/0.1.0"

        /** Единая конфигурация JSON — и для сети, и для golden-тестов парсера. */
        internal val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** Успех = state:1; иначе человекочитаемая ошибка сервера в msg. */
        internal fun requireState(state: Int, msg: String?) {
            if (state != 1) throw KgeuApiException(msg ?: "KGEU API error (state=$state)")
        }

        /**
         * Парсинг ответа /api/Rasp (выделен для golden-тестов, см. KgeuApiParseTest).
         * Контракт (живьём проверен, DATA-1): несуществующий idGroup отвечает
         * state:1 + rasp:[] — ШТАТНО, без ошибки; data может быть null.
         * «Пустой ответ ≠ успех-очистка» решается guard'ом в ScheduleRepository.
         */
        internal fun parseRaspResponse(raw: String): List<KgeuRaspItem> {
            val env = JSON.decodeFromString<KgeuObjectEnvelope<KgeuRaspData>>(raw)
            requireState(env.state, env.msg)
            return env.data?.rasp ?: emptyList()
        }

        /** «2026-10-12T08:00:00» → LocalDateTime; пустая/битая строка → null. */
        fun parseIsoDateTime(raw: String?): LocalDateTime? =
            if (raw.isNullOrBlank()) null
            else try {
                LocalDateTime.parse(raw)
            } catch (e: DateTimeParseException) {
                null
            }
    }
}
