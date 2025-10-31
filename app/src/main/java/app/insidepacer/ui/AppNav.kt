package app.insidepacer.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.insidepacer.ui.session.SessionRunScreen
import app.insidepacer.ui.onboarding.SpeedsScreen
import app.insidepacer.ui.quick.QuickSessionScreen
import app.insidepacer.data.SettingsRepo
import app.insidepacer.ui.history.HistoryScreen
import app.insidepacer.ui.history.HistoryDetailScreen
import app.insidepacer.domain.SessionLog
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "gate") {

        composable("gate") {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val repo = remember { SettingsRepo(ctx) }
            val speeds by repo.speeds.collectAsState(initial = emptyList())
            LaunchedEffect(speeds) {
                if (speeds.isEmpty()) nav.navigate("onboarding") { popUpTo("gate") { inclusive = true } }
                else nav.navigate("quick") { popUpTo("gate") { inclusive = true } }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        composable("onboarding") {
            SpeedsScreen(onContinue = { nav.navigate("quick") { popUpTo("onboarding") { inclusive = true } } })
        }

        composable("quick") {
            QuickSessionScreen(
                onEditSpeeds = { nav.navigate("onboarding") },
                onHistory = { nav.navigate("history") }
            )
        }

        // History list
        composable("history") {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onOpen = { log ->
                    val json = Json.encodeToString(SessionLog.serializer(), log)
                    val arg = URLEncoder.encode(json, StandardCharsets.UTF_8.toString())
                    nav.navigate("historyDetail/$arg")
                }
            )
        }

        // History detail (pass serialized log)
        composable(
            route = "historyDetail/{log}",
            arguments = listOf(navArgument("log"){ type = NavType.StringType })
        ) { entry ->
            val raw = entry.arguments?.getString("log").orEmpty()
            val log = remember(raw) { Json.decodeFromString(SessionLog.serializer(), java.net.URLDecoder.decode(raw, "UTF-8")) }
            HistoryDetailScreen(log = log, onBack = { nav.popBackStack() })
        }

        // (optional legacy)
        composable("run") { SessionRunScreen() }
    }
}
