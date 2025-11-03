package app.insidepacer.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.data.dayIndexFor
import app.insidepacer.data.inRange
import app.insidepacer.ui.components.CalendarView
import app.insidepacer.ui.programs.TemplatePickerDialog
import java.time.LocalDate

@Composable
fun ScheduleScreen(
    onOpenPrograms: () -> Unit,
    onOpenToday: () -> Unit,
    onRunToday: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { ProgramPrefs(ctx) }
    val progRepo = remember { ProgramRepo(ctx) }
    val tplRepo = remember { TemplateRepo(ctx) }
    val progressRepo = remember { ProgramProgressRepo.getInstance(ctx) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val program = remember(activeId) { activeId?.let { progRepo.get(it) } }
    val progress by progressRepo.progress.collectAsState()

    var pickForEpoch by remember { mutableStateOf<Long?>(null) }
    var menuForEpoch by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Campaign calendar", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenPrograms) { Text("Campaigns") }
                TextButton(onClick = onOpenToday) { Text("Today") }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (program == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No active program")
                    TextButton(onClick = onOpenPrograms) { Text("Choose a program") }
                }
            }
            return@Column
        }

        CalendarView(
            startEpochDay = program.startEpochDay,
            weeks = program.weeks,
            daysPerWeek = program.daysPerWeek,
            isPlanned = { epoch ->
                val idx = dayIndexFor(program, epoch)
                inRange(program, idx) && program.grid[idx / program.daysPerWeek][idx % program.daysPerWeek] != null
            },
            isDone = { epoch ->
                progress.firstOrNull { it.programId == program.id }?.doneEpochDays?.contains(epoch) == true
            },
            onDayClick = { epoch -> menuForEpoch = epoch }
        )

        Spacer(Modifier.height(8.dp))
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    if (menuForEpoch != null && program != null) {
        val epoch = menuForEpoch!!
        val idx = dayIndexFor(program, epoch)
        val inRangeDay = inRange(program, idx)
        val w = if (inRangeDay) idx / program.daysPerWeek else -1
        val d = if (inRangeDay) idx % program.daysPerWeek else -1
        val tid = if (inRangeDay) program.grid[w][d] else null
        val isToday = epoch == LocalDate.now().toEpochDay()
        val isDone = progressRepo.isDone(program.id, epoch)

        AlertDialog(
            onDismissRequest = { menuForEpoch = null },
            confirmButton = {},
            title = { Text("Day actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Date: ${LocalDate.ofEpochDay(epoch)}  •  ${if (inRangeDay) "W${w + 1}D${d + 1}" else "Out of range"}")
                    if (isToday && tid != null) {
                        Button(onClick = { menuForEpoch = null; onRunToday() }) { Text("Run today") }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            menuForEpoch = null
                            pickForEpoch = epoch
                        }) { Text("Assign…") }
                        OutlinedButton(onClick = {
                            if (inRangeDay) {
                                val row = program.grid[w].toMutableList(); row[d] = null
                                val grid = program.grid.toMutableList(); grid[w] = row
                                progRepo.save(program.copy(grid = grid))
                                menuForEpoch = null
                            }
                        }) { Text("Set Rest") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (isDone) progressRepo.clearDone(program.id, epoch) else progressRepo.markDone(program.id, epoch)
                            menuForEpoch = null
                        }) { Text(if (isDone) "Mark undone" else "Mark done") }

                        OutlinedButton(onClick = { status = "Tap a target day… (then choose 'Move here')" }) { Text("Move assignment…") }
                    }
                    Divider()
                    Text("Tip: to move, first copy the template ID in Campaigns, or just Re-Assign here.")
                }
            }
        )
    }

    if (pickForEpoch != null && program != null) {
        TemplatePickerDialog(
            onDismiss = { pickForEpoch = null },
            onPick = { chosenId ->
                val epoch = pickForEpoch!!
                val idx = dayIndexFor(program, epoch)
                if (inRange(program, idx)) {
                    val w = idx / program.daysPerWeek
                    val d = idx % program.daysPerWeek
                    val row = program.grid[w].toMutableList(); row[d] = chosenId
                    val grid = program.grid.toMutableList(); grid[w] = row
                    progRepo.save(program.copy(grid = grid))
                }
                pickForEpoch = null
            }
        )
    }
}
