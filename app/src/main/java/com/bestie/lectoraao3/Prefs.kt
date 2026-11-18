package com.bestie.lectoraao3

import android.content.Context
import org.json.JSONObject

data class TranslationConfig(
    val provider: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val variant: String,
    val honorifics: Boolean,
    val raya: Boolean,
    val custom: String,
    val batchChars: Int,
) {
    /** Si cambia el estilo, las traducciones guardadas dejan de valer. */
    private val styleKey = "$variant|$honorifics|$raya|${custom.trim()}"

    fun keyFor(html: String) = Storage.hash(styleKey + "\u0000" + html)

    val isReady: Boolean
        get() = when (provider) {
            Prefs.OPENAI -> baseUrl.isNotBlank() && model.isNotBlank()
            else -> apiKey.isNotBlank()
        }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lectora", Context.MODE_PRIVATE)

    companion object {
        const val GEMINI = "gemini"
        const val ANTHROPIC = "anthropic"
        const val OPENAI = "openai"
        val PROVIDERS = listOf(GEMINI, ANTHROPIC, OPENAI)

        fun defaultModel(provider: String) = when (provider) {
            GEMINI -> "gemini-flash-latest"
            ANTHROPIC -> "claude-sonnet-5-5"
            else -> ""
        }

        val VARIANTS = listOf("latam", "es", "rioplatense")
        val VARIANT_NAMES = listOf(
            "Español latinoamericano 🌎",
            "Español de España 🇪🇸",
            "Español rioplatense (voseo) 🧉",
        )
        const val HOME = "https://archiveofourown.org/"
        const val HOME_FFN = "https://www.fanfiction.net/"
    }

    var provider: String
        get() = sp.getString("provider", GEMINI) ?: GEMINI
        set(v) = sp.edit().putString("provider", v).apply()

    fun apiKey(p: String) = sp.getString("key_$p", "") ?: ""
    fun setApiKey(p: String, v: String) = sp.edit().putString("key_$p", v.trim()).apply()

    fun model(p: String) = (sp.getString("model_$p", "") ?: "").ifBlank { defaultModel(p) }
    fun setModel(p: String, v: String) = sp.edit().putString("model_$p", v.trim()).apply()

    var baseUrl: String
        get() = sp.getString("baseUrl", "https://api.openai.com/v1") ?: ""
        set(v) = sp.edit().putString("baseUrl", v.trim()).apply()

    var variant: String
        get() = sp.getString("variant", "latam") ?: "latam"
        set(v) = sp.edit().putString("variant", v).apply()

    var honorifics: Boolean
        get() = sp.getBoolean("honorifics", true)
        set(v) = sp.edit().putBoolean("honorifics", v).apply()

    var raya: Boolean
        get() = sp.getBoolean("raya", true)
        set(v) = sp.edit().putBoolean("raya", v).apply()

    var custom: String
        get() = sp.getString("custom", "") ?: ""
        set(v) = sp.edit().putString("custom", v).apply()

    var autoWorks: Boolean
        get() = sp.getBoolean("autoWorks", true)
        set(v) = sp.edit().putBoolean("autoWorks", v).apply()

    var autoLists: Boolean
        get() = sp.getBoolean("autoLists", false)
        set(v) = sp.edit().putBoolean("autoLists", v).apply()

    var translateUi: Boolean
        get() = sp.getBoolean("translateUi", true)
        set(v) = sp.edit().putBoolean("translateUi", v).apply()

    var darkMode: Boolean
        get() = sp.getBoolean("dark", false)
        set(v) = sp.edit().putBoolean("dark", v).apply()

    var textZoom: Int
        get() = sp.getInt("textZoom", 100)
        set(v) = sp.edit().putInt("textZoom", v).apply()

    var batchChars: Int
        get() = sp.getInt("batchChars", 3500)
        set(v) = sp.edit().putInt("batchChars", v.coerceIn(800, 20000)).apply()

    // ---- Lector
    var readerTheme: String
        get() = sp.getString("readerTheme", "rosa") ?: "rosa"
        set(v) = sp.edit().putString("readerTheme", v).apply()

    var readerFont: String
        get() = sp.getString("readerFont", "serif") ?: "serif"
        set(v) = sp.edit().putString("readerFont", v).apply()

    var readerSize: Float
        get() = sp.getFloat("readerSize", 18f)
        set(v) = sp.edit().putFloat("readerSize", v).apply()

    var readerLine: Float
        get() = sp.getFloat("readerLine", 1.6f)
        set(v) = sp.edit().putFloat("readerLine", v).apply()

    var readerJustify: Boolean
        get() = sp.getBoolean("readerJustify", false)
        set(v) = sp.edit().putBoolean("readerJustify", v).apply()

    var homeSite: String
        get() = sp.getString("homeSite", "ao3") ?: "ao3"
        set(v) = sp.edit().putString("homeSite", v).apply()

    var lastUrl: String
        get() = sp.getString("lastUrl", HOME) ?: HOME
        set(v) = sp.edit().putString("lastUrl", v).apply()

    fun config(): TranslationConfig {
        val p = provider
        return TranslationConfig(
            provider = p,
            apiKey = apiKey(p),
            model = model(p),
            baseUrl = baseUrl,
            variant = variant,
            honorifics = honorifics,
            raya = raya,
            custom = custom,
            batchChars = batchChars,
        )
    }

    fun jsOptions(): String = JSONObject()
        .put("dark", darkMode)
        .put("ui", translateUi)
        .toString()
}
