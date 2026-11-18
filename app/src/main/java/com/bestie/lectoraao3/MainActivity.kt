package com.bestie.lectoraao3

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var pageProgress: LinearProgressIndicator
    private lateinit var card: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusButton: MaterialButton
    private lateinit var trProgress: LinearProgressIndicator
    private lateinit var prefs: Prefs
    private lateinit var storage: Storage

    private var page: PageInfo? = null
    private var job: Job? = null
    private var showingTranslation = true
    private var handledUrl: String? = null
    private val translated = HashMap<String, String>()

    private val script: String by lazy { assets.open("ao3t.js").bufferedReader().use { it.readText() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        storage = Storage(this)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        web = findViewById(R.id.web)
        pageProgress = findViewById(R.id.pageProgress)
        card = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        statusButton = findViewById(R.id.statusButton)
        trProgress = findViewById(R.id.trProgress)
        findViewById<ImageButton>(R.id.statusClose).setOnClickListener { card.isVisible = false }

        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        val restored = savedInstanceState?.let { web.restoreState(it) }
        if (restored == null) web.loadUrl(urlFromIntent(intent) ?: prefs.lastUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        urlFromIntent(intent)?.let { web.loadUrl(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.settings.textZoom = prefs.textZoom
        web.evaluateJavascript("window.AO3T && AO3T.setup(${prefs.jsOptions()})", null)
    }

    private fun urlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        intent.data?.let { return it.toString() }
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return Regex("https?://\\S*(archiveofourown\\.org|ao3\\.org)\\S*").find(text)?.value
        }
        return null
    }

    private fun isAo3(host: String) =
        host == "archiveofourown.org" || host.endsWith(".archiveofourown.org") || host == "ao3.org" || host.endsWith(".ao3.org")

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            textZoom = prefs.textZoom
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        web.setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host
                if ((uri.scheme == "https" || uri.scheme == "http") && host != null && isAo3(host)) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                job?.cancel()
                job = null
                page = null
                handledUrl = null
                translated.clear()
                card.isVisible = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
                prefs.lastUrl = url
                inject(url)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                pageProgress.isVisible = newProgress < 100
                pageProgress.setProgressCompat(newProgress, true)
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                supportActionBar?.subtitle = title
                    ?.removeSuffix(" [Archive of Our Own]")
                    ?.removeSuffix(" | Archive of Our Own")
            }
        }
    }

    // ------------------------------------------------------------ page lifecycle

    private fun inject(url: String) {
        if (url == handledUrl) return
        handledUrl = url
        val code = script + "\n;(function(){var r=AO3T.collect();AO3T.setup(" + prefs.jsOptions() + ");return r;})();"
        web.evaluateJavascript(code) { raw -> onCollected(raw) }
    }

    private fun decodeJs(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            JSONTokener(raw).nextValue() as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun onCollected(raw: String?) {
        val json = decodeJs(raw) ?: return
        val info = try {
            PageInfo.parse(json)
        } catch (_: Exception) {
            return
        }
        page = info
        showingTranslation = true
        if (info.segments.isEmpty()) return
        if (info.alreadySpanish) {
            showMessage("Esta obra ya está en español 💕")
            return
        }

        lifecycleScope.launch {
            val cfg = prefs.config()
            val cache = withContext(Dispatchers.IO) { storage.loadCache(info.cacheKey) }
            if (page !== info) return@launch
            val found = JSONObject()
            for (seg in info.segments) {
                cache[cfg.keyFor(seg.html)]?.let {
                    found.put(seg.id, it)
                    translated[seg.id] = it
                }
            }
            if (found.length() > 0) applyToPage(found)

            val pending = info.segments.filter { it.id !in translated }
            val auto = if (info.isWork) prefs.autoWorks else prefs.autoLists
            when {
                pending.isEmpty() -> showMessage("✨ Traducido · guardado en tu biblioteca", "Ver original") { toggle() }
                !cfg.isReady && info.isWork -> showMessage("Agrega tu API key en Ajustes para traducir 💗", "Ajustes") { openSettings() }
                auto && cfg.isReady -> startTranslation(info, pending)
                info.isWork -> showMessage("Capítulo en ${info.meta.language.ifBlank { "otro idioma" }}", "Traducir") {
                    startTranslation(info, pending)
                }
            }
        }
    }

    private fun applyToPage(obj: JSONObject) {
        web.evaluateJavascript("AO3T.apply($obj)", null)
    }

    // ------------------------------------------------------------ translation

    private fun startTranslation(info: PageInfo, pending: List<Segment>) {
        job?.cancel()
        val cfg = prefs.config()
        if (!cfg.isReady) {
            showMessage("Primero configura tu proveedor y API key 💗", "Ajustes") { openSettings() }
            return
        }
        if (pending.isEmpty()) {
            showMessage("✨ Ya está todo traducido", "Ver original") { toggle() }
            return
        }
        if (!showingTranslation) toggle()

        job = lifecycleScope.launch {
            val translator = Translator(cfg)
            val glossary = withContext(Dispatchers.IO) { storage.loadGlossary(info.glossaryKey) }
            val cache = withContext(Dispatchers.IO) { storage.loadCache(info.cacheKey) }
            val batches = Translator.makeBatches(pending, cfg.batchChars)
            val index = HashMap<String, Int>()
            info.segments.forEachIndexed { i, s -> index[s.id] = i }

            try {
                batches.forEachIndexed { i, batch ->
                    showProgress("Traduciendo con cariño… ${i + 1}/${batches.size} ✨", i, batches.size)
                    val context = previousContext(info, index[batch.first().id] ?: 0)
                    val res = translator.translate(info.meta, batch, glossary, context)
                    val obj = JSONObject()
                    batch.forEachIndexed { j, seg ->
                        val t = res.translations[j]
                        obj.put(seg.id, t)
                        translated[seg.id] = t
                        cache[cfg.keyFor(seg.html)] = t
                    }
                    for ((k, v) in res.glossary) if (!glossary.containsKey(k)) glossary[k] = v
                    applyToPage(obj)
                    val cacheCopy = HashMap(cache)
                    val glossCopy = LinkedHashMap(glossary)
                    withContext(Dispatchers.IO) {
                        storage.saveCache(info.cacheKey, cacheCopy)
                        storage.saveGlossary(info.glossaryKey, glossCopy)
                    }
                }
                showMessage("✨ ¡Listo! Disfruta tu lectura 💗", "Ver original") { toggle() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Ups 🥺 ${friendly(e)}", "Reintentar") {
                    startTranslation(info, info.segments.filter { it.id !in translated })
                }
            }
        }
    }

    /** Los últimos párrafos ya traducidos antes de este lote, para que la IA mantenga el hilo. */
    private fun previousContext(info: PageInfo, start: Int): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var i = start - 1
        while (i >= 0 && out.size < 3) {
            val s = info.segments[i]
            val t = translated[s.id]
            if (t != null && s.kind == "text") out.add(0, s.plain to htmlToPlain(t))
            i--
        }
        return out
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "Error de la IA"
        is UnknownHostException -> "No hay conexión a internet."
        is SocketTimeoutException -> "La IA tardó demasiado. Intenta de nuevo."
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun toggle() {
        showingTranslation = !showingTranslation
        web.evaluateJavascript("AO3T.show('${if (showingTranslation) "tr" else "orig"}')", null)
        if (job?.isActive != true) {
            if (showingTranslation) showMessage("Mostrando traducción ✨", "Ver original") { toggle() }
            else showMessage("Mostrando el original", "Ver traducción") { toggle() }
        }
    }

    // ------------------------------------------------------------ status card

    private fun showMessage(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        card.isVisible = true
        statusText.text = text
        trProgress.isVisible = false
        statusButton.isVisible = action != null
        statusButton.text = action ?: ""
        statusButton.setOnClickListener { onAction?.invoke() }
    }

    private fun showProgress(text: String, done: Int, total: Int) {
        card.isVisible = true
        statusText.text = text
        trProgress.isVisible = true
        trProgress.max = total
        trProgress.setProgressCompat(done, true)
        statusButton.isVisible = true
        statusButton.text = "Pausar"
        statusButton.setOnClickListener {
            job?.cancel()
            val info = page ?: return@setOnClickListener
            showMessage("Traducción en pausa ⏸", "Continuar") {
                startTranslation(info, info.segments.filter { it.id !in translated })
            }
        }
    }

    // ------------------------------------------------------------ menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val info = page
        when (item.itemId) {
            R.id.action_translate -> {
                if (info == null || info.segments.isEmpty()) {
                    Toast.makeText(this, "No encontré texto para traducir en esta página", Toast.LENGTH_SHORT).show()
                } else {
                    startTranslation(info, info.segments.filter { it.id !in translated })
                }
            }
            R.id.action_toggle -> if (translated.isNotEmpty()) toggle()
            R.id.action_home -> web.loadUrl(Prefs.HOME)
            R.id.action_open -> openUrlDialog()
            R.id.action_settings -> openSettings()
            R.id.action_glossary -> {
                if (info?.isWork == true) {
                    startActivity(
                        Intent(this, GlossaryActivity::class.java)
                            .putExtra(GlossaryActivity.EXTRA_KEY, info.glossaryKey)
                            .putExtra(GlossaryActivity.EXTRA_TITLE, info.meta.title)
                    )
                } else {
                    Toast.makeText(this, "Abre una obra para ver su glosario", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.action_retranslate -> if (info != null) {
                job?.cancel()
                lifecycleScope.launch {
                    val cfg = prefs.config()
                    val cache = withContext(Dispatchers.IO) { storage.loadCache(info.cacheKey) }
                    info.segments.forEach { cache.remove(cfg.keyFor(it.html)) }
                    withContext(Dispatchers.IO) { storage.saveCache(info.cacheKey, cache) }
                    translated.clear()
                    web.evaluateJavascript("AO3T.clear()", null)
                    startTranslation(info, info.segments)
                }
            }
            R.id.action_clear -> if (info != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("¿Borrar traducciones?")
                    .setMessage("Se borran las traducciones guardadas de esta obra (el glosario se queda).")
                    .setPositiveButton("Borrar") { _, _ ->
                        job?.cancel()
                        storage.deleteCache(info.cacheKey)
                        translated.clear()
                        web.evaluateJavascript("AO3T.clear()", null)
                        showMessage("Traducciones borradas 🧹", "Traducir") { startTranslation(info, info.segments) }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun openUrlDialog() {
        val input = EditText(this).apply {
            hint = "Enlace de AO3, número de obra o búsqueda"
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Abrir en AO3 ✨")
            .setView(box)
            .setPositiveButton("Abrir") { _, _ ->
                val t = input.text.toString().trim()
                val url = when {
                    t.isEmpty() -> null
                    t.startsWith("http") -> t
                    t.contains("archiveofourown.org") -> "https://$t"
                    t.all { it.isDigit() } -> "https://archiveofourown.org/works/$t"
                    else -> "https://archiveofourown.org/works/search?work_search%5Bquery%5D=" + URLEncoder.encode(t, "UTF-8")
                }
                url?.let { web.loadUrl(Uri.parse(it).toString()) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
