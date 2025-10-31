package app.insidepacer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import app.insidepacer.ui.session.SessionRunScreen

@Composable
fun AppNav(){
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "run"){
        composable("run"){ SessionRunScreen() }
    }
}
