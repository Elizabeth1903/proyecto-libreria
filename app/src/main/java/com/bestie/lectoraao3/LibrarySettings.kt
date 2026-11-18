@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bestie.lectoraao3

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Biblioteca

@Composable
fun LibraryScreen(nav: NavHostController) {
    val lib by Library.entries.collectAsState()
    val downloads by Downloads.status.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Leyendo", "Guardados", "Descargados")
    val list = when (tab) {
        0 -> lib.values.filter { it.lastRead > 0 }.sortedByDescending { it.lastRead }
        1 -> lib.values.filter { it.saved }.sortedByDescending { it.lastRead }
        else -> lib.values.filter { it.downloaded > 0 || downloads.containsKey(it.work.key) }.sortedByDescending { it.lastRead }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Mi biblioteca 📚", fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            if (list.isEmpty()) {
                EmptyView(
                    when (tab) {
                        0 -> "📖"
                        1 -> "💗"
                        else -> "📥"
                    },
                    when (tab) {
                        0 -> "Aquí aparecen los fics que vas leyendo, con tu progreso"
                        1 -> "Toca el corazón en un fic para guardarlo aquí"
                        else -> "Descarga fics desde su ficha para leerlos sin internet"
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.work.key }) { e -> LibCard(nav, e, downloads[e.work.key]) }
                }
            }
        }
    }
}

