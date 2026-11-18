package com.bestie.lectoraao3

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/** Editor simple del glosario de una obra: "original = traducción", una por línea. */
class GlossaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glossary)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        val key = intent.getStringExtra(EXTRA_KEY) ?: run { finish(); return }
        val storage = Storage(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Glosario 📚"
        toolbar.subtitle = intent.getStringExtra(EXTRA_TITLE)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        val text = findViewById<EditText>(R.id.glossaryText)
        text.setText(storage.loadGlossary(key).entries.joinToString("\n") { "${it.key} = ${it.value}" })

        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener { text.setText("") }
        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            val map = LinkedHashMap<String, String>()
            text.text.toString().lines().forEach { line ->
                val i = line.indexOf('=')
                if (i > 0) {
                    val k = line.substring(0, i).trim()
                    val v = line.substring(i + 1).trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
                }
            }
            storage.saveGlossary(key, map)
            Toast.makeText(this, "Glosario guardado 💗", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
