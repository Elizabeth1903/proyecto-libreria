package com.bestie.lectoraao3

import android.net.Uri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object Ffn {
    const val BASE = "https://www.fanfiction.net"

    val categories = listOf(
        Category("anime", "Anime/Manga", "🌸"),
        Category("book", "Libros", "📚"),
        Category("cartoon", "Caricaturas", "💥"),
        Category("comic", "Cómics", "🦸"),
        Category("game", "Videojuegos", "🎮"),
        Category("misc", "Varios", "✨"),
        Category("movie", "Películas", "🎬"),
        Category("play", "Obras y musicales", "🎭"),
        Category("tv", "Series de TV", "📺"),
    )

    private val GENRES = listOf(
        "Hurt/Comfort", "Romance", "Humor", "Drama", "Poetry", "Adventure", "Mystery", "Horror", "Parody", "Angst",
        "Supernatural", "Suspense", "Sci-Fi", "Fantasy", "Spiritual", "Tragedy", "Western", "Crime", "Family",
        "Friendship", "General",
    )

    fun fandomsUrl(cat: String) = "$BASE/$cat/"

    fun listUrl(src: ListSource, page: Int): String {
        val f = src.filters
        if (src.query.isNotBlank()) {
            return "$BASE/search/?keywords=${Uri.encode(src.query)}&ready=1&type=story" + if (page > 1) "&ppage=$page" else ""
        }
        val lan = when (f.language) {
            "en" -> "1"
            "es" -> "2"
            else -> ""
        }
        val base = if (src.fandomUrl.startsWith("http")) src.fandomUrl else BASE + src.fandomUrl
        return base.substringBefore('?') +
            "?srt=${f.sort.ifBlank { "4" }}&r=${f.rating.ifBlank { "10" }}&lan=$lan&s=${if (f.complete) "2" else "0"}&p=$page"
    }

    fun storyUrl(id: String, chapter: String = "1") = "$BASE/s/$id/$chapter"

    fun parseFandoms(html: String): List<Fandom> {
        val doc = parseHtml(html, BASE)
        val root = doc.selectFirst("#list_output") ?: doc
        return root.select("a[href]").mapNotNull { a ->
            val href = a.attr("href")
            if (!href.startsWith("/") || href.count { it == '/' } < 3) return@mapNotNull null
            val count = a.nextElementSibling()?.text()?.trim()?.trim('(', ')').orEmpty()
            Fandom(a.attr("title").ifBlank { a.text() }, a.absUrl("href"), count, parseCount(count))
        }.distinctBy { it.url }.sortedByDescending { it.countValue }
    }

    private data class Meta(
        val fandoms: List<String> = emptyList(),
        val rating: String = "",
        val language: String = "",
        val genres: String = "",
        val characters: List<String> = emptyList(),
        val chapters: String = "1",
        val words: String = "",
        val reviews: String = "",
        val favs: String = "",
        val follows: String = "",
        val updated: String = "",
        val published: String = "",
        val complete: Boolean = false,
    )

    private fun isGenre(token: String): Boolean {
        var t = token
        GENRES.forEach { t = t.replace(it, "") }
        return t.replace("/", "").isBlank()
    }

    /** "Rated: T - English - Romance/Angst - Harry P., Draco M. - Chapters: 12 - Words: 50,321 - …" */
    private fun parseMeta(line: String): Meta {
        val tokens = line.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        val rIdx = tokens.indexOfFirst { it.startsWith("Rated:") }
        var m = Meta(fandoms = if (rIdx > 0) tokens.subList(0, rIdx).filter { it != "Crossover" } else emptyList())
        tokens.forEachIndexed { i, t ->
            when {
                t.startsWith("Rated:") -> m = m.copy(rating = t.removePrefix("Rated:").trim().removePrefix("Fiction").trim())
                i == rIdx + 1 && rIdx >= 0 -> m = m.copy(language = t)
                t.startsWith("Chapters:") -> m = m.copy(chapters = t.removePrefix("Chapters:").trim())
                t.startsWith("Words:") -> m = m.copy(words = t.removePrefix("Words:").trim())
                t.startsWith("Reviews:") -> m = m.copy(reviews = t.removePrefix("Reviews:").trim())
                t.startsWith("Favs:") -> m = m.copy(favs = t.removePrefix("Favs:").trim())
                t.startsWith("Follows:") -> m = m.copy(follows = t.removePrefix("Follows:").trim())
                t.startsWith("Updated:") -> m = m.copy(updated = t.removePrefix("Updated:").trim())
                t.startsWith("Published:") -> m = m.copy(published = t.removePrefix("Published:").trim())
                t == "Complete" || t == "Status: Complete" -> m = m.copy(complete = true)
                t.startsWith("id:") || t.contains(":") -> Unit
                i > rIdx && rIdx >= 0 && isGenre(t) -> m = m.copy(genres = t)
                i > rIdx && rIdx >= 0 -> m = m.copy(
                    characters = m.characters + t.replace("[", ",").replace("]", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
            }
        }
        return m
    }

    fun parseList(html: String): Pair<List<WorkSummary>, Boolean> {
        val doc = parseHtml(html, BASE)
        val works = doc.select("div.z-list").mapNotNull { z ->
            val a = z.selectFirst("a.stitle") ?: return@mapNotNull null
            val id = Regex("/s/(\\d+)").find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val pad = z.selectFirst("div.z-padtop")
            val metaLine = pad?.selectFirst("div.xgray")?.text().orEmpty()
            val summary = pad?.clone()?.also { it.select("div.xgray").remove() }?.html().orEmpty()
            val m = parseMeta(metaLine)
            WorkSummary(
                site = Site.FFN,
                id = id,
                title = a.text(),
                authors = listOfNotNull(z.selectFirst("a[href^=/u/]")?.text()),
                fandoms = m.fandoms,
                rating = m.rating,
                characters = m.characters,
                tags = listOf(m.genres).filter { it.isNotBlank() },
                summaryHtml = summary,
                language = m.language,
                words = m.words,
                chapters = m.chapters,
                kudos = m.favs,
                hits = m.follows,
                updated = m.updated.ifBlank { m.published },
                complete = m.complete,
                info = metaLine,
            )
        }
        val hasNext = doc.select("a").any { it.text().trim().startsWith("Next") }
        return works to hasNext
    }

    fun parseStory(id: String, html: String): WorkDetail {
        val doc = parseHtml(html, BASE)
        val top = doc.selectFirst("#profile_top")
            ?: throw ApiException("No pude leer esta historia. Puede que la hayan borrado 🥺")
        val title = top.selectFirst("b.xcontrast_txt")?.text() ?: top.selectFirst("b")?.text().orEmpty()
        val info = top.selectFirst("span.xgray")?.text().orEmpty()
        val m = parseMeta(info)
        val crumbs = doc.select("#pre_story_links a").map { it.text() }.filter { it.isNotBlank() }
        val work = WorkSummary(
            site = Site.FFN,
            id = id,
            title = title,
            authors = listOfNotNull(top.selectFirst("a[href^=/u/]")?.text()),
            fandoms = if (crumbs.size > 1) crumbs.drop(1) else crumbs,
            rating = m.rating,
            characters = m.characters,
            tags = listOf(m.genres).filter { it.isNotBlank() },
            summaryHtml = top.selectFirst("div.xcontrast_txt")?.html().orEmpty(),
            language = m.language,
            words = m.words,
            chapters = m.chapters,
            kudos = m.favs,
            hits = m.follows,
            updated = m.updated.ifBlank { m.published },
            complete = m.complete,
            info = info,
        )
        val options = doc.selectFirst("select#chap_select")?.select("option").orEmpty()
        val chapters = if (options.isNotEmpty()) {
            options.mapIndexed { i, o -> ChapterRef(i, o.attr("value"), o.text()) }
        } else {
            listOf(ChapterRef(0, "1", title))
        }
        return WorkDetail(work, emptyList(), chapters, parseChapter(doc))
    }

    fun parseChapterPage(html: String): ChapterContent =
        parseChapter(parseHtml(html, BASE)) ?: throw ApiException("No pude leer este capítulo 🥺")

    private fun parseChapter(doc: Document): ChapterContent? {
        val story: Element = doc.selectFirst("#storytext") ?: doc.selectFirst("#storycontent")
            ?: doc.selectFirst(".storytext") ?: return null
        val title = doc.selectFirst("select#chap_select option[selected]")?.text().orEmpty()
        return ChapterContent(title, emptyList(), emptyList(), HtmlBlocks.extract(story), emptyList())
    }
}
