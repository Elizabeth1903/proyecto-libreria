package com.bestie.lectoraao3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val retryable: Boolean = false, val waitMs: Long = 0) : Exception(message)
class FormatException(message: String) : Exception(message)

data class BatchResult(val translations: List<String>, val glossary: Map<String, String>)

/**
 * Traduce lotes de fragmentos HTML usando un LLM, con el contexto de la obra
 * (fandom, personajes, relaciones, resumen), un glosario persistente y los
 * párrafos anteriores ya traducidos, para que la traducción sea coherente.
 */
class Translator(private val cfg: TranslationConfig) {

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_SEGMENTS_PER_BATCH = 40

        fun makeBatches(segs: List<Segment>, maxChars: Int): List<List<Segment>> {
            val out = mutableListOf<List<Segment>>()
            var cur = mutableListOf<Segment>()
            var size = 0
            for (s in segs) {
                val len = s.html.length
                if (cur.isNotEmpty() && (size + len > maxChars || cur.size >= MAX_SEGMENTS_PER_BATCH)) {
                    out.add(cur)
                    cur = mutableListOf()
                    size = 0
                }
                cur.add(s)
                size += len
            }
            if (cur.isNotEmpty()) out.add(cur)
            return out
        }
    }

    suspend fun translate(
        meta: WorkMeta,
        segs: List<Segment>,
        glossary: Map<String, String>,
        context: List<Pair<String, String>>,
    ): BatchResult {
        return try {
            withRetries {
                val raw = complete(systemPrompt(meta), userPrompt(segs, glossary, context))
                parse(raw, segs.size)
            }
        } catch (e: FormatException) {
            // Si la IA se confundió con el lote, lo partimos a la mitad y probamos de nuevo
            if (segs.size <= 1) throw ApiException("La IA devolvió una respuesta con formato inesperado (${e.message})")
            val mid = segs.size / 2
            val first = segs.subList(0, mid)
            val a = translate(meta, first, glossary, context)
            val ctxB = (context + first.zip(a.translations).map { it.first.plain to htmlToPlain(it.second) }).takeLast(3)
            val b = translate(meta, segs.subList(mid, segs.size), glossary + a.glossary, ctxB)
            BatchResult(a.translations + b.translations, a.glossary + b.glossary)
        }
    }

    private suspend fun <T> withRetries(block: suspend () -> T): T {
        var attempt = 0
        var formatFails = 0
        while (true) {
            try {
                return block()
            } catch (e: FormatException) {
                formatFails++
                if (formatFails >= 2) throw e
            } catch (e: ApiException) {
                if (!e.retryable || attempt >= 4) throw e
                delay(if (e.waitMs > 0) e.waitMs.coerceAtMost(60_000) else 2000L shl attempt)
            } catch (e: IOException) {
                if (attempt >= 3) throw e
                delay(2000L shl attempt)
            }
            attempt++
        }
        throw IllegalStateException("unreachable")
    }

    // ---------------------------------------------------------------- prompts

    private fun variantDescription() = when (cfg.variant) {
        "es" -> "Peninsular Spanish (Spain): use 'vosotros' for informal plural you, Spain vocabulary and idioms"
        "rioplatense" -> "Rioplatense Spanish (Argentina/Uruguay): use 'vos' with voseo verb forms for informal you, 'ustedes' for plural, rioplatense vocabulary"
        else -> "Latin American Spanish: neutral and natural across Latin America, 'tú' for informal you, 'ustedes' for plural (never 'vosotros'), avoid Spain-only slang"
    }

    private fun systemPrompt(meta: WorkMeta): String = buildString {
        appendLine("You are an award-winning literary translator who specializes in fanfiction. Translate from the source language (auto-detect; usually English) into ${variantDescription()}.")
        appendLine()
        if (meta.isWork) {
            appendLine("## The work you are translating")
            appendLine("- Source: ${if (meta.site == "ffn") "FanFiction.net / FictionPress" else "Archive of Our Own (AO3)"}")
            if (meta.title.isNotBlank()) appendLine("- Title: ${meta.title}")
            if (meta.fandoms.isNotEmpty()) appendLine("- Fandom(s): ${meta.fandoms.joinToString(", ")}")
            if (meta.relationships.isNotEmpty()) appendLine("- Relationships: ${meta.relationships.joinToString(", ")}")
            if (meta.characters.isNotEmpty()) appendLine("- Characters: ${meta.characters.joinToString(", ")}")
            if (meta.tags.isNotEmpty()) appendLine("- Tags: ${meta.tags.joinToString(", ")}")
            if (meta.rating.isNotBlank()) appendLine("- Rating: ${meta.rating}")
            if (meta.info.isNotBlank()) appendLine("- Story info line (genres, characters in [pairings], status): ${meta.info}")
            if (meta.summary.isNotBlank()) appendLine("- Summary: ${meta.summary.take(1200)}")
            appendLine()
        } else {
            appendLine("You are translating a fanfiction archive listing page (AO3 or FanFiction.net): each segment may come from a different work (summaries, notes, tags).")
            appendLine()
        }
        appendLine("## How to translate")
        appendLine("1. Write natural, idiomatic, flowing Spanish that reads like a professionally published novel, never a literal word-for-word rendering. Keep the author's voice, tone, register, humor, rhythm, tension and level of profanity or explicitness exactly as in the original — do not soften, censor or embellish.")
        appendLine("2. Use the work context and the previous passage to resolve ambiguity: who is speaking, the gender and number agreement of every character (follow their canon genders and any pronouns stated in the text; for they/them characters use natural gender-neutral phrasing), tú/usted choices according to the relationship, double meanings and puns (adapt them so they still work in Spanish).")
        appendLine("3. Never translate character names, nicknames used as names, or fandom-specific proper nouns, unless the fandom has a well-known official Spanish translation (then use it). Always follow the glossary.")
        if (cfg.honorifics) {
            appendLine("4. Keep honorifics and culture-specific address terms as they are (-san, -kun, -chan, -senpai, hyung, oppa, unnie, sunbae, ge, jiejie, etc.).")
        } else {
            appendLine("4. Adapt honorifics into natural Spanish equivalents or drop them when Spanish would not use them.")
        }
        if (cfg.raya) {
            appendLine("5. Dialogue: convert quotation-mark dialogue into Spanish raya dialogue (—) following RAE conventions, e.g. “Come here,” she said. → —Ven aquí —dijo ella. Keep it consistent across segments; a paragraph that starts with dialogue starts with —.")
        } else {
            appendLine("5. Dialogue: keep the same quotation style as the original, but use Spanish punctuation (¿? ¡!).")
        }
        appendLine("6. Always use opening ¿ and ¡. Italic thoughts stay italic. Onomatopoeia and texting style may be adapted to how Spanish speakers write them.")
        appendLine("7. Preserve ALL HTML tags and their attributes exactly (<em>, <strong>, <i>, <b>, <u>, <s>, <br>, <span class=…>, <a href=…>, <img>…). Move tags only so they wrap the equivalent translated words. Never add <p> or other wrappers, never drop <br>.")
        appendLine("8. Translate author notes, summaries, chapter titles and tags too. 'Chapter 3' → 'Capítulo 3'. For AO3 tags and fandom slang use the terms Spanish-speaking fandom actually uses (fluff, angst, smut, AU, slow burn, enemies to lovers, omegaverse terms… are usually kept in English or adapted as the community does).")
        appendLine("9. If a segment is already in Spanish, is only symbols, a URL, or a scene separator, return it unchanged.")
        if (cfg.custom.isNotBlank()) {
            appendLine("10. Reader's personal instructions (highest priority): ${cfg.custom.trim()}")
        }
        appendLine()
        appendLine("## Output format")
        appendLine("Reply with ONLY a JSON object, no markdown fences, no comments:")
        appendLine("{\"t\": [\"translation of segment 0\", \"translation of segment 1\", ...], \"g\": {\"source term\": \"Spanish rendering\"}}")
        appendLine("- \"t\" must contain exactly one string per input segment, in the same order.")
        appendLine("- \"g\" lists only NEW recurring terms worth keeping consistent in future chapters (invented words, nicknames and pet names, titles, ranks, magic or world-building terms, places, catchphrases) with the rendering you chose. Use {} if there are none. Do not repeat glossary entries.")
    }

    private fun userPrompt(
        segs: List<Segment>,
        glossary: Map<String, String>,
        context: List<Pair<String, String>>,
    ): String = buildString {
        if (glossary.isNotEmpty()) {
            appendLine("GLOSSARY (mandatory, keep these renderings):")
            glossary.entries.take(200).forEach { appendLine("- ${it.key} → ${it.value}") }
            appendLine()
        }
        if (context.isNotEmpty()) {
            appendLine("PREVIOUS PASSAGE (context only, do not translate again):")
            context.forEach { (orig, tr) ->
                appendLine("ORIGINAL: ${orig.take(700)}")
                appendLine("SPANISH: ${tr.take(700)}")
            }
            appendLine()
        }
        appendLine("Translate these ${segs.size} segments (JSON array of HTML strings):")
        append(JSONArray(segs.map { it.html }).toString())
    }

    // ---------------------------------------------------------------- parsing

    private fun parse(raw: String, n: Int): BatchResult {
        var s = raw.trim()
        if (s.startsWith("```")) s = s.substringAfter('\n').substringBeforeLast("```").trim()
        val (arr, gloss) = try {
            if (s.startsWith("[")) {
                JSONArray(s) to emptyMap<String, String>()
            } else {
                val start = s.indexOf('{')
                val end = s.lastIndexOf('}')
                if (start < 0 || end <= start) throw FormatException("sin JSON")
                val obj = JSONObject(s.substring(start, end + 1))
                val t = obj.optJSONArray("t") ?: obj.optJSONArray("translations")
                    ?: throw FormatException("falta el campo t")
                val g = obj.optJSONObject("g")
                val map = LinkedHashMap<String, String>()
                if (g != null) for (k in g.keys()) {
                    val v = g.optString(k)
                    if (k.isNotBlank() && v.isNotBlank()) map[k.trim()] = v.trim()
                }
                t to map
            }
        } catch (e: JSONException) {
            throw FormatException(e.message ?: "JSON inválido")
        }
        if (arr.length() != n) throw FormatException("esperaba $n fragmentos y llegaron ${arr.length()}")
        return BatchResult((0 until n).map { arr.optString(it) }, gloss)
    }

    // ---------------------------------------------------------------- providers

    private suspend fun complete(system: String, user: String): String = withContext(Dispatchers.IO) {
        when (cfg.provider) {
            Prefs.ANTHROPIC -> anthropic(system, user)
            Prefs.OPENAI -> openAi(system, user)
            else -> gemini(system, user)
        }
    }

    private fun gemini(system: String, user: String): String {
        val model = cfg.model.ifBlank { Prefs.defaultModel(Prefs.GEMINI) }.removePrefix("models/")
        val safety = JSONArray()
        listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
        ).forEach { safety.put(JSONObject().put("category", it).put("threshold", "BLOCK_NONE")) }

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
                )
            )
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            .put("safetySettings", safety)

        val json = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body,
            mapOf("x-goog-api-key" to cfg.apiKey),
        )
        val cands = json.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw ApiException("Gemini bloqueó este fragmento ${if (reason.isNotBlank()) "($reason)" else ""}. Prueba con otro proveedor.")
        }
        val c0 = cands.getJSONObject(0)
        val parts = c0.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        if (sb.isBlank()) {
            val fr = c0.optString("finishReason")
            if (fr == "MAX_TOKENS") throw FormatException("respuesta cortada")
            throw ApiException("Gemini no devolvió texto ($fr). Prueba con otro modelo o proveedor.")
        }
        return sb.toString()
    }

    private fun anthropic(system: String, user: String): String {
        val body = JSONObject()
            .put("model", cfg.model.ifBlank { Prefs.defaultModel(Prefs.ANTHROPIC) })
            .put("max_tokens", 16000)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val json = post(
            "https://api.anthropic.com/v1/messages",
            body,
            mapOf("x-api-key" to cfg.apiKey, "anthropic-version" to "2023-06-01"),
        )
        val content = json.optJSONArray("content") ?: throw ApiException("Respuesta vacía de Claude")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val c = content.getJSONObject(i)
            if (c.optString("type") == "text") sb.append(c.optString("text"))
        }
        if (sb.isBlank()) throw ApiException("Claude no devolvió texto (${json.optString("stop_reason")})")
        return sb.toString()
    }

    private fun openAi(system: String, user: String): String {
        val body = JSONObject()
            .put("model", cfg.model)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        val headers = if (cfg.apiKey.isNotBlank()) mapOf("Authorization" to "Bearer ${cfg.apiKey}") else emptyMap()
        val json = post(cfg.baseUrl.trimEnd('/') + "/chat/completions", body, headers)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw ApiException("Respuesta vacía del modelo")
        val text = choice.optJSONObject("message")?.optString("content").orEmpty()
        if (text.isBlank()) throw ApiException("El modelo no devolvió texto (${choice.optString("finish_reason")})")
        return text
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = extractError(text)
                throw when (resp.code) {
                    429 -> ApiException(
                        "Llegaste al límite de uso de tu API (429). $msg",
                        retryable = true,
                        waitMs = (resp.header("retry-after")?.toLongOrNull() ?: 0L) * 1000L,
                    )
                    500, 502, 503, 504, 529 -> ApiException("El servidor de la IA está ocupado (${resp.code}).", retryable = true)
                    401, 403 -> ApiException("La API key no es válida o no tiene permisos (${resp.code}). $msg")
                    404 -> ApiException("No encontré ese modelo; revisa el nombre en Ajustes. $msg")
                    else -> ApiException("Error ${resp.code}: $msg")
                }
            }
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw ApiException("Respuesta no válida del servidor")
            }
        }
    }

    private fun extractError(text: String): String = try {
        val o = JSONObject(text)
        val e = o.opt("error")
        when (e) {
            is JSONObject -> e.optString("message")
            is String -> e
            else -> o.optString("message")
        }.take(300)
    } catch (_: Exception) {
        text.take(200)
    }
}
