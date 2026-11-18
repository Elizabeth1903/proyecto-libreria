package com.bestie.lectoraao3

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Traducciones, glosarios, capítulos offline y fichas guardadas en la memoria interna. */
class Storage(context: Context) {
    private val root = context.filesDir
    private val cacheDir = File(root, "traducciones").apply { mkdirs() }
    private val glossDir = File(root, "glosarios").apply { mkdirs() }
    private val chapDir = File(root, "capitulos").apply { mkdirs() }
    private val detailDir = File(root, "fichas").apply { mkdirs() }
    val libraryFile = File(root, "biblioteca.json")

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_\\-]"), "_")

    fun loadCache(key: String): MutableMap<String, String> = readMap(File(cacheDir, safe(key) + ".json"), HashMap())
    fun saveCache(key: String, map: Map<String, String>) = writeMap(File(cacheDir, safe(key) + ".json"), map)
    fun deleteCache(key: String) {
        File(cacheDir, safe(key) + ".json").delete()
    }

    fun clearAllTranslations() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun loadGlossary(key: String): LinkedHashMap<String, String> =
        readMap(File(glossDir, safe(key) + ".json"), LinkedHashMap())

    fun saveGlossary(key: String, map: Map<String, String>) = writeMap(File(glossDir, safe(key) + ".json"), map)

    fun saveChapter(workKey: String, index: Int, c: ChapterContent) {
        val dir = File(chapDir, safe(workKey)).apply { mkdirs() }
        writeText(File(dir, "$index.json"), chapterToJson(c).toString())
    }

    fun loadChapter(workKey: String, index: Int): ChapterContent? {
        val f = File(File(chapDir, safe(workKey)), "$index.json")
        if (!f.exists()) return null
        return try {
            chapterFromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    fun savedChapterCount(workKey: String): Int =
        File(chapDir, safe(workKey)).listFiles()?.count { it.name.endsWith(".json") } ?: 0

    fun deleteChapters(workKey: String) {
        File(chapDir, safe(workKey)).deleteRecursively()
    }

    fun saveDetail(d: WorkDetail) = writeText(File(detailDir, safe(d.work.key) + ".json"), d.toJson().toString())

    fun loadDetail(workKey: String): WorkDetail? {
        val f = File(detailDir, safe(workKey) + ".json")
        if (!f.exists()) return null
        return try {
            WorkDetail.fromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    private fun <M : MutableMap<String, String>> readMap(file: File, into: M): M {
        if (!file.exists()) return into
        try {
            val o = JSONObject(file.readText())
            for (k in o.keys()) into[k] = o.optString(k)
        } catch (_: Exception) {
        }
        return into
    }

    private fun writeMap(file: File, map: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        writeText(file, o.toString())
    }

    @Synchronized
    fun writeText(file: File, text: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        fun hash(s: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            return d.take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
