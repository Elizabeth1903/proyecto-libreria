package com.bestie.lectoraao3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ItemStyle { TITLE, NOTE_HEADER, NOTE, TEXT, HR }

data class ReaderItem(val key: String, val html: String, val style: ItemStyle, val center: Boolean = false)

/** Arma la lista de elementos que muestra el lector para un capítulo. */
object Reading {
    fun items(chapter: Int, c: ChapterContent, workNotes: List<Para>): List<ReaderItem> {
        val out = mutableListOf<ReaderItem>()
        val p = "c$chapter"
        if (c.title.isNotBlank()) out.add(ReaderItem("${p}_t", c.title, ItemStyle.TITLE, true))
        fun block(tag: String, header: String, paras: List<Para>) {
            if (paras.isEmpty()) return
            out.add(ReaderItem("${p}_${tag}h", header, ItemStyle.NOTE_HEADER))
            paras.forEachIndexed { i, para ->
                out.add(ReaderItem("${p}_$tag$i", para.html, if (para.hr) ItemStyle.HR else ItemStyle.NOTE, para.center))
            }
        }
        block("s", "Resumen del capítulo", c.summary)
        block("n", "Notas", c.notes.ifEmpty { workNotes })
        c.text.forEachIndexed { i, para ->
            out.add(ReaderItem("${p}_p$i", para.html, if (para.hr) ItemStyle.HR else ItemStyle.TEXT, para.center))
        }
        block("e", "Notas finales", c.endNotes)
        return out
    }

    fun segments(items: List<ReaderItem>): List<Segment> = items
        .filter { it.style != ItemStyle.HR && it.style != ItemStyle.NOTE_HEADER }
        .map { Segment(it.key, it.html, if (it.style == ItemStyle.TITLE) "title" else "text") }
}

/** Motor de traducción: caché, lotes, contexto de párrafos anteriores y glosario por obra. */
object Tr {
    fun ready() = App.prefs.config().isReady

    suspend fun run(
        meta: WorkMeta,
        cacheKey: String,
        glossaryKey: String,
        segs: List<Segment>,
        cacheOnly: Boolean = false,
        onUpdate: suspend (Map<String, String>, Int, Int) -> Unit,
    ) {
        val cfg = App.prefs.config()
        val storage = App.storage
        val cache = withContext(Dispatchers.IO) { storage.loadCache(cacheKey) }
        val got = LinkedHashMap<String, String>()
        for (s in segs) cache[cfg.keyFor(s.html)]?.let { got[s.id] = it }
        if (got.isNotEmpty()) onUpdate(HashMap(got), 0, 0)
        if (cacheOnly) return

        val pending = withContext(Dispatchers.Default) { segs.filter { it.id !in got && hasLetters(it.html) } }
        if (pending.isEmpty() || !cfg.isReady) return

        val glossary = withContext(Dispatchers.IO) { storage.loadGlossary(glossaryKey) }
        val batches = Translator.makeBatches(pending, cfg.batchChars)
        val translator = Translator(cfg)
        val order = HashMap<String, Int>()
        segs.forEachIndexed { i, s -> order[s.id] = i }

        batches.forEachIndexed { bi, batch ->
            onUpdate(emptyMap(), bi, batches.size)
            val context = previous(segs, order[batch.first().id] ?: 0, got)
            val res = translator.translate(meta, batch, glossary, context)
            val upd = HashMap<String, String>()
            batch.forEachIndexed { j, s ->
                val t = res.translations[j]
                upd[s.id] = t
                got[s.id] = t
                cache[cfg.keyFor(s.html)] = t
            }
            for ((k, v) in res.glossary) if (!glossary.containsKey(k)) glossary[k] = v
            val cc = HashMap(cache)
            val gc = LinkedHashMap(glossary)
            withContext(Dispatchers.IO) {
                storage.saveCache(cacheKey, cc)
                storage.saveGlossary(glossaryKey, gc)
            }
            onUpdate(upd, bi + 1, batches.size)
        }
    }

    private fun previous(segs: List<Segment>, start: Int, got: Map<String, String>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var i = start - 1
        while (i >= 0 && out.size < 3) {
            val s = segs[i]
            val t = got[s.id]
            if (t != null && s.kind == "text") out.add(0, s.plain to htmlToPlain(t))
            i--
        }
        return out
    }
}
