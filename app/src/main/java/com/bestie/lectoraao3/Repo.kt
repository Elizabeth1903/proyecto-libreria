package com.bestie.lectoraao3

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LectoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }
}

object App {
    lateinit var ctx: Context
    lateinit var prefs: Prefs
    lateinit var storage: Storage
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(c: Context) {
        ctx = c.applicationContext
        prefs = Prefs(ctx)
        storage = Storage(ctx)
        Library.init()
    }
}

fun friendly(e: Throwable): String = when (e) {
    is ApiException -> e.message ?: "Error"
    is UnknownHostException -> "No pude encontrar el servidor: revisa tu internet 📶 (${e.message})"
    is SocketTimeoutException -> "La IA tardó demasiado en responder. Intenta de nuevo o prueba otro modelo."
    is java.net.ConnectException -> "No pude conectar con el servidor (${e.message})"
    is javax.net.ssl.SSLException -> "Problema de conexión segura (SSL): ${e.message}"
    is java.io.IOException -> "Error de conexión: ${e.javaClass.simpleName} ${e.message.orEmpty()}"
    else -> "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
}

/** Opciones de filtros por sitio: (valor, etiqueta). */
object Options {
    fun sorts(site: Site, search: Boolean) = when (site) {
        Site.AO3 -> listOfNotNull(
            if (search) "_score" to "Relevancia" else null,
            "kudos_count" to "Más kudos",
            "hits" to "Más visitas",
            "bookmarks_count" to "Más guardados",
            "revised_at" to "Recién actualizados",
            "word_count" to "Más largos",
        )
        Site.FFN -> listOf(
            "4" to "Más favoritos",
            "5" to "Más seguidos",
            "3" to "Más reseñas",
            "1" to "Recién actualizados",
            "2" to "Recién publicados",
        )
    }

    fun ratings(site: Site) = when (site) {
        Site.AO3 -> listOf("" to "Todas", "10" to "General", "11" to "Adolescentes", "12" to "Maduro", "13" to "Explícito")
        Site.FFN -> listOf("10" to "Todas", "103" to "K → T", "1" to "K", "2" to "K+", "3" to "T", "4" to "M")
    }

    val languages = listOf("" to "Todos los idiomas", "en" to "Inglés", "es" to "Español")

    fun defaults(site: Site, search: Boolean) = Filters(sort = sorts(site, search).first().first, rating = ratings(site).first().first)
}

object Repo {
    private val fandomCache = HashMap<String, List<Fandom>>()
    private val details = HashMap<String, WorkDetail>()

    suspend fun fandoms(site: Site, category: String): List<Fandom> {
        val k = "${site.id}:$category"
        fandomCache[k]?.let { return it }
        val list = when (site) {
            Site.AO3 -> Net.get(Ao3.fandomsUrl(category)).let { withContext(Dispatchers.Default) { Ao3.parseFandoms(it) } }
            Site.FFN -> Net.get(Ffn.fandomsUrl(category), viaWeb = true).let { withContext(Dispatchers.Default) { Ffn.parseFandoms(it) } }
        }
        if (list.isEmpty()) throw ApiException("No encontré fandoms aquí 🥺")
        fandomCache[k] = list
        return list
    }

    suspend fun works(src: ListSource, page: Int): Pair<List<WorkSummary>, Boolean> = when (src.site) {
        Site.AO3 -> Net.get(Ao3.searchUrl(src, page)).let { withContext(Dispatchers.Default) { Ao3.parseList(it) } }
        Site.FFN -> Net.get(Ffn.listUrl(src, page), viaWeb = true).let { withContext(Dispatchers.Default) { Ffn.parseList(it) } }
    }

    suspend fun detail(site: Site, id: String, force: Boolean = false): WorkDetail {
        val key = "${site.id}_$id"
        if (!force) details[key]?.let { return it }
        return try {
            val d = when (site) {
                Site.AO3 -> Net.get(Ao3.workUrl(id)).let { withContext(Dispatchers.Default) { Ao3.parseWork(id, it) } }
                Site.FFN -> Net.get(Ffn.storyUrl(id), viaWeb = true).let { withContext(Dispatchers.Default) { Ffn.parseStory(id, it) } }
            }
            details[key] = d
            withContext(Dispatchers.IO) { App.storage.saveDetail(d) }
            d
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión: usamos la ficha guardada si existe
            withContext(Dispatchers.IO) { App.storage.loadDetail(key) }?.also { details[key] = it } ?: throw e
        }
    }

