package com.bestie.lectoraao3

import org.json.JSONArray
import org.json.JSONObject

enum class Site(val id: String, val label: String) {
    AO3("ao3", "AO3"),
    FFN("ffn", "FanFiction.net");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: AO3
    }
}

data class Category(val id: String, val name: String, val emoji: String)

data class Fandom(val name: String, val url: String, val count: String, val countValue: Long)

/** Un párrafo listo para mostrar: HTML en línea (cursivas, negritas…), centrado o separador. */
data class Para(val html: String, val center: Boolean = false, val hr: Boolean = false)

data class ChapterRef(val index: Int, val id: String, val title: String)

data class ChapterContent(
    val title: String,
    val summary: List<Para>,
    val notes: List<Para>,
    val text: List<Para>,
    val endNotes: List<Para>,
)

data class WorkSummary(
    val site: Site,
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val fandoms: List<String> = emptyList(),
    val rating: String = "",
    val warnings: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summaryHtml: String = "",
    val language: String = "",
    val words: String = "",
    val chapters: String = "",
    val kudos: String = "",
    val hits: String = "",
    val updated: String = "",
    val complete: Boolean = false,
    val info: String = "",
) {
    val key get() = "${site.id}_$id"
    val author get() = authors.joinToString(", ").ifBlank { "Anónimo" }
    val isSpanish get() = language.trim().let { it.startsWith("Español", true) || it.equals("Spanish", true) }
    val webUrl
        get() = when (site) {
            Site.AO3 -> "${Ao3.BASE}/works/$id"
            Site.FFN -> "${Ffn.BASE}/s/$id/1"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("site", site.id).put("id", id).put("title", title)
        .put("authors", JSONArray(authors)).put("fandoms", JSONArray(fandoms))
        .put("rating", rating).put("warnings", JSONArray(warnings)).put("categories", JSONArray(categories))
        .put("relationships", JSONArray(relationships)).put("characters", JSONArray(characters))
        .put("tags", JSONArray(tags)).put("summary", summaryHtml).put("language", language)
        .put("words", words).put("chapters", chapters).put("kudos", kudos).put("hits", hits)
        .put("updated", updated).put("complete", complete).put("info", info)

    fun toMeta() = WorkMeta(
        isWork = true,
        site = site.id,
        title = title,
        author = author,
        fandoms = fandoms,
        relationships = relationships,
        characters = characters,
        tags = tags.take(30),
        rating = rating,
        language = language,
        summary = htmlToPlain(summaryHtml).take(1500),
        info = info,
    )

    companion object {
        fun fromJson(o: JSONObject) = WorkSummary(
            site = Site.of(o.optString("site")),
            id = o.optString("id"),
            title = o.optString("title"),
            authors = o.optJSONArray("authors").toStringList(),
            fandoms = o.optJSONArray("fandoms").toStringList(),
            rating = o.optString("rating"),
            warnings = o.optJSONArray("warnings").toStringList(),
            categories = o.optJSONArray("categories").toStringList(),
            relationships = o.optJSONArray("relationships").toStringList(),
            characters = o.optJSONArray("characters").toStringList(),
            tags = o.optJSONArray("tags").toStringList(),
            summaryHtml = o.optString("summary"),
            language = o.optString("language"),
            words = o.optString("words"),
            chapters = o.optString("chapters"),
            kudos = o.optString("kudos"),
            hits = o.optString("hits"),
            updated = o.optString("updated"),
            complete = o.optBoolean("complete"),
            info = o.optString("info"),
        )
    }
}

data class WorkDetail(
    val work: WorkSummary,
    val notes: List<Para>,
    val chapters: List<ChapterRef>,
    val firstChapter: ChapterContent? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("work", work.toJson())
        .put("notes", parasToJson(notes))
        .put("chapters", JSONArray().apply {
            chapters.forEach { put(JSONObject().put("i", it.index).put("id", it.id).put("t", it.title)) }
        })

    companion object {
        fun fromJson(o: JSONObject): WorkDetail {
            val arr = o.optJSONArray("chapters") ?: JSONArray()
            return WorkDetail(
                work = WorkSummary.fromJson(o.getJSONObject("work")),
                notes = parasFromJson(o.optJSONArray("notes")),
                chapters = (0 until arr.length()).map {
                    val c = arr.getJSONObject(it)
                    ChapterRef(c.optInt("i"), c.optString("id"), c.optString("t"))
                },
            )
        }
    }
}

fun chapterToJson(c: ChapterContent): JSONObject = JSONObject()
    .put("title", c.title)
    .put("summary", parasToJson(c.summary))
    .put("notes", parasToJson(c.notes))
    .put("text", parasToJson(c.text))
    .put("end", parasToJson(c.endNotes))

fun chapterFromJson(o: JSONObject) = ChapterContent(
    title = o.optString("title"),
    summary = parasFromJson(o.optJSONArray("summary")),
    notes = parasFromJson(o.optJSONArray("notes")),
    text = parasFromJson(o.optJSONArray("text")),
    endNotes = parasFromJson(o.optJSONArray("end")),
)

fun parasToJson(list: List<Para>) = JSONArray().apply {
    list.forEach { put(JSONObject().put("h", it.html).put("c", it.center).put("r", it.hr)) }
}

fun parasFromJson(arr: JSONArray?): List<Para> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Para(o.optString("h"), o.optBoolean("c"), o.optBoolean("r"))
    }
}

/** Filtros de una lista: los valores dependen del sitio (ver [Options]). */
data class Filters(
    val sort: String = "",
    val complete: Boolean = false,
    val language: String = "",
    val rating: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("sort", sort).put("complete", complete)
        .put("language", language).put("rating", rating)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Filters() else Filters(
            o.optString("sort"), o.optBoolean("complete"), o.optString("language"), o.optString("rating")
        )
    }
}

/** De dónde sale una lista de fics: un fandom o una búsqueda. */
data class ListSource(
    val site: Site,
    val title: String,
    val fandom: String = "",
    val fandomUrl: String = "",
    val query: String = "",
    val filters: Filters = Filters(),
) {
    fun toJson(): String = JSONObject().put("site", site.id).put("title", title).put("fandom", fandom)
        .put("fandomUrl", fandomUrl).put("query", query).put("filters", filters.toJson()).toString()

    companion object {
        fun fromJson(s: String): ListSource {
            val o = JSONObject(s)
            return ListSource(
                Site.of(o.optString("site")), o.optString("title"), o.optString("fandom"),
                o.optString("fandomUrl"), o.optString("query"), Filters.fromJson(o.optJSONObject("filters"))
            )
        }
    }
}

/** Contexto de la obra para la IA. */
data class WorkMeta(
    val isWork: Boolean = false,
    val site: String = "ao3",
    val title: String = "",
    val author: String = "",
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val rating: String = "",
    val language: String = "",
    val summary: String = "",
    val info: String = "",
)

/** Un fragmento a traducir. */
data class Segment(val id: String, val html: String, val kind: String) {
    val plain: String by lazy { htmlToPlain(html) }
}

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
