package com.bestie.lectoraao3

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

fun parseHtml(html: String, base: String): Document =
    Jsoup.parse(html, base).apply { outputSettings().prettyPrint(false) }

private val TAG_RE = Regex("<[^>]+>")
private val SPACE_RE = Regex("\\s+")
private val LETTER_RE = Regex("\\p{L}")

fun htmlToPlain(html: String): String =
    Parser.unescapeEntities(html.replace(Regex("(?i)<br\\s*/?>"), " ").replace(TAG_RE, " "), false)
        .replace(' ', ' ').replace(SPACE_RE, " ").trim()

fun hasLetters(html: String) = LETTER_RE.containsMatchIn(htmlToPlain(html))

/** Convierte el texto de una obra en párrafos sueltos, conservando el formato en línea. */
object HtmlBlocks {
    private val BLOCK = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "td", "th", "dd", "dt", "blockquote", "div", "ul", "ol",
        "dl", "table", "tbody", "thead", "tfoot", "tr", "center", "pre", "hr", "section", "article", "aside",
        "header", "footer", "figure", "figcaption", "details", "summary", "address", "fieldset", "form", "main", "nav"
    )
    private val SKIP = setOf("script", "style", "noscript", "textarea", "select", "iframe", "svg", "canvas", "video", "audio", "button", "input")

    fun extract(root: Element?): List<Para> {
        if (root == null) return emptyList()
        val out = mutableListOf<Para>()
        walk(root, out, false)
        return out
    }

    private fun hasBlockChild(e: Element) = e.children().any { it.tagName() in BLOCK }

    private fun isCenter(e: Element): Boolean =
        e.attr("align").equals("center", true) ||
            e.attr("style").replace(" ", "").contains("text-align:center", true) ||
            e.tagName() == "center"

    private fun walk(e: Element, out: MutableList<Para>, center: Boolean) {
        val tag = e.tagName()
        if (tag in SKIP || e.hasClass("landmark") || e.hasClass("xgray")) return
        val c = center || isCenter(e)
        if (tag == "hr") {
            out.add(Para("", c, hr = true))
            return
        }
        if (!hasBlockChild(e)) {
            addLeaf(e.html(), c, out)
            return
        }
        val run = StringBuilder()
        fun flush() {
            if (run.isNotBlank()) addLeaf(run.toString(), c, out)
            run.setLength(0)
        }
        for (n in e.childNodes()) {
            if (n is Element && n.tagName() in BLOCK) {
                flush()
                walk(n, out, c)
            } else if (n is Element && n.tagName() in SKIP) {
                flush()
            } else {
                run.append(n.outerHtml())
            }
        }
        flush()
    }

    private val BR = Regex("(?i)<br\\s*/?>")

    private fun addLeaf(html: String, center: Boolean, out: MutableList<Para>) {
        val h = html.trim()
        if (htmlToPlain(h).isBlank()) return
        if (h.length > 3000 && BR.containsMatchIn(h)) {
            // Capítulos viejos sin párrafos: los partimos en trozos legibles
            val chunk = StringBuilder()
            for (line in h.split(BR)) {
                if (chunk.isNotEmpty()) chunk.append("<br>")
                chunk.append(line)
                if (chunk.length > 1200) {
                    if (htmlToPlain(chunk.toString()).isNotBlank()) out.add(Para(chunk.toString(), center))
                    chunk.setLength(0)
                }
            }
            if (htmlToPlain(chunk.toString()).isNotBlank()) out.add(Para(chunk.toString(), center))
        } else {
            out.add(Para(h, center))
        }
    }
}

/** "1.2K" / "848K" / "12,345" → número, para ordenar fandoms. */
fun parseCount(s: String): Long {
    val t = s.trim().replace(",", "").replace("(", "").replace(")", "").uppercase()
    if (t.isEmpty()) return 0
    val mult = when {
        t.endsWith("K") -> 1_000.0
        t.endsWith("M") -> 1_000_000.0
        else -> 1.0
    }
    val num = t.trimEnd('K', 'M').toDoubleOrNull() ?: return 0
    return (num * mult).toLong()
}

fun shortCount(s: String): String {
    val n = parseCount(s)
    return when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 10_000 -> "${n / 1000}k"
        n >= 1_000 -> String.format("%.1fk", n / 1000.0)
        else -> s.ifBlank { "0" }
    }
}
