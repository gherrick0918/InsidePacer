package app.insidepacer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.SessionLog
import app.insidepacer.ui.history.HistoryDetailScreen
import app.insidepacer.ui.history.HistoryScreen
import app.insidepacer.ui.onboarding.SpeedsScreen
import app.insidepacer.ui.quick.QuickSessionScreen
import app.insidepacer.ui.templates.TemplateEditorScreen
import app.insidepacer.ui.templates.TemplatesListScreen
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNav() {
    val rootNav = rememberNavController()

    NavHost(rootNav, startDestination = "gate") {

        // Gate decides where to go on launch
        composable("gate") {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val settings = SettingsRepo(ctx)
            val speeds by settings.speeds.collectAsState(initial = emptyList<Int>())
            LaunchedEffect(speeds) {
                if (speeds.isEmpty()) {
                    rootNav.navigate("onboarding") { popUpTo("gate") { inclusive = true } }
                } else {
                    rootNav.navigate("home") { popUpTo("gate") { inclusive = true } }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // One-time onboarding to collect speeds
        composable("onboarding") {
            SpeedsScreen(onContinue = {
                rootNav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }

        // Main shell with bottom navigation + nested graph
        composable("home") {
            MainShell(
                onEditSpeeds = { rootNav.navigate("onboarding") }
            )
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable (() -> Unit)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(onEditSpeeds: () -> Unit) {
    val nav = rememberNavController()
    val items = listOf(
        NavItem("quick", "Quick") { Icon(Icons.Default.PlayArrow, contentDescription = null) },
        NavItem("templates", "Templates") { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
        NavItem("history", "History") { Icon(Icons.Default.History, contentDescription = null) },
    )

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "quick"

    // Show bottom bar only on top-level tabs
    val showBottom = current == "quick" || current == "templates" || current == "history"

    val title = when {
        current.startsWith("templateEditor") -> "Edit template"
        current.startsWith("historyDetail") -> "Session details"
        current == "templates" -> "Templates"
        current == "history" -> "History"
        else -> "Quick session"
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(title) }) },
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    items.forEach { item ->
                        val selected = current.startsWith(item.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(nav, startDestination = "quick", modifier = Modifier.padding(inner)) {

            composable("quick") {
                // Quick can still navigate to onboarding to edit speeds
                QuickSessionScreen(
                    onEditSpeeds = onEditSpeeds,
                    onOpenHistory = { nav.navigate("history") },
                    onOpenTemplates = { nav.navigate("templates") }
                )
            }

            composable("templates") {
                TemplatesListScreen(
                    onBack = { /* not used in shell */ },
                    onNew = { nav.navigate("templateEditor") },
                    onEdit = { id -> nav.navigate("templateEditor?tid=$id") }
                )
            }

            composable(
                route = "templateEditor?tid={tid}",
                arguments = listOf(navArgument("tid") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                val tid = entry.arguments?.getString("tid")
                TemplateEditorScreen(templateId = tid, onBack = { nav.popBackStack() })
            }

            composable("history") {
                HistoryScreen(
                    onBack = { /* not used in shell */ },
                    onOpen = { log ->
                        val json = Json.encodeToString(SessionLog.serializer(), log)
                        val arg = URLEncoder.encode(json, StandardCharsets.UTF_8.toString())
                        nav.navigate("historyDetail/$arg")
                    }
                )
            }

            composable(
                route = "historyDetail/{log}",
                arguments = listOf(navArgument("log") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("log").orEmpty()
                val log = Json.decodeFromString(SessionLog.serializer(), java.net.URLDecoder.decode(raw, "UTF-8"))
                HistoryDetailScreen(log = log, onBack = { nav.popBackStack() })
            }
        }
    }
}
