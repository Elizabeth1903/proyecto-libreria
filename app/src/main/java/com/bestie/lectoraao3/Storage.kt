package com.bestie.lectoraao3

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Guarda traducciones y glosarios en la memoria interna, para leer offline y no pagar dos veces. */
class Storage(context: Context) {
    private val cacheDir = File(context.filesDir, "traducciones").apply { mkdirs() }
    private val glossDir = File(context.filesDir, "glosarios").apply { mkdirs() }

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_\\-]"), "_")

    fun loadCache(key: String): MutableMap<String, String> = readMap(File(cacheDir, safe(key) + ".json"), HashMap())

    fun saveCache(key: String, map: Map<String, String>) = writeMap(File(cacheDir, safe(key) + ".json"), map)

    fun deleteCache(key: String) {
        File(cacheDir, safe(key) + ".json").delete()
    }

    fun loadGlossary(key: String): LinkedHashMap<String, String> =
        readMap(File(glossDir, safe(key) + ".json"), LinkedHashMap())

    fun saveGlossary(key: String, map: Map<String, String>) = writeMap(File(glossDir, safe(key) + ".json"), map)

    private fun <M : MutableMap<String, String>> readMap(file: File, into: M): M {
        if (!file.exists()) return into
        try {
            val o = JSONObject(file.readText())
            for (k in o.keys()) into[k] = o.optString(k)
        } catch (_: Exception) {
        }
        return into
    }

    @Synchronized
    private fun writeMap(file: File, map: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(o.toString())
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
