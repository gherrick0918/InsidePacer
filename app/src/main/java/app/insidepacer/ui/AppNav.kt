package app.insidepacer.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.insidepacer.domain.SessionLog
import app.insidepacer.ui.history.HistoryDetailScreen
import app.insidepacer.ui.history.HistoryScreen
import app.insidepacer.ui.onboarding.SpeedsScreen
import app.insidepacer.ui.quick.QuickSessionScreen
import app.insidepacer.ui.templates.TemplateEditorScreen
import app.insidepacer.ui.templates.TemplatesListScreen
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppNav() {
    val rootNav = rememberNavController()

    NavHost(rootNav, startDestination = "gate") {

        // Gate decides where to go on launch
        composable("gate") {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val repo = remember { app.insidepacer.data.SettingsRepo(ctx) }
            LaunchedEffect(Unit) {
                val speeds = repo.speeds.first()   // suspend until loaded
                val dest = if (speeds.isEmpty()) "onboarding" else "home"
                rootNav.navigate(dest) { popUpTo("gate") { inclusive = true } }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // One-time onboarding to collect speeds
        composable("onboarding") {
            Scaffold(
                topBar = { TopAppBar(title = { Text("Set Your Speeds") }) }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    SpeedsScreen(onContinue = {
                        rootNav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                    })
                }
            }
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
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
private fun MainShell(onEditSpeeds: () -> Unit) {
    val nav = rememberNavController()
    val items = listOf(
        NavItem("quick", "Quick") { Icon(Icons.Default.PlayArrow, contentDescription = null) },
        NavItem("templates", "Templates") { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
        NavItem("history", "History") { Icon(Icons.Default.History, contentDescription = null) },
    )

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "quick"

    val currentScreenTitle = when {
        currentRoute.startsWith("quick") -> "Quick Session"
        currentRoute.startsWith("templates") -> "Templates"
        currentRoute.startsWith("templateEditor") -> "Edit Template"
        currentRoute.startsWith("history") -> "History"
        currentRoute.startsWith("historyDetail") -> "Session Details"
        else -> "InsidePacer"
    }
    val showBackArrow = currentRoute.startsWith("templateEditor") || currentRoute.startsWith("historyDetail")


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentScreenTitle) },
                navigationIcon = {
                    if (showBackArrow) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    val selected = currentRoute.startsWith(item.route)
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
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = "quick",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("quick") {
                QuickSessionScreen(
                    onEditSpeeds = onEditSpeeds,
                )
            }

            composable("templates") {
                TemplatesListScreen(
                    onNew = { nav.navigate("templateEditor") },
                    onEdit = { id -> nav.navigate("templateEditor?tid=$id") }
                )
            }

            composable(
                route = "templateEditor?tid={tid}",
                arguments = listOf(navArgument("tid") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                })
            ) { entry ->
                val tid = entry.arguments?.getString("tid")
                TemplateEditorScreen(templateId = tid, onBack = { nav.popBackStack() })
            }

            composable("history") {
                HistoryScreen(
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
