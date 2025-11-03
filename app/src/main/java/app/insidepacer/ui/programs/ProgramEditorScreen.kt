
package app.insidepacer.ui.programs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Program
import app.insidepacer.ui.components.CalendarView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ProgramEditorScreen(programId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { ProgramRepo(ctx) }
    val prefs = remember { ProgramPrefs(ctx) }
    val progressRepo = remember { ProgramProgressRepo.getInstance(ctx) }
    val tplRepo = remember { TemplateRepo(ctx) }

    var program by remember(programId) {
        mutableStateOf(programId?.let { repo.get(it) } ?: run {
            val start = LocalDate.now().toEpochDay()
            repo.create("New program", start, weeks = 4)
        })
    }

    var name by remember { mutableStateOf(program.name) }
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(program.startEpochDay))) }
    var showPickerForDate by remember { mutableStateOf<LocalDate?>(null) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)

    fun saveNow(updated: Program = program) { program = updated; repo.save(updated) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(program.id) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) program = repo.get(program.id) ?: program
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { saveNow(program.copy(name = name)); onDone() }) { Text("Save") }
        }

        OutlinedTextField(name, { value -> name = value }, label = { Text("Program name") }, modifier = Modifier.fillMaxWidth())

        CalendarView(
            month = month,
            onMonthChanged = { month = it },
            fullDayCell = { date, modifier ->
                val dayIndex = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(program.startEpochDay), date).toInt()
                val w = dayIndex / program.daysPerWeek
                val d = dayIndex % program.daysPerWeek
                val isValidDay = dayIndex >= 0 && w < program.grid.size && d < program.grid[w].size
                val tid = if (isValidDay) program.grid[w][d] else null

                val label = when (tid) {
                    null -> "Rest"
                    else -> tplRepo.get(tid)?.name ?: "?"
                }
                val epochDay = date.toEpochDay()
                val done = progressRepo.isDone(program.id, epochDay)

                val isWorkout = tid != null
                val color = when {
                    isWorkout && done -> MaterialTheme.colorScheme.tertiaryContainer
                    isWorkout && !done -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }

                Surface(
                    color = color,
                    border = if (date.isEqual(LocalDate.now())) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    tonalElevation = 1.dp,
                    modifier = modifier
                ) {
                    Box(Modifier.padding(2.dp).clickable { if (isValidDay) showPickerForDate = date }) {
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                        var expanded by remember { mutableStateOf(false) }
                        if(isValidDay) {
                            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                IconButton(onClick = { expanded = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Assign…") },
                                        onClick = { expanded = false; showPickerForDate = date }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (done) "Mark undone" else "Mark done") },
                                        onClick = {
                                            expanded = false
                                            if (done) progressRepo.clearDone(program.id, epochDay)
                                            else progressRepo.markDone(program.id, epochDay)
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        if(done) {
                            Icon(
                                Icons.Default.Check, "Done",
                                modifier = Modifier.align(Alignment.BottomCenter).size(16.dp)
                            )
                        }
                    }
                }
            }
        )

        Text("Active: ${if (program.id == activeId) "yes" else "no"} • Start: ${
            LocalDate.ofEpochDay(program.startEpochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = {
                val today = LocalDate.now().toEpochDay()
                val updated = program.copy(startEpochDay = today)
                saveNow(updated)
                month = YearMonth.from(LocalDate.ofEpochDay(updated.startEpochDay))
            }) { Text("Set to today") }
            OutlinedButton(onClick = {
                val updated = program.copy(startEpochDay = program.startEpochDay - 1)
                saveNow(updated)
                month = YearMonth.from(LocalDate.ofEpochDay(updated.startEpochDay))
            }) { Text("-1 day") }
            OutlinedButton(onClick = {
                val updated = program.copy(startEpochDay = program.startEpochDay + 1)
                saveNow(updated)
                month = YearMonth.from(LocalDate.ofEpochDay(updated.startEpochDay))
            }) { Text("+1 day") }
            OutlinedButton(onClick = {
                val updated = program.copy(startEpochDay = program.startEpochDay - 7)
                saveNow(updated)
                month = YearMonth.from(LocalDate.ofEpochDay(updated.startEpochDay))
            }) { Text("-7 days") }
            OutlinedButton(onClick = {
                val updated = program.copy(startEpochDay = program.startEpochDay + 7)
                saveNow(updated)
                month = YearMonth.from(LocalDate.ofEpochDay(updated.startEpochDay))
            }) { Text("+7 days") }
        }
    }

    if (showPickerForDate != null) {
        val date = showPickerForDate!!
        TemplatePickerDialog(
            onDismiss = { showPickerForDate = null },
            onPick = { chosenId ->
                val dayIndex = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(program.startEpochDay), date).toInt()
                val w = dayIndex / program.daysPerWeek
                val d = dayIndex % program.daysPerWeek
                if (dayIndex >= 0 && w < program.grid.size && d < program.grid[w].size) {
                    val newRow = program.grid[w].toMutableList(); newRow[d] = chosenId
                    val newGrid = program.grid.toMutableList(); newGrid[w] = newRow
                    saveNow(program.copy(grid = newGrid))
                }
                showPickerForDate = null
            }
        )
    }
}
