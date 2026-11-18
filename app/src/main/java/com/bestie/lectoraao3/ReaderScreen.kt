@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.bestie.lectoraao3

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderStyle(val theme: String, val font: String, val size: Float, val line: Float, val justify: Boolean) {
    fun save() {
        val p = App.prefs
        p.readerTheme = theme
        p.readerFont = font
        p.readerSize = size
        p.readerLine = line
        p.readerJustify = justify
    }

    val family: FontFamily
        get() = when (font) {
            "sans" -> FontFamily.SansSerif
            "mono" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Serif
        }

    companion object {
        fun load() = App.prefs.let { ReaderStyle(it.readerTheme, it.readerFont, it.readerSize, it.readerLine, it.readerJustify) }
    }
}

@Composable
fun ReaderScreen(nav: NavHostController, site: Site, id: String, startChapter: Int) {
    var style by remember { mutableStateOf(ReaderStyle.load()) }
    val colors = readerColors(style.theme)
    var detail by remember { mutableStateOf(Repo.cachedDetail(site, id)) }
    var chapter by rememberSaveable { mutableIntStateOf(startChapter) }
    var items by remember { mutableStateOf<List<ReaderItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val tr = remember { mutableStateMapOf<String, String>() }
    val flipped = remember { mutableStateMapOf<String, Boolean>() }
    var showTr by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trError by remember { mutableStateOf<String?>(null) }
    var trJob by remember { mutableStateOf<Job?>(null) }
    var paused by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun translate(d: WorkDetail, list: List<ReaderItem>, cacheOnly: Boolean) {
        trJob?.cancel()
        trError = null
        paused = false
        trJob = scope.launch {
            try {
                Tr.run(d.work.toMeta(), d.work.key, d.work.key, Reading.segments(list), cacheOnly) { upd, done, total ->
                    tr.putAll(upd)
                    progress = if (total > 0 && done < total) done to total else null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trError = friendly(e)
            } finally {
                progress = null
            }
        }
    }

    fun goTo(i: Int) {
        val d = detail ?: return
        if (i < 0 || i >= d.chapters.size) return
        Library.setProgress(d.work, i, 0, d.chapters.size)
        chapter = i
    }

    LaunchedEffect(chapter, reload) {
        items = null
        error = null
        tr.clear()
        flipped.clear()
        trJob?.cancel()
        progress = null
        try {
            val d = detail ?: Repo.detail(site, id).also { detail = it }
            val c = Repo.chapter(d, chapter)
            val list = Reading.items(chapter, c, if (chapter == 0) d.notes else emptyList())
            items = list
            val entry = Library.get(d.work.key)
            val start = if (entry != null && entry.chapterIndex == chapter) entry.itemIndex.coerceIn(0, list.size) else 0
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            listState.scrollToItem(start)
            Library.setProgress(d.work, chapter, start, d.chapters.size)
            val auto = App.prefs.autoWorks && Tr.ready() && !d.work.isSpanish
            translate(d, list, cacheOnly = !auto)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        }
    }

    // Guarda por dónde vas leyendo
    LaunchedEffect(items) {
        val d = detail ?: return@LaunchedEffect
        if (items == null) return@LaunchedEffect
        val ch = chapter
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { idx ->
            delay(800)
            Library.setProgress(d.work, ch, idx, d.chapters.size)
        }
    }

    val d = detail
    val total = d?.chapters?.size ?: 0

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bg,
                    titleContentColor = colors.fg,
                    navigationIconContentColor = colors.fg,
                    actionIconContentColor = colors.fg,
                ),
                title = {
                    Column {
                        Text(d?.work?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (total > 0) Text("Capítulo ${chapter + 1} de $total", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = {
                        val dd = detail
                        val list = items
                        when {
                            dd == null || list == null -> Unit
                            tr.isNotEmpty() && progress == null && !paused -> showTr = !showTr
                            !Tr.ready() -> trError = "Configura tu API key en Ajustes para traducir 💗"
                            else -> {
                                showTr = true
                                translate(dd, list, false)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Translate, "Traducir", tint = if (showTr && tr.isNotEmpty()) colors.accent else colors.fg)
                    }
                    IconButton(onClick = { showStyle = true }) { Icon(Icons.Filled.FormatSize, "Estilo") }
                    IconButton(onClick = { showChapters = true }) { Icon(Icons.AutoMirrored.Filled.List, "Capítulos") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            progress?.let { (done, n) ->
                LinearProgressIndicator(
                    progress = { done.toFloat() / n },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.note,
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Traduciendo con cariño… $done/$n ✨", Modifier.weight(1f), color = colors.fg, style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = {
                        trJob?.cancel()
                        paused = true
                    }) { Text("Pausar", color = colors.accent) }
                }
            }
            if (paused && progress == null) {
                Banner("Traducción en pausa ⏸", "Continuar") {
                    val dd = detail
                    val list = items
                    if (dd != null && list != null) translate(dd, list, false)
                }
            }
            trError?.let { msg ->
                Banner("Ups 🥺 $msg", "Reintentar") {
                    val dd = detail
                    val list = items
                    if (dd != null && list != null) translate(dd, list, false)
                }
            }
            val list = items
            when {
                error != null -> Box(Modifier.background(colors.bg)) { ErrorView(error!!) { reload++ } }
                list == null -> LoadingView("Abriendo el capítulo… 📖")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 40.dp),
                ) {
                    itemsIndexed(list, key = { _, it -> it.key }) { _, item ->
                        val useTr = showTr != (flipped[item.key] == true)
                        val shown = if (useTr) tr[item.key] ?: item.html else item.html
                        Paragraph(item, shown, style, colors) {
                            if (tr.containsKey(item.key)) flipped[item.key] = !(flipped[item.key] ?: false)
                        }
                    }
                    item(key = "nav") {
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { goTo(chapter - 1) }, enabled = chapter > 0, modifier = Modifier.weight(1f)) {
                                Text("← Anterior", color = if (chapter > 0) colors.accent else colors.fg.copy(alpha = 0.4f))
                            }
                            Button(
                                onClick = { goTo(chapter + 1) },
                                enabled = chapter < total - 1,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            ) { Text("Siguiente →") }
                        }
                        if (chapter >= total - 1 && total > 0) {
                            Text(
                                "Fin 💗 ¡Gracias por leer!",
                                Modifier.fillMaxWidth().padding(top = 24.dp),
                                textAlign = TextAlign.Center,
                                color = colors.fg.copy(alpha = 0.7f),
                            )
                        }
                        Text(
                            "Tip: mantén presionado un párrafo para ver el original",
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.fg.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }

    if (showStyle) {
        ModalBottomSheet(onDismissRequest = { showStyle = false }) {
            StyleSheet(style) {
                style = it
                it.save()
            }
        }
    }

    if (showChapters && d != null) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            Text("Capítulos", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxHeight(0.7f), state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, chapter - 2))) {
                itemsIndexed(d.chapters) { i, c ->
                    ListItem(
                        headlineContent = {
                            Text(
                                if (total > 1) c.title else "Capítulo único",
                                fontWeight = if (i == chapter) FontWeight.Bold else FontWeight.Normal,
                                color = if (i == chapter) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        },
                        modifier = Modifier.clickable {
                            showChapters = false
                            goTo(i)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Paragraph(item: ReaderItem, shown: String, style: ReaderStyle, colors: ReaderColors, onLongPress: () -> Unit) {
    val size = style.size
    val clickMod = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress)
    when (item.style) {
        ItemStyle.TITLE -> Text(
            html(shown),
            modifier = clickMod.padding(top = 12.dp, bottom = 20.dp),
            fontFamily = style.family,
            fontSize = (size + 6).sp,
            lineHeight = ((size + 6) * 1.3f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.fg,
        )
        ItemStyle.NOTE_HEADER -> Text(
            item.html,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
        )
        ItemStyle.NOTE -> Text(
            html(shown),
            modifier = clickMod
                .clip(RoundedCornerShape(10.dp))
                .background(colors.note)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontFamily = style.family,
            fontSize = (size - 3).sp,
            lineHeight = ((size - 3) * style.line).sp,
            fontStyle = FontStyle.Normal,
            textAlign = if (item.center) TextAlign.Center else TextAlign.Start,
            color = colors.fg.copy(alpha = 0.85f),
        )
        ItemStyle.HR -> Text(
            "✦  ✦  ✦",
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
            color = colors.accent,
        )
        ItemStyle.TEXT -> Text(
            html(shown),
            modifier = clickMod.padding(bottom = (size * 0.8f).dp),
            fontFamily = style.family,
            fontSize = size.sp,
            lineHeight = (size * style.line).sp,
            textAlign = when {
                item.center -> TextAlign.Center
                style.justify -> TextAlign.Justify
                else -> TextAlign.Start
            },
            color = colors.fg,
        )
    }
}

@Composable
private fun StyleSheet(style: ReaderStyle, onChange: (ReaderStyle) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Tema", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            READER_THEMES.forEach { (id, name) ->
                val c = readerColors(id)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onChange(style.copy(theme = id)) }) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c.bg)
                            .border(if (style.theme == id) 3.dp else 1.dp, if (style.theme == id) c.accent else Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = c.fg) }
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Letra", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("serif" to "Serif", "sans" to "Sans", "mono" to "Mono", "cursive" to "Cursiva").forEach { (id, name) ->
                FilterChip(selected = style.font == id, onClick = { onChange(style.copy(font = id)) }, label = { Text(name) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Tamaño: ${style.size.toInt()}", style = MaterialTheme.typography.labelLarge)
        Slider(value = style.size, onValueChange = { onChange(style.copy(size = it)) }, valueRange = 13f..30f, steps = 16)
        Text("Interlineado: ${"%.1f".format(style.line)}", style = MaterialTheme.typography.labelLarge)
        Slider(value = style.line, onValueChange = { onChange(style.copy(line = it)) }, valueRange = 1.2f..2.2f, steps = 9)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Justificar texto", Modifier.weight(1f))
            Switch(checked = style.justify, onCheckedChange = { onChange(style.copy(justify = it)) })
        }
    }
}
