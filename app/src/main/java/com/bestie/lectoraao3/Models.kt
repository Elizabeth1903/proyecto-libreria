package com.bestie.lectoraao3

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject

/** Un fragmento de la página (párrafo, título, nota, etiqueta…) que se puede traducir. */
data class Segment(val id: String, val html: String, val kind: String) {
    val plain: String by lazy { htmlToPlain(html) }
}

/** Contexto de la obra que se le pasa a la IA para que traduzca con coherencia. */
data class WorkMeta(
    val isWork: Boolean = false,
    val title: String = "",
    val author: String = "",
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val rating: String = "",
    val language: String = "",
    val summary: String = "",
)

data class PageInfo(
    val page: String,
    val workId: String?,
    val chapterId: String?,
    val url: String,
    val meta: WorkMeta,
    val segments: List<Segment>,
) {
    val isWork get() = page == "work" && workId != null
    val cacheKey get() = if (isWork) "work_$workId" else "page_" + Storage.hash(url.substringBefore('#'))
    val glossaryKey get() = if (isWork) "work_$workId" else "general"
    val alreadySpanish get() = isWork && meta.language.trim().startsWith("Español", ignoreCase = true)

    companion object {
        fun parse(json: String): PageInfo {
            val o = JSONObject(json)
            val m = o.optJSONObject("meta") ?: JSONObject()
            val meta = WorkMeta(
                isWork = o.optString("page") == "work",
                title = m.optString("title"),
                author = m.optString("author"),
                fandoms = m.optJSONArray("fandoms").toStringList(),
                relationships = m.optJSONArray("relationships").toStringList(),
                characters = m.optJSONArray("characters").toStringList(),
                tags = m.optJSONArray("tags").toStringList(),
                rating = m.optString("rating"),
                language = m.optString("language"),
                summary = m.optString("summary"),
            )
            val arr = o.optJSONArray("segs") ?: JSONArray()
            val segs = (0 until arr.length()).map {
                val s = arr.getJSONObject(it)
                Segment(s.getString("id"), s.getString("html"), s.optString("kind", "text"))
            }
            return PageInfo(
                page = o.optString("page", "list"),
                workId = o.optString("workId").takeIf { it.isNotBlank() && it != "null" },
                chapterId = o.optString("chapterId").takeIf { it.isNotBlank() && it != "null" },
                url = o.optString("url"),
                meta = meta,
                segments = segs,
            )
        }
    }
}

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

fun htmlToPlain(html: String): String =
    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
