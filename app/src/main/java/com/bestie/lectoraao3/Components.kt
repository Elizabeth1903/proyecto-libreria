@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bestie.lectoraao3

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun worksRoute(src: ListSource) = "works/" + Uri.encode(src.toJson())
fun workRoute(w: WorkSummary) = "work/${w.site.id}/${w.id}"
fun readerRoute(site: Site, id: String, chapter: Int) = "reader/${site.id}/$id/$chapter"

@Composable
fun html(s: String): AnnotatedString = remember(s) {
    try {
        AnnotatedString.fromHtml(s)
    } catch (_: Exception) {
        AnnotatedString(htmlToPlain(s))
    }
}

@Composable
fun LoadingView(text: String = "Cargando… ✨") {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🥺", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
fun EmptyView(emoji: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun Pill(text: String, bg: Color = MaterialTheme.colorScheme.surfaceVariant, fg: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
    )
}

fun ratingEs(r: String): String = when {
    r.startsWith("General") -> "General"
    r.startsWith("Teen") -> "Adolescentes"
    r.startsWith("Mature") -> "Maduro"
    r.startsWith("Explicit") -> "Explícito"
    r.startsWith("Not Rated") -> "Sin clasificar"
    else -> r
}

@Composable
fun RatingPill(r: String) {
    if (r.isBlank()) return
    val c = when {
        r.startsWith("General") || r == "K" || r == "K+" -> Color(0xFF5BAA6A)
        r.startsWith("Teen") || r == "T" -> Color(0xFFE0A526)
        r.startsWith("Mature") || r == "M" -> Color(0xFFE07A2E)
        r.startsWith("Explicit") || r == "MA" -> Color(0xFFD64545)
        else -> Color(0xFF9E8794)
    }
    Pill(ratingEs(r), bg = c.copy(alpha = 0.18f), fg = c)
}

private val WARNINGS_ES = mapOf(
    "No Archive Warnings Apply" to "Sin advertencias",
    "Creator Chose Not To Use Archive Warnings" to "Sin advertencias elegidas",
    "Choose Not To Use Archive Warnings" to "Sin advertencias elegidas",
    "Graphic Depictions Of Violence" to "Violencia gráfica",
    "Major Character Death" to "Muerte de personaje principal",
    "Rape/Non-Con" to "Sin consentimiento",
    "Underage" to "Menores de edad",
    "Underage Sex" to "Sexo con menores de edad",
)

fun warningEs(w: String) = WARNINGS_ES[w] ?: w

@Composable
fun DropChip(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text(options.firstOrNull { it.first == selected }?.second ?: label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = {
                    open = false
                    onSelect(v)
                })
            }
        }
    }
}

@Composable
fun FilterBar(site: Site, search: Boolean, filters: Filters, onChange: (Filters) -> Unit) {
    if (site == Site.FFN && search) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val sorts = Options.sorts(site, search)
        DropChip("Orden", sorts, filters.sort.ifBlank { sorts.first().first }) { onChange(filters.copy(sort = it)) }
        val ratings = Options.ratings(site)
        DropChip("Clasificación", ratings, filters.rating.ifBlank { ratings.first().first }) { onChange(filters.copy(rating = it)) }
        FilterChip(
            selected = filters.complete,
            onClick = { onChange(filters.copy(complete = !filters.complete)) },
            label = { Text("Completos") },
        )
        DropChip("Idioma", Options.languages, filters.language) { onChange(filters.copy(language = it)) }
    }
}

@Composable
fun WorkCard(
    work: WorkSummary,
    trTitle: String?,
    trSummary: String?,
    saved: Boolean,
    onClick: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        html(trTitle ?: work.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "por ${work.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Guardar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.padding(end = 8.dp)) {
                if (work.fandoms.isNotEmpty()) {
                    Text(
                        work.fandoms.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RatingPill(work.rating)
                    Pill(if (work.complete) "Completo ✓" else "En progreso")
                    if (work.language.isNotBlank()) Pill(work.language)
                }
                if (work.summaryHtml.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        html(trSummary ?: work.summaryHtml),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val tags = (work.relationships + work.characters + work.tags).take(8)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    statsLine(work),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun statsLine(w: WorkSummary): String {
    val parts = mutableListOf<String>()
    if (w.words.isNotBlank()) parts += "📖 ${shortCount(w.words)} palabras"
    if (w.chapters.isNotBlank()) parts += "📑 ${w.chapters} caps"
    if (w.kudos.isNotBlank()) parts += if (w.site == Site.AO3) "♥ ${shortCount(w.kudos)}" else "★ ${shortCount(w.kudos)}"
    if (w.updated.isNotBlank()) parts += "🗓 ${w.updated}"
    return parts.joinToString("  ·  ")
}

@Composable
fun Banner(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
