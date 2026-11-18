@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bestie.lectoraao3

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun WorkScreen(nav: NavHostController, site: Site, id: String) {
    val ctx = LocalContext.current
    var detail by remember { mutableStateOf(Repo.cachedDetail(site, id)) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val tr = remember { mutableStateMapOf<String, String>() }
    var showTr by remember { mutableStateOf(true) }
    var trProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trError by remember { mutableStateOf<String?>(null) }
    var trJob by remember { mutableStateOf<Job?>(null) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lib by Library.entries.collectAsState()
    val downloads by Downloads.status.collectAsState()

    fun segments(d: WorkDetail): List<Segment> {
        val out = mutableListOf(Segment("d_t", d.work.title, "title"))
        if (d.work.summaryHtml.isNotBlank()) out += Segment("d_s", d.work.summaryHtml, "text")
        d.notes.forEachIndexed { i, p -> if (!p.hr) out += Segment("d_n$i", p.html, "text") }
        d.work.tags.forEachIndexed { i, t -> out += Segment("d_g$i", t, "title") }
        if (d.chapters.size > 1) d.chapters.take(150).forEachIndexed { i, c -> out += Segment("d_c$i", c.title, "title") }
        return out
    }

    fun translate(d: WorkDetail, cacheOnly: Boolean) {
        trJob?.cancel()
        trError = null
        trJob = scope.launch {
            try {
                Tr.run(d.work.toMeta(), d.work.key, d.work.key, segments(d), cacheOnly) { upd, done, total ->
                    tr.putAll(upd)
                    trProgress = if (total > 0 && done < total) done to total else null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trError = friendly(e)
            } finally {
                trProgress = null
            }
        }
    }

    LaunchedEffect(reload) {
        error = null
        try {
            val d = Repo.detail(site, id, force = reload > 0)
            detail = d
            val auto = App.prefs.autoWorks && Tr.ready() && !d.work.isSpanish
            translate(d, cacheOnly = !auto)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        }
    }

    fun t(key: String, orig: String) = if (showTr) tr[key] ?: orig else orig

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(site.label) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    val d = detail
                    if (d != null) {
                        IconButton(onClick = {
                            if (tr.isNotEmpty() && trProgress == null) {
                                showTr = !showTr
                            } else if (!Tr.ready()) {
                                trError = "Configura tu API key en Ajustes para traducir 💗"
                            } else {
                                showTr = true
                                translate(d, false)
                            }
                        }) {
                            Icon(
                                Icons.Filled.Translate, "Traducir",
                                tint = if (showTr && tr.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Traducir de nuevo") }, onClick = {
                                    menu = false
                                    App.storage.deleteCache(d.work.key)
                                    tr.clear()
                                    showTr = true
                                    translate(d, false)
                                })
                                DropdownMenuItem(text = { Text("Glosario de la obra 📚") }, onClick = {
                                    menu = false
                                    nav.navigate("glossary/${Uri.encode(d.work.key)}/${Uri.encode(d.work.title)}")
                                })
                                DropdownMenuItem(text = { Text("Abrir en el navegador") }, onClick = {
                                    menu = false
                                    openUrl(ctx, d.work.webUrl)
                                })
                                DropdownMenuItem(text = { Text("Actualizar ficha") }, onClick = {
                                    menu = false
                                    reload++
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        val d = detail
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                d == null && error != null -> ErrorView(error!!) { reload++ }
                d == null -> LoadingView("Abriendo la ficha… 📖")
                else -> {
                    val w = d.work
                    val entry = lib[w.key]
                    val dlStatus = downloads[w.key]
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
                        item {
                            trProgress?.let { (done, total) ->
                                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                                Text("Traduciendo la ficha… $done/$total ✨", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(8.dp))
                            }
                            trError?.let { Banner(it, "Ok") { trError = null } }
                            Text(html(t("d_t", w.title)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("por ${w.author}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (w.fandoms.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(w.fandoms.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RatingPill(w.rating)
                                Pill(if (w.complete) "Completo ✓" else "En progreso")
                                if (w.language.isNotBlank()) Pill(w.language)
                                w.categories.forEach { Pill(it) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(statsLine(w), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(14.dp))

                            val reading = entry != null && entry.lastRead > 0
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { nav.navigate(readerRoute(site, id, if (reading) entry!!.chapterIndex else 0)) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (reading) "Continuar · cap. ${entry!!.chapterIndex + 1}" else "Empezar a leer ✨") }
                                FilledTonalIconButton(onClick = { Library.toggleSaved(w) }) {
                                    Icon(if (entry?.saved == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Guardar")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (dlStatus != null) {
                                Banner(dlStatus, if (Downloads.isRunning(w.key)) "Cancelar" else "Ok") { Downloads.cancel(w.key) }
                            } else {
                                val downloaded = entry?.downloaded ?: 0
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { Downloads.start(d, false) }, modifier = Modifier.weight(1f)) {
                                        Icon(if (downloaded >= d.chapters.size) Icons.Filled.Check else Icons.Filled.Download, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (downloaded >= d.chapters.size) "Descargado" else "Descargar", maxLines = 1)
                                    }
                                    OutlinedButton(onClick = { Downloads.start(d, true) }, modifier = Modifier.weight(1f)) {
                                        Text("Descargar + traducir", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        if (w.summaryHtml.isNotBlank()) {
                            item {
                                SectionTitle("Resumen")
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Text(html(t("d_s", w.summaryHtml)), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        if (d.notes.isNotEmpty()) {
                            item {
                                SectionTitle("Notas de la autora")
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        d.notes.forEachIndexed { i, p ->
                                            if (!p.hr) Text(html(t("d_n$i", p.html)), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            TagSection("Advertencias", w.warnings.map { warningEs(it) })
                            TagSection("Relaciones", w.relationships)
                            TagSection("Personajes", w.characters)
                            TagSection("Etiquetas", w.tags.mapIndexed { i, tag -> t("d_g$i", tag) })
                        }
                        item { SectionTitle("Capítulos (${d.chapters.size})") }
                        itemsIndexed(d.chapters, key = { _, c -> "ch_${c.index}" }) { i, c ->
                            val read = entry != null && entry.lastRead > 0 && i < entry.chapterIndex
                            val current = entry != null && entry.lastRead > 0 && i == entry.chapterIndex
                            ListItem(
                                headlineContent = {
                                    Text(
                                        html(if (d.chapters.size > 1) t("d_c$i", c.title) else "Capítulo único"),
                                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = if (current) ({ Text("Leyendo ahora 💗") }) else null,
                                trailingContent = if (read) ({ Icon(Icons.Filled.Check, "Leído", tint = MaterialTheme.colorScheme.primary) }) else null,
                                modifier = Modifier.clickable {
                                    Library.setProgress(w, i, 0, d.chapters.size)
                                    nav.navigate(readerRoute(site, id, i))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSection(title: String, tags: List<String>) {
    if (tags.isEmpty()) return
    Text(
        title,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { Pill(htmlToPlain(it), bg = MaterialTheme.colorScheme.secondaryContainer, fg = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
