package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramRepo
import java.time.LocalDate

@Composable
fun TodayScreen(onOpenPrograms: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ProgramPrefs(ctx) }
    val repo = remember { ProgramRepo(ctx) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val program = remember(activeId) { activeId?.let { repo.get(it) } }
    val today = LocalDate.now().toEpochDay()
    val dayIndex = program?.let { (today - it.startEpochDay).toInt() } ?: null

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            program == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active program")
                TextButton(onClick = onOpenPrograms) { Text("Choose a program") }
            }
            dayIndex!! < 0 || dayIndex >= program.weeks * program.daysPerWeek ->
                Text("Program not active today (out of range)")
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Today is Week ${dayIndex / 7 + 1}, Day ${(dayIndex % 7) + 1}")
                Text("No assignment yet — R7 will let you assign a template.")
            }
        }
    }
}
