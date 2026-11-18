package com.bestie.lectoraao3

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Comparte las cookies (sesión de AO3, verificación de Cloudflare) entre el WebView y OkHttp. */
object WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = CookieManager.getInstance()
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val s = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return s.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }
}

object Net {
    val userAgent: String by lazy {
        try {
            WebSettings.getDefaultUserAgent(App.ctx)
        } catch (_: Exception) {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun isChallenge(html: String): Boolean =
        html.contains("_cf_chl_opt") ||
            Regex("<title>\\s*(Just a moment|Attention Required|Un momento)", RegexOption.IGNORE_CASE).containsMatchIn(html)

    /** Descarga una página. Si Cloudflare la protege, usa el navegador invisible. */
    suspend fun get(url: String, viaWeb: Boolean = false): String {
        if (!viaWeb) {
            val (code, body) = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
            }
            when {
                code == 429 -> throw ApiException("El sitio pide esperar un poquito por muchas visitas seguidas. Intenta en un minuto 🙏")
                code in 200..299 && !isChallenge(body) -> return body
                code == 404 -> throw ApiException("No encontré esa página (404). Puede que la hayan borrado 🥺")
                code == 403 || code == 503 || isChallenge(body) -> Unit
                else -> throw ApiException("Error $code al cargar la página")
            }
        }
        return WebFetcher.fetch(url)
    }
}

/**
 * Carga páginas en un WebView invisible (pasa la verificación de Cloudflare como un navegador normal).
 * Si la verificación pide tocar algo, [challenge] expone el WebView para que la app lo muestre.
 */
object WebFetcher {
    val challenge = MutableStateFlow<WebView?>(null)
    private val mutex = Mutex()
    private var web: WebView? = null
    @Volatile private var loaded = false

    suspend fun fetch(url: String): String = mutex.withLock {
        withContext(Dispatchers.Main) { load(url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(): WebView = web ?: WebView(App.ctx).also { w ->
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.loadsImagesAutomatically = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loaded = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                loaded = true
                CookieManager.getInstance().flush()
            }
        }
        web = w
    }

    private suspend fun load(url: String): String {
        val w = view()
        loaded = false
        w.loadUrl(url)
        val start = System.currentTimeMillis()
        try {
            while (true) {
                delay(700)
                val elapsed = System.currentTimeMillis() - start
                val html = if (loaded) html(w) else null
                if (html != null && html.length > 300) {
                    if (!Net.isChallenge(html)) {
                        challenge.value = null
                        return html
                    }
                    if (elapsed > 6000 && challenge.value == null) challenge.value = w
                }
                if (elapsed > 150_000) throw ApiException("No pude pasar la verificación del sitio. Intenta de nuevo 🥺")
            }
        } finally {
            challenge.value = null
        }
    }

    private suspend fun html(w: WebView): String? = suspendCancellableCoroutine { c ->
        w.evaluateJavascript("document.readyState=='complete' ? document.documentElement.outerHTML : null") { raw ->
            val s = try {
                if (raw == null || raw == "null") null else JSONTokener(raw).nextValue() as? String
            } catch (_: Exception) {
                null
            }
            if (c.isActive) c.resume(s)
        }
    }
}
