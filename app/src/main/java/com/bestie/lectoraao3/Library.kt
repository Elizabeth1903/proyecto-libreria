package com.bestie.lectoraao3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LibEntry(
    val work: WorkSummary,
    val saved: Boolean = false,
    val chapterIndex: Int = 0,
    val itemIndex: Int = 0,
    val totalChapters: Int = 0,
    val lastRead: Long = 0,
    val downloaded: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("work", work.toJson()).put("saved", saved)
        .put("ch", chapterIndex).put("item", itemIndex).put("total", totalChapters)
        .put("last", lastRead).put("dl", downloaded)

    companion object {
        fun fromJson(o: JSONObject) = LibEntry(
            WorkSummary.fromJson(o.getJSONObject("work")), o.optBoolean("saved"), o.optInt("ch"),
            o.optInt("item"), o.optInt("total"), o.optLong("last"), o.optInt("dl"),
        )
    }
}

/** Biblioteca: guardados, historial con progreso y descargados. */
object Library {
    val entries = MutableStateFlow<Map<String, LibEntry>>(emptyMap())

    fun init() {
        val f = App.storage.libraryFile
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            val map = LinkedHashMap<String, LibEntry>()
            for (i in 0 until arr.length()) {
                val e = LibEntry.fromJson(arr.getJSONObject(i))
                map[e.work.key] = e
            }
            entries.value = map
        } catch (_: Exception) {
        }
    }

    fun get(key: String) = entries.value[key]

    private fun update(work: WorkSummary, change: (LibEntry) -> LibEntry?) {
        val map = LinkedHashMap(entries.value)
        val old = map[work.key] ?: LibEntry(work)
        val new = change(old.copy(work = mergeWork(old.work, work)))
        if (new == null) map.remove(work.key) else map[work.key] = new
        entries.value = map
        persist(map)
    }

    /** La ficha de una lista trae menos datos que la de la obra: no perdemos lo que ya sabíamos. */
    private fun mergeWork(old: WorkSummary, new: WorkSummary) =
        if (new.summaryHtml.isBlank() && old.summaryHtml.isNotBlank()) new.copy(summaryHtml = old.summaryHtml) else new

    private fun persist(map: Map<String, LibEntry>) {
        val arr = JSONArray()
        map.values.forEach { arr.put(it.toJson()) }
        val text = arr.toString()
        App.scope.launch(Dispatchers.IO) { App.storage.writeText(App.storage.libraryFile, text) }
    }

    fun toggleSaved(work: WorkSummary) = update(work) { it.copy(saved = !it.saved) }

    fun setProgress(work: WorkSummary, chapter: Int, item: Int, total: Int) =
        update(work) { it.copy(chapterIndex = chapter, itemIndex = item, totalChapters = total, lastRead = System.currentTimeMillis()) }

    fun setDownloaded(work: WorkSummary, count: Int, total: Int) =
        update(work) { it.copy(downloaded = count, totalChapters = maxOf(total, it.totalChapters)) }

    fun removeFromHistory(work: WorkSummary) = update(work) {
        val e = it.copy(lastRead = 0, chapterIndex = 0, itemIndex = 0)
        if (!e.saved && e.downloaded == 0) null else e
    }

    fun remove(work: WorkSummary) {
        App.storage.deleteChapters(work.key)
        update(work) { null }
    }
}
