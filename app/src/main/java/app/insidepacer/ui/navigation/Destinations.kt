package app.insidepacer.ui.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.insidepacer.domain.SessionLog
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Destination(val route: String) {
    open val arguments: List<NamedNavArgument> = emptyList()

    data object Gate : Destination("gate")
    data object Onboarding : Destination("onboarding")
    data object Quick : Destination("quick")
    data object Templates : Destination("templates")
    data object History : Destination("history")
    data object Programs : Destination("programs")
    data object Today : Destination("today")
    data object Schedule : Destination("schedule")
    data object Settings : Destination("settings")

    data object TemplateEditor : Destination("templateEditor?tid={tid}") {
        override val arguments = listOf(navArgument("tid") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })
        fun buildRoute(templateId: String): String = "templateEditor?tid=$templateId"
    }

    data object HistoryDetail : Destination("historyDetail/{log}") {
        override val arguments = listOf(navArgument("log") { type = NavType.StringType })
        fun buildRoute(log: SessionLog): String {
            val json = Json.encodeToString(SessionLog.serializer(), log)
            val arg = URLEncoder.encode(json, StandardCharsets.UTF_8.toString())
            return "historyDetail/$arg"
        }
    }

    data object ProgramEditor : Destination("programEditor?pid={pid}") {
        override val arguments = listOf(navArgument("pid") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })
        fun buildRoute(id: String): String = "programEditor?pid=$id"
    }
}
