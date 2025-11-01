package app.insidepacer.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.SessionLog
import app.insidepacer.ui.components.RpgBackground
import app.insidepacer.ui.history.HistoryDetailScreen
import app.insidepacer.ui.history.HistoryScreen
import app.insidepacer.ui.navigation.Destination
import app.insidepacer.ui.onboarding.SpeedsScreen
import app.insidepacer.ui.quick.QuickSessionScreen
import app.insidepacer.ui.templates.TemplateEditorScreen
import app.insidepacer.ui.templates.TemplatesListScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    val currentScreenTitle = when (currentRoute) {
        Destination.Quick.route -> "Quick Quest"
        Destination.Templates.route -> "Training Tomes"
        Destination.TemplateEditor.route -> "Edit Training Tome"
        Destination.History.route -> "Run Ledger"
        Destination.HistoryDetail.route -> "Session Chronicle"
        Destination.Onboarding.route -> "Pace Registry"
        else -> "InsidePacer"
    }
    val showBackArrow = currentRoute in listOf(
        Destination.TemplateEditor.route,
        Destination.HistoryDetail.route
    )
    val showDrawerIcon = !showBackArrow && currentRoute != Destination.Onboarding.route &&
        currentRoute != Destination.Gate.route && currentRoute != null

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = homeDestinations.any { it.route == currentRoute },
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("InsidePacer", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Pacekeeper's Guild Ledger",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                homeDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationDrawerItem(
                        label = { Text(drawerLabelFor(destination)) },
                        icon = {
                            Icon(
                                when (destination) {
                                    Destination.Quick -> Icons.Default.PlayArrow
                                    Destination.Templates -> Icons.AutoMirrored.Filled.ListAlt
                                    Destination.History -> Icons.Default.History
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null
                            )
                        },
                        selected = selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (!selected) {
                                nav.navigate(destination.route) {
                                    popUpTo(Destination.Quick.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                if (currentRoute != Destination.Gate.route) {
                    TopAppBar(
                        title = { Text(currentScreenTitle) },
                        navigationIcon = {
                            when {
                                showBackArrow -> {
                                    IconButton(onClick = { nav.popBackStack() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }

                                showDrawerIcon -> {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        ) { innerPadding ->
            RpgBackground {
                Box(Modifier.padding(innerPadding)) {
                    NavHost(
                        navController = nav,
                        startDestination = Destination.Gate.route,
                        modifier = Modifier.fillMaxSize()
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
                SpeedsScreen(onContinue = {
                    nav.navigate(Destination.Quick.route) { popUpTo(Destination.Onboarding.route) { inclusive = true } }
                })
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
}

private fun drawerLabelFor(destination: Destination) = when (destination) {
    Destination.Quick -> "Quick Quest"
    Destination.Templates -> "Training Tomes"
    Destination.History -> "Run Ledger"
    else -> destination.route.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
