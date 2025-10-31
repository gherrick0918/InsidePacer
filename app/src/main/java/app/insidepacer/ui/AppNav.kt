package app.insidepacer.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.navigation.compose.*
import app.insidepacer.ui.session.SessionRunScreen
import app.insidepacer.ui.onboarding.SpeedsScreen
import app.insidepacer.ui.quick.QuickSessionScreen
import app.insidepacer.data.SettingsRepo

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "gate") {

        composable("gate") {
            // decide start: if no speeds saved -> onboarding, else quick
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

        composable("quick") { QuickSessionScreen(onEditSpeeds = { nav.navigate("onboarding") }) }

        // keep the old run route for now (optional)
        composable("run") { SessionRunScreen() }
    }
}
