package com.bestie.lectoraao3

import android.net.Uri
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object Ao3 {
    const val BASE = "https://archiveofourown.org"

    val categories = listOf(
        Category("Anime & Manga", "Anime y manga", "🌸"),
        Category("Books & Literature", "Libros y literatura", "📚"),
        Category("Cartoons & Comics & Graphic Novels", "Caricaturas y cómics", "💥"),
        Category("Celebrities & Real People", "Personas reales", "⭐"),
        Category("Movies", "Películas", "🎬"),
        Category("Music & Bands", "Música y bandas", "🎤"),
        Category("Other Media", "Otros medios", "✨"),
        Category("Theater", "Teatro", "🎭"),
        Category("TV Shows", "Series de TV", "📺"),
        Category("Video Games", "Videojuegos", "🎮"),
        Category("Uncategorized Fandoms", "Sin categoría", "💫"),
    )

    fun fandomsUrl(category: String) = "$BASE/media/" + Uri.encode(category.replace(" & ", " *a* ")) + "/fandoms"

    fun searchUrl(src: ListSource, page: Int): String {
        val f = src.filters
        val b = Uri.parse("$BASE/works/search").buildUpon()
        b.appendQueryParameter("work_search[query]", src.query)
        if (src.fandom.isNotBlank()) b.appendQueryParameter("work_search[fandom_names]", src.fandom)
        b.appendQueryParameter("work_search[sort_column]", f.sort.ifBlank { if (src.query.isBlank()) "kudos_count" else "_score" })
        b.appendQueryParameter("work_search[sort_direction]", "desc")
        if (f.complete) b.appendQueryParameter("work_search[complete]", "T")
        if (f.language.isNotBlank()) b.appendQueryParameter("work_search[language_id]", f.language)
        if (f.rating.isNotBlank()) b.appendQueryParameter("work_search[rating_ids]", f.rating)
        b.appendQueryParameter("commit", "Search")
        if (page > 1) b.appendQueryParameter("page", page.toString())
        return b.build().toString()
    }

    fun workUrl(id: String) = "$BASE/works/$id?view_adult=true"
    fun chapterUrl(id: String, chapterId: String) =
        if (chapterId.isBlank()) workUrl(id) else "$BASE/works/$id/chapters/$chapterId?view_adult=true"
    fun fullWorkUrl(id: String) = "$BASE/works/$id?view_full_work=true&view_adult=true"

    fun parseFandoms(html: String): List<Fandom> {
        val doc = parseHtml(html, BASE)
        return doc.select("ol.alphabet li.letter ul.tags li").mapNotNull { li ->
            val a = li.selectFirst("a.tag") ?: return@mapNotNull null
            val count = Regex("\\(([\\d,]+)\\)").find(li.ownText())?.groupValues?.get(1) ?: ""
            Fandom(a.text(), a.absUrl("href"), count, parseCount(count))
        }.sortedByDescending { it.countValue }
    }

    fun parseList(html: String): Pair<List<WorkSummary>, Boolean> {
        val doc = parseHtml(html, BASE)
        val works = doc.select("li.work.blurb, li.blurb.work").mapNotNull { parseBlurb(it) }
        val hasNext = doc.selectFirst("ol.pagination li.next a") != null
        return works to hasNext
    }

    private fun texts(e: Element, sel: String) = e.select(sel).map { it.text() }.filter { it.isNotBlank() }

    private fun parseBlurb(li: Element): WorkSummary? {
        val titleA = li.selectFirst("h4.heading a[href^=/works/]") ?: return null
        val id = Regex("/works/(\\d+)").find(titleA.attr("href"))?.groupValues?.get(1) ?: return null
        val req = li.selectFirst("ul.required-tags")
        return WorkSummary(
            site = Site.AO3,
            id = id,
            title = titleA.text(),
            authors = texts(li, "h4.heading a[rel=author]"),
            fandoms = texts(li, "h5.fandoms a.tag"),
            rating = req?.selectFirst("span.rating")?.attr("title").orEmpty(),
            warnings = req?.selectFirst("span.warnings")?.attr("title")?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
            categories = req?.selectFirst("span.category")?.attr("title")?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
            relationships = texts(li, "ul.tags li.relationships a.tag"),
            characters = texts(li, "ul.tags li.characters a.tag"),
            tags = texts(li, "ul.tags li.freeforms a.tag"),
            summaryHtml = li.selectFirst("blockquote.userstuff.summary")?.html().orEmpty(),
            language = li.selectFirst("dd.language")?.text().orEmpty(),
            words = li.selectFirst("dd.words")?.text().orEmpty(),
            chapters = li.selectFirst("dd.chapters")?.text().orEmpty(),
            kudos = li.selectFirst("dd.kudos")?.text().orEmpty(),
            hits = li.selectFirst("dd.hits")?.text().orEmpty(),
            updated = li.selectFirst("p.datetime")?.text().orEmpty(),
            complete = req?.selectFirst("span.iswip")?.hasClass("complete-yes") ?: false,
        )
    }

    private fun isRestricted(doc: Document, html: String) =
        doc.selectFirst("#workskin") == null &&
            (html.contains("only available to registered users", true) || doc.selectFirst("form#new_user") != null ||
                doc.location().contains("/users/login"))

    fun parseWork(id: String, html: String): WorkDetail {
        val doc = parseHtml(html, BASE)
        if (isRestricted(doc, html)) throw ApiException("Esta obra es solo para usuarios registrados. Inicia sesión en AO3 desde Ajustes 🔒")
        if (doc.selectFirst("#workskin") == null) throw ApiException("No pude leer esta obra 🥺")
        val meta = doc.selectFirst("dl.work.meta") ?: doc
        val chaptersText = meta.selectFirst("dd.stats dd.chapters")?.text().orEmpty()
        val parts = chaptersText.split("/")
        val complete = parts.size == 2 && parts[0].trim() == parts[1].trim()
        val title = doc.selectFirst("#workskin .preface h2.title")?.text().orEmpty()
        val work = WorkSummary(
            site = Site.AO3,
            id = id,
            title = title,
            authors = doc.select("#workskin .preface h3.byline a[rel=author]").map { it.text() }
                .ifEmpty { listOfNotNull(doc.selectFirst("#workskin .preface h3.byline")?.text()) },
            fandoms = texts(meta, "dd.fandom a.tag"),
            rating = texts(meta, "dd.rating a.tag").joinToString(", "),
            warnings = texts(meta, "dd.warning a.tag"),
            categories = texts(meta, "dd.category a.tag"),
            relationships = texts(meta, "dd.relationship a.tag"),
            characters = texts(meta, "dd.character a.tag"),
            tags = texts(meta, "dd.freeform a.tag"),
            summaryHtml = doc.selectFirst("#workskin .preface .summary blockquote.userstuff")?.html().orEmpty(),
            language = meta.selectFirst("dd.language")?.text().orEmpty(),
            words = meta.selectFirst("dd.stats dd.words")?.text().orEmpty(),
            chapters = chaptersText,
            kudos = meta.selectFirst("dd.stats dd.kudos")?.text().orEmpty(),
            hits = meta.selectFirst("dd.stats dd.hits")?.text().orEmpty(),
            updated = (meta.selectFirst("dd.stats dd.status") ?: meta.selectFirst("dd.stats dd.published"))?.text().orEmpty(),
            complete = complete,
        )
        val options = doc.select("select#selected_id option")
        val chapters = if (options.isNotEmpty()) {
            options.mapIndexed { i, o -> ChapterRef(i, o.attr("value"), o.text()) }
        } else {
            listOf(ChapterRef(0, "", title))
        }
        val notes = HtmlBlocks.extract(doc.selectFirst("#workskin > .preface .notes blockquote.userstuff"))
        return WorkDetail(work, notes, chapters, parseChapter(doc))
    }

    fun parseChapterPage(html: String): ChapterContent {
        val doc = parseHtml(html, BASE)
        if (isRestricted(doc, html)) throw ApiException("Este capítulo es solo para usuarios registrados. Inicia sesión en AO3 desde Ajustes 🔒")
        return parseChapter(doc) ?: throw ApiException("No pude leer este capítulo 🥺")
    }

    /** Todos los capítulos de una vez (para descargar). */
    fun parseFullWork(html: String): List<ChapterContent> {
        val doc = parseHtml(html, BASE)
        val chs = doc.select("#chapters > div.chapter")
        if (chs.isEmpty()) return listOfNotNull(parseChapter(doc))
        return chs.map { parseChapterElement(it, null) }
    }

    private fun parseChapter(doc: Document): ChapterContent? {
        val chapters = doc.selectFirst("#chapters") ?: return null
        val ch = chapters.children().firstOrNull { it.tagName() == "div" && it.hasClass("chapter") }
        return if (ch != null) parseChapterElement(ch, null) else parseChapterElement(chapters, doc)
    }

    private fun parseChapterElement(ch: Element, oneShotDoc: Document?): ChapterContent {
        val textEl = ch.selectFirst("div.userstuff.module") ?: ch.selectFirst("div.userstuff")
        val endNotes = ch.selectFirst(".end.notes blockquote.userstuff")
            ?: oneShotDoc?.selectFirst("#work_endnotes blockquote.userstuff")
        return ChapterContent(
            title = ch.selectFirst("h3.title")?.text().orEmpty(),
            summary = HtmlBlocks.extract(ch.selectFirst("div.summary blockquote.userstuff")),
            notes = if (oneShotDoc != null) emptyList() else HtmlBlocks.extract(ch.selectFirst("div.notes:not(.end) blockquote.userstuff")),
            text = HtmlBlocks.extract(textEl),
            endNotes = HtmlBlocks.extract(endNotes),
        )
    }
}
