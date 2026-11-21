package com.eroideches.shopai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eroideches.shopai.AppGraph
import com.eroideches.shopai.ui.detail.DetailScreen
import com.eroideches.shopai.ui.saved.SavedScreen
import com.eroideches.shopai.ui.scan.ScanScreen
import com.eroideches.shopai.ui.search.SearchScreen
import com.eroideches.shopai.ui.search.SearchViewModel
import com.eroideches.shopai.ui.settings.DiagnosticsScreen
import com.eroideches.shopai.ui.settings.GuideScreen
import com.eroideches.shopai.ui.settings.KeysScreen
import com.eroideches.shopai.ui.settings.SettingsScreen
import com.eroideches.shopai.ui.settings.SiteBrowserScreen
import com.eroideches.shopai.ui.settings.SitesScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("search", "Cerca", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("scan", "Scansiona", Icons.Outlined.QrCodeScanner, Icons.Filled.QrCodeScanner),
    Tab("saved", "Salvati", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab("settings", "Impostazioni", Icons.Outlined.Settings, Icons.Filled.Settings),
)

/** Richieste in arrivo da fuori (notifica, "Condividi" da un'altra app). */
data class Launch(val tab: String? = null, val query: String? = null, val nonce: Long = System.nanoTime())

@Composable
fun AppRoot(g: AppGraph, launch: Launch?) {
    val nav = rememberNavController()
    val searchVm: SearchViewModel = viewModel { SearchViewModel(g) }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val topLevel = tabs.any { t -> dest?.hierarchy?.any { it.route == t.route } == true } || dest?.route?.startsWith("settings") == true

    LaunchedEffect(launch?.nonce) {
        launch ?: return@LaunchedEffect
        launch.query?.let { q -> searchVm.search(q); nav.goTab("search") }
        launch.tab?.let { nav.goTab(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (topLevel) NavigationBar {
                tabs.forEach { t ->
                    val selected = dest?.hierarchy?.any { it.route == t.route || (t.route == "settings" && it.route?.startsWith("settings") == true) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            NavHost(nav, startDestination = "search") {
                composable("search") {
                    SearchScreen(searchVm, onOpenDetail = { nav.navigate("detail") }, onScan = { nav.goTab("scan") }, onUnlockSite = { nav.navigate("site/$it") })
                }
                composable("scan") {
                    ScanScreen(g) { q -> searchVm.query.value = q; searchVm.search(q); nav.goTab("search") }
                }
                composable("saved") {
                    SavedScreen(g, onOpenDetail = { nav.navigate("detail") }, onSearch = { q -> searchVm.search(q); nav.goTab("search") })
                }
                composable("settings") {
                    SettingsScreen(g, onSites = { nav.navigate("settings/sites") }, onKeys = { nav.navigate("settings/keys") }, onDiagnostics = { nav.navigate("settings/diagnostics") })
                }
                composable("settings/sites") {
                    SitesScreen(g, onBack = { nav.popBackStack() }, onPrepare = { nav.navigate("site/$it") }, onGuide = { nav.navigate("settings/guide/$it") })
                }
                composable("settings/keys") { KeysScreen(g, onBack = { nav.popBackStack() }, onGuide = { nav.navigate("settings/guide/$it") }) }
                composable("settings/guide/{id}") { e -> GuideScreen(g, e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() }) }
                composable("settings/diagnostics") { DiagnosticsScreen(g, onBack = { nav.popBackStack() }, onOpenSite = { nav.navigate("site/$it") }) }
                composable("site/{id}") { e -> SiteBrowserScreen(g, e.arguments?.getString("id") ?: "", onDone = { nav.popBackStack() }) }
                composable("detail") { DetailScreen(g, onBack = { nav.popBackStack() }) }
            }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
