package app.insidepacer.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.insidepacer.domain.SessionLog
import kotlinx.serialization.json.Json
import java.net.URLEncoder

sealed class Destination(val route: String) {
    object Gate : Destination("gate")
    object Onboarding : Destination("onboarding")
    object Quick : Destination("quick")
    object Templates : Destination("templates")
    object TemplateEditor : Destination("template/{tid}") {
        val arguments = listOf(navArgument("tid") { nullable = true })
        fun buildRoute(templateId: String?) = "template/$templateId"
    }

    object History : Destination("history")
    object HistoryDetail : Destination("history/{log}") {
        val arguments = listOf(navArgument("log") { type = NavType.StringType })
        fun buildRoute(log: SessionLog): String {
            val json = Json.encodeToString(SessionLog.serializer(), log)
            val encoded = URLEncoder.encode(json, "UTF-8")
            return "history/$encoded"
        }
    }

    object Programs : Destination("programs")
    object ProgramEditor : Destination("program/{pid}") {
        val arguments = listOf(navArgument("pid") { nullable = true })
        fun buildRoute(programId: String?) = "program/$programId"
    }

    object GeneratePlan : Destination("generate?pid={pid}") {
        val arguments = listOf(navArgument("pid") { nullable = true; defaultValue = null })
        fun buildRoute(programId: String?): String {
            return if (programId != null) "generate?pid=$programId" else "generate"
        }
    }

    object Today : Destination("today")
    object Schedule : Destination("schedule")
    object Profile : Destination("profile")
    object Statistics : Destination("statistics")
    object Settings : Destination("settings")
}
