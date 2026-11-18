package com.bestie.lectoraao3

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var providerSpinner: Spinner
    private lateinit var providerHelp: TextView
    private lateinit var apiKey: TextInputEditText
    private lateinit var model: TextInputEditText
    private lateinit var modelLayout: TextInputLayout
    private lateinit var baseUrl: TextInputEditText
    private lateinit var baseUrlLayout: TextInputLayout

    private val keys = HashMap<String, String>()
    private val models = HashMap<String, String>()
    private var current = Prefs.GEMINI

    private val providerNames = listOf(
        "Google Gemini (tiene nivel gratis) 🌟",
        "Anthropic Claude 💜",
        "Compatible con OpenAI (OpenAI, OpenRouter, DeepSeek…)",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        providerSpinner = findViewById(R.id.providerSpinner)
        providerHelp = findViewById(R.id.providerHelp)
        apiKey = findViewById(R.id.apiKey)
        model = findViewById(R.id.model)
        modelLayout = findViewById(R.id.modelLayout)
        baseUrl = findViewById(R.id.baseUrl)
        baseUrlLayout = findViewById(R.id.baseUrlLayout)

        Prefs.PROVIDERS.forEach {
            keys[it] = prefs.apiKey(it)
            models[it] = prefs.model(it)
        }
        current = prefs.provider
        baseUrl.setText(prefs.baseUrl)

        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providerNames)
        providerSpinner.setSelection(Prefs.PROVIDERS.indexOf(current).coerceAtLeast(0))
        loadProviderFields()
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val p = Prefs.PROVIDERS[position]
                if (p == current) return
                stashProviderFields()
                current = p
                loadProviderFields()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val variantSpinner = findViewById<Spinner>(R.id.variantSpinner)
        variantSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Prefs.VARIANT_NAMES)
        variantSpinner.setSelection(Prefs.VARIANTS.indexOf(prefs.variant).coerceAtLeast(0))

        val honorifics = findViewById<MaterialSwitch>(R.id.honorifics).apply { isChecked = prefs.honorifics }
        val raya = findViewById<MaterialSwitch>(R.id.raya).apply { isChecked = prefs.raya }
        val custom = findViewById<TextInputEditText>(R.id.custom).apply { setText(prefs.custom) }
        val autoWorks = findViewById<MaterialSwitch>(R.id.autoWorks).apply { isChecked = prefs.autoWorks }
        val autoLists = findViewById<MaterialSwitch>(R.id.autoLists).apply { isChecked = prefs.autoLists }
        val translateUi = findViewById<MaterialSwitch>(R.id.translateUi).apply { isChecked = prefs.translateUi }
        val dark = findViewById<MaterialSwitch>(R.id.dark).apply { isChecked = prefs.darkMode }
        val batch = findViewById<TextInputEditText>(R.id.batch).apply { setText(prefs.batchChars.toString()) }

        val zoomLabel = findViewById<TextView>(R.id.zoomLabel)
        val zoom = findViewById<Slider>(R.id.zoom)
        val z = ((prefs.textZoom.coerceIn(70, 200) + 5) / 10) * 10
        zoom.value = z.toFloat()
        zoomLabel.text = "Tamaño de texto: $z%"
        zoom.addOnChangeListener { _, value, _ -> zoomLabel.text = "Tamaño de texto: ${value.toInt()}%" }

        findViewById<MaterialButton>(R.id.testButton).setOnClickListener { testConnection() }

        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            stashProviderFields()
            Prefs.PROVIDERS.forEach {
                prefs.setApiKey(it, keys[it].orEmpty())
                prefs.setModel(it, models[it].orEmpty())
            }
            prefs.provider = current
            prefs.baseUrl = baseUrl.text.toString()
            prefs.variant = Prefs.VARIANTS[variantSpinner.selectedItemPosition.coerceAtLeast(0)]
            prefs.honorifics = honorifics.isChecked
            prefs.raya = raya.isChecked
            prefs.custom = custom.text.toString()
            prefs.autoWorks = autoWorks.isChecked
            prefs.autoLists = autoLists.isChecked
            prefs.translateUi = translateUi.isChecked
            prefs.darkMode = dark.isChecked
            prefs.textZoom = zoom.value.toInt()
            prefs.batchChars = batch.text.toString().toIntOrNull() ?: 3500
            Toast.makeText(this, "Guardado 💗", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun stashProviderFields() {
        keys[current] = apiKey.text.toString().trim()
        models[current] = model.text.toString().trim()
    }

    private fun loadProviderFields() {
        apiKey.setText(keys[current].orEmpty())
        model.setText(models[current].orEmpty().ifBlank { Prefs.defaultModel(current) })
        baseUrlLayout.isVisible = current == Prefs.OPENAI
        providerHelp.text = when (current) {
            Prefs.GEMINI -> "Consigue tu key gratis en https://aistudio.google.com/apikey · Modelo sugerido: gemini-flash-latest (rápido) o gemini-pro-latest (más fino)."
            Prefs.ANTHROPIC -> "Crea tu key en https://console.anthropic.com · Modelos: claude-sonnet-5-5 (la mejor prosa) o claude-haiku-5-5 (más barato)."
            else -> "Funciona con cualquier API estilo OpenAI: https://api.openai.com/v1, https://openrouter.ai/api/v1, https://api.deepseek.com, o un modelo local. Escribe el nombre exacto del modelo."
        }
        modelLayout.helperText = if (current == Prefs.OPENAI) "Obligatorio" else "Puedes dejar el sugerido"
    }

    private fun testConnection() {
        stashProviderFields()
        val cfg = TranslationConfig(
            provider = current,
            apiKey = keys[current].orEmpty(),
            model = models[current].orEmpty().ifBlank { Prefs.defaultModel(current) },
            baseUrl = baseUrl.text.toString().trim(),
            variant = prefs.variant,
            honorifics = true,
            raya = true,
            custom = "",
            batchChars = 3500,
        )
        if (!cfg.isReady) {
            Toast.makeText(this, "Completa la API key (y el modelo/URL) primero", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Probando… ✨", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val sample = Segment("t0", "“You came back,” she whispered, and <em>god</em>, she’d missed her.", "text")
            val msg = try {
                val r = Translator(cfg).translate(WorkMeta(), listOf(sample), emptyMap(), emptyList())
                "¡Funciona! 💗\n\n" + htmlToPlain(r.translations.first())
            } catch (e: Exception) {
                "No funcionó 🥺\n\n${e.message ?: e.javaClass.simpleName}"
            }
            MaterialAlertDialogBuilder(this@SettingsActivity)
                .setTitle("Prueba de conexión")
                .setMessage(msg)
                .setPositiveButton("Ok", null)
                .show()
        }
    }
}
