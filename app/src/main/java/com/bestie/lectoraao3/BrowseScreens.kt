@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bestie.lectoraao3

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SiteChips(site: Site, onChange: (Site) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Site.entries.forEach { s ->
            FilterChip(
                selected = s == site,
                onClick = { onChange(s) },
                label = { Text(if (s == Site.AO3) "AO3 💗" else "FanFiction.net 📘") },
            )
        }
    }
}

@Composable
fun HomeScreen(nav: NavHostController) {
    var site by remember { mutableStateOf(Site.of(App.prefs.homeSite)) }
    val lib by Library.entries.collectAsState()
    val reading = lib.values.filter { it.lastRead > 0 }.sortedByDescending { it.lastRead }.take(10)
    val cats = if (site == Site.AO3) Ao3.categories else Ffn.categories

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Lectora Fics ✨", fontWeight = FontWeight.Bold)
                    Text("Tus fics favoritos, en español", style = MaterialTheme.typography.bodySmall)
                }
            })
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
        ) {
            if (reading.isNotEmpty()) {
                item { SectionTitle("Seguir leyendo 💗") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(reading, key = { it.work.key }) { e ->
                            Card(
                                onClick = { nav.navigate(readerRoute(e.work.site, e.work.id, e.chapterIndex)) },
                                modifier = Modifier.width(220.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(e.work.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(e.work.fandoms.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(10.dp))
                                    val total = maxOf(e.totalChapters, 1)
                                    LinearProgressIndicator(progress = { (e.chapterIndex + 1f) / total }, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(4.dp))
                                    Text("Cap. ${e.chapterIndex + 1} de ${e.totalChapters}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("Explorar")
                SiteChips(site) {
                    site = it
                    App.prefs.homeSite = it.id
                }
                Spacer(Modifier.height(12.dp))
            }
            items(cats.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        Card(
                            onClick = { nav.navigate("fandoms/${site.id}/${Uri.encode(c.id)}") },
                            modifier = Modifier.weight(1f).height(96.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(c.emoji, style = MaterialTheme.typography.headlineSmall)
                                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FandomsScreen(nav: NavHostController, site: Site, category: String) {
    val cat = (if (site == Site.AO3) Ao3.categories else Ffn.categories).firstOrNull { it.id == category }
    var fandoms by remember { mutableStateOf<List<Fandom>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        error = null
        try {
            fandoms = Repo.fandoms(site, category)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendly(e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${cat?.emoji ?: ""} ${cat?.name ?: category}") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar fandom…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
            )
            val list = fandoms
            when {
                error != null && list == null -> ErrorView(error!!) { reload++ }
                list == null -> LoadingView("Buscando fandoms… 🔎")
                else -> {
                    val shown = if (filter.isBlank()) list else list.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(shown, key = { it.url }) { f ->
                            ListItem(
                                headlineContent = { Text(f.name) },
                                trailingContent = { if (f.count.isNotBlank()) Pill(shortCount(f.count)) },
                                modifier = Modifier.clickable {
                                    nav.navigate(
                                        worksRoute(ListSource(site, f.name, fandom = f.name, fandomUrl = f.url, filters = Options.defaults(site, false)))
                                    )
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
fun WorksScreen(nav: NavHostController, src: ListSource) {
    val search = src.query.isNotBlank()
    var filters by remember { mutableStateOf(src.filters) }
    val works = remember { mutableStateListOf<WorkSummary>() }
    var page by remember { mutableIntStateOf(0) }
    var hasNext by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tr = remember { mutableStateMapOf<String, String>() }
    var autoTr by remember { mutableStateOf(App.prefs.autoLists) }
    var trError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lib by Library.entries.collectAsState()

    fun translate(list: List<WorkSummary>) {
        if (list.isEmpty()) return
        val segs = list.filter { !it.isSpanish }.flatMap {
            listOf(Segment("${it.key}_t", it.title, "title"), Segment("${it.key}_s", it.summaryHtml, "text"))
        }.filter { it.html.isNotBlank() }
        scope.launch {
            try {
                Tr.run(WorkMeta(), "listas", "general", segs, cacheOnly = !autoTr) { upd, _, _ -> tr.putAll(upd) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trError = friendly(e)
            }
        }
    }

    fun loadMore() {
        if (loading || !hasNext) return
        loading = true
        error = null
        scope.launch {
            try {
                val (list, next) = Repo.works(src.copy(filters = filters), page + 1)
                page++
                val fresh = list.filter { w -> works.none { it.key == w.key } }
                works.addAll(fresh)
                hasNext = next && fresh.isNotEmpty()
                translate(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(filters) {
        works.clear()
        page = 0
        hasNext = true
        loading = false
        loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(src.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = {
                        autoTr = !autoTr
                        if (autoTr) {
                            if (!Tr.ready()) trError = "Configura tu API key en Ajustes para traducir 💗"
                            translate(works.toList())
                        }
                    }) {
                        Icon(
                            Icons.Filled.Translate,
                            "Traducir resúmenes",
                            tint = if (autoTr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            FilterBar(src.site, search, filters) { filters = it }
            trError?.let { Banner(it, "Ok") { trError = null } }
            if (works.isEmpty() && loading) {
                LoadingView("Buscando fics bonitos… ✨")
            } else if (works.isEmpty() && error != null) {
                ErrorView(error!!) { loadMore() }
            } else if (works.isEmpty() && !hasNext) {
                EmptyView("🔍", "No encontré fics con estos filtros")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(works, key = { it.key }) { w ->
                        WorkCard(
                            work = w,
                            trTitle = if (autoTr) tr["${w.key}_t"] else null,
                            trSummary = if (autoTr) tr["${w.key}_s"] else null,
                            saved = lib[w.key]?.saved == true,
                            onClick = { nav.navigate(workRoute(w)) },
                            onSave = { Library.toggleSaved(w) },
                        )
                    }
                    item(key = "footer_$page") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                error != null -> TextButton(onClick = { loadMore() }) { Text("Reintentar 🔄") }
                                hasNext -> {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                    LaunchedEffect(page) { loadMore() }
                                }
                                else -> Text("Eso es todo 💗", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(nav: NavHostController) {
    var site by remember { mutableStateOf(Site.of(App.prefs.homeSite)) }
    var query by remember { mutableStateOf("") }
    var filters by remember(site) { mutableStateOf(Options.defaults(site, true)) }

    fun go() {
        val q = query.trim()
        if (q.isEmpty()) return
        val link = parseFicLink(q)
        if (link != null) {
            nav.navigate("work/${link.first.id}/${link.second}")
            return
        }
        nav.navigate(worksRoute(ListSource(site, "“$q”", query = q, filters = filters)))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Buscar 🔍", fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            SiteChips(site) { site = it }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Título, autor, personaje, etiqueta o enlace…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
            )
            Spacer(Modifier.height(12.dp))
            if (site == Site.AO3) {
                Text("Filtros", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.offset(x = (-12).dp)) { FilterBar(site, true, filters) { filters = it } }
            } else {
                Text(
                    "En FanFiction.net la búsqueda es por palabras clave. Para filtrar por clasificación o idioma, entra a un fandom desde Inicio 💗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { go() }, modifier = Modifier.fillMaxWidth()) { Text("Buscar ✨") }
            Spacer(Modifier.height(24.dp))
            Text(
                "Tip: pega aquí el enlace de un fic de AO3 o FanFiction.net para abrirlo directo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