@Composable
private fun LibCard(nav: NavHostController, e: LibEntry, status: String?) {
    var menu by remember { mutableStateOf(false) }
    val w = e.work
    Card(
        onClick = {
            if (e.lastRead > 0) nav.navigate(readerRoute(w.site, w.id, e.chapterIndex)) else nav.navigate(workRoute(w))
        },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(w.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${w.author} · ${w.site.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (w.fandoms.isNotEmpty()) {
                    Text(w.fandoms.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                if (e.totalChapters > 0 && e.lastRead > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (e.chapterIndex + 1f) / e.totalChapters }, modifier = Modifier.fillMaxWidth())
                    Text("Cap. ${e.chapterIndex + 1} de ${e.totalChapters}", style = MaterialTheme.typography.labelSmall)
                }
                if (status != null) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                else if (e.downloaded > 0) Text("📥 ${e.downloaded} caps sin internet", style = MaterialTheme.typography.labelSmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Ver ficha") }, onClick = {
                        menu = false
                        nav.navigate(workRoute(w))
                    })
                    DropdownMenuItem(text = { Text(if (e.saved) "Quitar de guardados" else "Guardar 💗") }, onClick = {
                        menu = false
                        Library.toggleSaved(w)
                    })
                    if (e.lastRead > 0) DropdownMenuItem(text = { Text("Quitar del historial") }, onClick = {
                        menu = false
                        Library.removeFromHistory(w)
                    })
                    DropdownMenuItem(text = { Text("Borrar de la biblioteca") }, onClick = {
                        menu = false
                        Library.remove(w)
                    })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Ajustes

private val PROVIDER_NAMES = listOf(
    Prefs.GEMINI to "Google Gemini (tiene nivel gratis) 🌟",
    Prefs.ANTHROPIC to "Anthropic Claude 💜",
    Prefs.OPENAI to "Compatible con OpenAI (OpenRouter, DeepSeek…)",
)

@Composable
fun SettingsScreen(nav: NavHostController) {
    val p = App.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(p.provider) }
    var key by remember(provider) { mutableStateOf(p.apiKey(provider)) }
    var model by remember(provider) { mutableStateOf(p.model(provider)) }
    var baseUrl by remember { mutableStateOf(p.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var variant by remember { mutableStateOf(p.variant) }
    var honorifics by remember { mutableStateOf(p.honorifics) }
    var raya by remember { mutableStateOf(p.raya) }
    var custom by remember { mutableStateOf(p.custom) }
    var batch by remember { mutableStateOf(p.batchChars.toString()) }
    var autoWorks by remember { mutableStateOf(p.autoWorks) }
    var autoLists by remember { mutableStateOf(p.autoLists) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes ⚙️", fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            SectionTitle("Traducción con IA ✨")
            DropChip("Proveedor", PROVIDER_NAMES, provider) {
                provider = it
                p.provider = it
            }
            Text(
                when (provider) {
                    Prefs.GEMINI -> "Consigue tu key gratis en aistudio.google.com/apikey"
                    Prefs.ANTHROPIC -> "Crea tu key en console.anthropic.com. claude-sonnet-5-5 tiene la prosa más bonita; claude-haiku-5-5 es más barato."
                    else -> "Cualquier API estilo OpenAI: api.openai.com/v1, openrouter.ai/api/v1, api.deepseek.com…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (provider == Prefs.GEMINI || provider == Prefs.ANTHROPIC) {
                TextButton(onClick = {
                    openUrl(ctx, if (provider == Prefs.GEMINI) "https://aistudio.google.com/apikey" else "https://console.anthropic.com/settings/keys")
                }) { Text("Abrir página para conseguir la key →") }
            }
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    p.setApiKey(provider, it)
                },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostrar")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    p.setModel(provider, it)
                },
                label = { Text("Modelo") },
                supportingText = { Text(if (provider == Prefs.OPENAI) "Obligatorio" else "Puedes dejar el sugerido") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider == Prefs.OPENAI) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        p.baseUrl = it
                    },
                    label = { Text("URL base") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        testResult = try {
                            val sample = Segment("t0", "“You came back,” she whispered, and <em>god</em>, she’d missed her.", "text")
                            val r = Translator(p.config(), quick = true).translate(WorkMeta(), listOf(sample), emptyMap(), emptyList())
                            "¡Funciona! 💗 (${(System.currentTimeMillis() - t0) / 1000} s)\n\n" + htmlToPlain(r.translations.first())
                        } catch (e: Exception) {
                            "No funcionó 🥺 (${(System.currentTimeMillis() - t0) / 1000} s)\n\n${friendly(e)}\n\nModelo: ${p.model(provider)}"
                        }
                        testing = false
                    }
                },
            ) { Text(if (testing) "Probando…" else "Probar conexión") }

            SectionTitle("Estilo de traducción 💅")
            DropChip("Variante", Prefs.VARIANTS.zip(Prefs.VARIANT_NAMES), variant) {
                variant = it
                p.variant = it
            }
            SwitchRow("Conservar honoríficos (-san, -kun, hyung…)", honorifics) {
                honorifics = it
                p.honorifics = it
            }
            SwitchRow("Diálogos con raya (—) como en novelas", raya) {
                raya = it
                p.raya = it
            }
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it
                    p.custom = it
                },
                label = { Text("Instrucciones extra (opcional)") },
                supportingText = { Text("Ej: usa «cariño» para «darling»; deja los hechizos en inglés") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = batch,
                onValueChange = {
                    batch = it.filter { c -> c.isDigit() }
                    batch.toIntOrNull()?.let { n -> p.batchChars = n }
                },
                label = { Text("Tamaño de cada envío (caracteres)") },
                supportingText = { Text("Más grande = más contexto; más chico = más estable") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Lectura 📖")
            SwitchRow("Traducir capítulos y fichas automáticamente", autoWorks) {
                autoWorks = it
                p.autoWorks = it
            }
            SwitchRow("Traducir resúmenes en listas automáticamente", autoLists) {
                autoLists = it
                p.autoLists = it
            }

            SectionTitle("Cuenta de AO3 🔐")
            Text(
                "Inicia sesión para leer obras restringidas a usuarios registrados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { nav.navigate("login") }) { Text("Iniciar sesión") }
                OutlinedButton(onClick = {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    Toast.makeText(ctx, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                }) { Text("Cerrar sesión") }
            }

            SectionTitle("Datos 🧹")
            OutlinedButton(onClick = { confirmClear = true }) { Text("Borrar todas las traducciones guardadas") }
            Spacer(Modifier.height(40.dp))
        }
    }

    testResult?.let {
        AlertDialog(
            onDismissRequest = { testResult = null },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("Ok") } },
            title = { Text("Prueba de conexión") },
            text = { Text(it) },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(onClick = {
                    App.storage.clearAllTranslations()
                    confirmClear = false
                    Toast.makeText(ctx, "Traducciones borradas", Toast.LENGTH_SHORT).show()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            title = { Text("¿Borrar traducciones?") },
            text = { Text("Tendrás que volver a traducir los capítulos (tu biblioteca y glosarios se quedan).") },
        )
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------ Login AO3

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iniciar sesión en AO3") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
    ) { pad ->
        AndroidView(
            modifier = Modifier.padding(pad).fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {}
                        override fun onPageFinished(view: WebView, url: String) {
                            CookieManager.getInstance().flush()
                            val cookies = CookieManager.getInstance().getCookie(Ao3.BASE).orEmpty()
                            if (!url.contains("/users/login") && url.startsWith(Ao3.BASE) && cookies.contains("user_credentials")) {
                                Toast.makeText(ctx, "¡Sesión iniciada! 💗", Toast.LENGTH_SHORT).show()
                                nav.popBackStack()
                            }
                        }
                    }
                    loadUrl("${Ao3.BASE}/users/login")
                }
            },
        )
    }
}

// ------------------------------------------------------------------ Glosario

@Composable
fun GlossaryScreen(nav: NavHostController, key: String, title: String) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(App.storage.loadGlossary(key).entries.joinToString("\n") { "${it.key} = ${it.value}" }) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Glosario 📚")
                        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    TextButton(onClick = {
                        val map = LinkedHashMap<String, String>()
                        text.lines().forEach { line ->
                            val i = line.indexOf('=')
                            if (i > 0) {
                                val k = line.substring(0, i).trim()
                                val v = line.substring(i + 1).trim()
                                if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
                            }
                        }
                        App.storage.saveGlossary(key, map)
                        Toast.makeText(ctx, "Glosario guardado 💗", Toast.LENGTH_SHORT).show()
                        nav.popBackStack()
                    }) { Text("Guardar") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().padding(16.dp)) {
            Text(
                "Una entrada por línea: original = traducción. La IA respeta este glosario en cada capítulo y va agregando términos nuevos sola ✨",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("Sunshine = Solcito") },
            )
        }
    }
}