    fun cachedDetail(site: Site, id: String) = details["${site.id}_$id"]

    suspend fun chapter(d: WorkDetail, index: Int): ChapterContent {
        val key = d.work.key
        withContext(Dispatchers.IO) { App.storage.loadChapter(key, index) }?.let { return it }
        val ref = d.chapters.getOrNull(index) ?: throw ApiException("Ese capítulo no existe")
        val content = if (index == 0 && d.firstChapter != null) d.firstChapter else fetchChapter(d.work, ref)
        withContext(Dispatchers.IO) { App.storage.saveChapter(key, index, content) }
        return content
    }

    private suspend fun fetchChapter(w: WorkSummary, ref: ChapterRef): ChapterContent = when (w.site) {
        Site.AO3 -> Net.get(Ao3.chapterUrl(w.id, ref.id)).let { withContext(Dispatchers.Default) { Ao3.parseChapterPage(it) } }
        Site.FFN -> Net.get(Ffn.storyUrl(w.id, ref.id), viaWeb = true).let { withContext(Dispatchers.Default) { Ffn.parseChapterPage(it) } }
    }

    /** Descarga todos los capítulos para leer sin internet. */
    suspend fun downloadAll(d: WorkDetail, progress: (Int, Int) -> Unit): Int {
        val key = d.work.key
        val total = d.chapters.size
        if (d.work.site == Site.AO3 && total > 1) {
            progress(0, total)
            val html = Net.get(Ao3.fullWorkUrl(d.work.id))
            val chapters = withContext(Dispatchers.Default) { Ao3.parseFullWork(html) }
            withContext(Dispatchers.IO) { chapters.forEachIndexed { i, c -> App.storage.saveChapter(key, i, c) } }
            progress(chapters.size, total)
            return chapters.size
        }
        for (i in 0 until total) {
            progress(i, total)
            val existing = withContext(Dispatchers.IO) { App.storage.loadChapter(key, i) }
            if (existing == null) {
                chapter(d, i)
                if (d.work.site == Site.FFN) delay(1200)
            }
        }
        progress(total, total)
        return total
    }
}

/** Descargas en segundo plano (siguen aunque cambies de pantalla). */
object Downloads {
    val status = MutableStateFlow<Map<String, String>>(emptyMap())
    private val jobs = HashMap<String, Job>()

    private fun set(key: String, text: String?) {
        val m = HashMap(status.value)
        if (text == null) m.remove(key) else m[key] = text
        status.value = m
    }

    fun isRunning(key: String) = jobs[key]?.isActive == true

    fun cancel(key: String) {
        jobs[key]?.cancel()
        set(key, null)
    }

    fun start(d: WorkDetail, translate: Boolean) {
        val key = d.work.key
        if (isRunning(key)) return
        jobs[key] = App.scope.launch {
            try {
                set(key, "Descargando…")
                val count = Repo.downloadAll(d) { i, n -> set(key, "Descargando $i/$n 📥") }
                Library.setDownloaded(d.work, count, d.chapters.size)
                if (translate) {
                    if (!App.prefs.config().isReady) {
                        set(key, "Descargado ✓ (configura tu API key para traducir)")
                        return@launch
                    }
                    for (i in 0 until count) {
                        val c = withContext(Dispatchers.IO) { App.storage.loadChapter(key, i) } ?: continue
                        val items = Reading.items(i, c, if (i == 0) d.notes else emptyList())
                        Tr.run(d.work.toMeta(), key, key, Reading.segments(items)) { _, done, total ->
                            set(key, "Traduciendo cap. ${i + 1}/$count" + if (total > 0) " · $done/$total ✨" else "")
                        }
                    }
                }
                set(key, null)
            } catch (e: CancellationException) {
                set(key, null)
                throw e
            } catch (e: Exception) {
                set(key, "Error: ${friendly(e)}")
            }
        }
    }
}
