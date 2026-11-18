@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bestie.lectoraao3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val link = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        link.value = linkFrom(intent)
        setContent {
            LectoraTheme { AppRoot(link) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { link.value = it }
    }

    private fun linkFrom(intent: Intent?): String? {
        if (intent == null) return null
        intent.data?.let { return it.toString() }
        if (intent.action == Intent.ACTION_SEND) return intent.getStringExtra(Intent.EXTRA_TEXT)
        return null
    }
}

/** Saca (sitio, id) de un enlace de AO3 o FanFiction.net. */
fun parseFicLink(text: String): Pair<Site, String>? {
    Regex("archiveofourown\\.org/(?:collections/[^/]+/)?works/(\\d+)").find(text)?.let { return Site.AO3 to it.groupValues[1] }
    Regex("fanfiction\\.net/s/(\\d+)").find(text)?.let { return Site.FFN to it.groupValues[1] }
    return null
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Inicio", Icons.Filled.Home),
    Tab("search", "Buscar", Icons.Filled.Search),
    Tab("library", "Biblioteca", Icons.Filled.Bookmarks),
    Tab("settings", "Ajustes", Icons.Filled.Settings),
)

@Composable
fun AppRoot(link: MutableState<String?>) {
    val nav = rememberNavController()
    val challenge by WebFetcher.challenge.collectAsState()

    LaunchedEffect(link.value) {
        val l = link.value ?: return@LaunchedEffect
        parseFicLink(l)?.let { (site, id) -> nav.navigate("work/${site.id}/$id") }
        link.value = null
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(pad)) {
            AppNav(nav)
        }
    }

    challenge?.let { ChallengeDialog(it) }
}

@Composable
private fun AppNav(nav: NavHostController) {
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("search") { SearchScreen(nav) }
        composable("library") { LibraryScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("login") { LoginScreen(nav) }
        composable("fandoms/{site}/{cat}") { e ->
            val a = e.arguments
            FandomsScreen(nav, Site.of(a?.getString("site")), Uri.decode(a?.getString("cat").orEmpty()))
        }
        composable("works/{src}") { e ->
            val src = try {
                ListSource.fromJson(Uri.decode(e.arguments?.getString("src").orEmpty()))
            } catch (_: Exception) {
                null
            }
            if (src == null) LaunchedEffect(Unit) { nav.popBackStack() } else WorksScreen(nav, src)
        }
        composable("work/{site}/{id}") { e ->
            val a = e.arguments
            WorkScreen(nav, Site.of(a?.getString("site")), a?.getString("id").orEmpty())
        }
        composable("reader/{site}/{id}/{ch}") { e ->
            val a = e.arguments
            ReaderScreen(nav, Site.of(a?.getString("site")), a?.getString("id").orEmpty(), a?.getString("ch")?.toIntOrNull() ?: 0)
        }
        composable("glossary/{key}/{title}") { e ->
            val a = e.arguments
            GlossaryScreen(nav, Uri.decode(a?.getString("key").orEmpty()), Uri.decode(a?.getString("title").orEmpty()))
        }
    }
}

/** Cuando FanFiction.net pide verificar que eres humana, mostramos su página aquí. */
@Composable
private fun ChallengeDialog(web: WebView) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Verificación rápida 🤖", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "El sitio quiere comprobar que eres humana. Completa la verificación de abajo y seguimos solitas ✨",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                AndroidView(
                    factory = {
                        (web.parent as? ViewGroup)?.removeView(web)
                        web
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                )
            }
        }
    }
}
