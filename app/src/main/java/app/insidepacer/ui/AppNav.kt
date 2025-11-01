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
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.SessionLog
import app.insidepacer.ui.history.HistoryDetailScreen
import app.insidepacer.ui.history.HistoryScreen
import app.insidepacer.ui.navigation.Destination
import app.insidepacer.ui.onboarding.SpeedsScreen
import app.insidepacer.ui.quick.QuickSessionScreen
import app.insidepacer.ui.templates.TemplateEditorScreen
import app.insidepacer.ui.templates.TemplatesListScreen
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val homeDestinations = listOf(
        Destination.Quick,
        Destination.Templates,
        Destination.History
    )
    val homeIcons = mapOf(
        Destination.Quick to Icons.Default.PlayArrow,
        Destination.Templates to Icons.AutoMirrored.Filled.ListAlt,
        Destination.History to Icons.Default.History
    )

    val currentScreenTitle = when (currentRoute) {
        Destination.Quick.route -> "Quick Session"
        Destination.Templates.route -> "Templates"
        Destination.TemplateEditor.route -> "Edit Template"
        Destination.History.route -> "History"
        Destination.HistoryDetail.route -> "Session Details"
        else -> "InsidePacer"
    }
    val showBackArrow = currentRoute in listOf(
        Destination.TemplateEditor.route,
        Destination.HistoryDetail.route
    )

    Scaffold(
        topBar = {
            // Onboarding has its own separate top bar
            if (currentRoute != Destination.Onboarding.route) {
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
            }
        },
        bottomBar = {
            if (homeDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    homeDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigate(destination.route) },
                            icon = {
                                homeIcons[destination]?.let {
                                    Icon(
                                        it,
                                        contentDescription = null
                                    )
                                }
                            },
                            label = { Text(destination.route.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Destination.Gate.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Gate.route) {
                val context = LocalContext.current
                val repo = remember { SettingsRepo(context) }
                LaunchedEffect(Unit) {
                    val speeds = repo.speeds.first()
                    val dest = if (speeds.isEmpty()) Destination.Onboarding.route else Destination.Quick.route
                    nav.navigate(dest) { popUpTo(Destination.Gate.route) { inclusive = true } }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            composable(Destination.Onboarding.route) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Set Your Speeds") }) }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        SpeedsScreen(onContinue = {
                            nav.navigate(Destination.Quick.route) { popUpTo(Destination.Onboarding.route) { inclusive = true } }
                        })
                    }
                }
            }

            composable(Destination.Quick.route) {
                QuickSessionScreen(onEditSpeeds = { nav.navigate(Destination.Onboarding.route) })
            }

            composable(Destination.Templates.route) {
                TemplatesListScreen(
                    onNew = { nav.navigate(Destination.TemplateEditor.route) },
                    onEdit = { id -> nav.navigate(Destination.TemplateEditor.buildRoute(id)) }
                )
            }

            composable(
                route = Destination.TemplateEditor.route,
                arguments = Destination.TemplateEditor.arguments
            ) { entry ->
                val tid = entry.arguments?.getString("tid")
                TemplateEditorScreen(templateId = tid, onBack = { nav.popBackStack() })
            }

            composable(Destination.History.route) {
                HistoryScreen(
                    onOpen = { log -> nav.navigate(Destination.HistoryDetail.buildRoute(log)) }
                )
            }

            composable(
                route = Destination.HistoryDetail.route,
                arguments = Destination.HistoryDetail.arguments
            ) { entry ->
                val raw = entry.arguments?.getString("log").orEmpty()
                val log = Json.decodeFromString(SessionLog.serializer(), java.net.URLDecoder.decode(raw, "UTF-8"))
                HistoryDetailScreen(log = log, onBack = { nav.popBackStack() })
            }
        }
    }
}
