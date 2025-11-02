package app.insidepacer.ui.programs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Program
import java.time.LocalDate

@Composable
fun ProgramEditorScreen(programId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { ProgramRepo(ctx) }
    val progressRepo = remember { ProgramProgressRepo(ctx) }
    val prefs = remember { ProgramPrefs(ctx) }
    val templateRepo = remember { TemplateRepo(ctx) }

    // load existing or stub
    var program by remember(programId) {
        mutableStateOf(programId?.let { repo.get(it) } ?: run {
            val start = LocalDate.now().toEpochDay()
            repo.create("New program", start, weeks = 4)
        })
    }

    var name by remember { mutableStateOf(program.name) }
    var weeksText by remember { mutableStateOf(program.weeks.toString()) }
    var showPickerForIndex by remember { mutableStateOf<Int?>(null) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)

    fun saveNow(updated: Program = program) {
        program = updated
        repo.save(updated)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { saveNow(program.copy(name = name)); onDone() }) { Text("Save") }
        }

        OutlinedTextField(name, { name = it }, label = { Text("Program name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            weeksText,
            { weeksText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Weeks") },
            modifier = Modifier.width(160.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text("Tap a day to assign a template (or Rest).")
        Spacer(Modifier.height(12.dp))

        val weeks = weeksText.toIntOrNull() ?: program.weeks
        // Expand/contract grid if weeks changed
        LaunchedEffect(weeks) {
            if (weeks != program.weeks) {
                val newGrid = List(weeks) { w ->
                    if (w < program.grid.size) program.grid[w]
                    else List(program.daysPerWeek) { null as String? }
                }
                saveNow(program.copy(weeks = weeks, grid = newGrid))
            }
        }

        val cells = program.weeks * program.daysPerWeek
        LazyVerticalGrid(
            columns = GridCells.Fixed(program.daysPerWeek),
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed((0 until cells).toList()) { idx, _ ->
                val w = idx / program.daysPerWeek
                val d = idx % program.daysPerWeek
                val tid = program.grid[w][d]
                val label = when (tid) {
                    null -> "Rest"
                    else -> templateRepo.get(tid)?.name ?: "?"
                }
                val epochDay = program.startEpochDay + idx
                val done = progressRepo.isDone(program.id, epochDay)

                Surface(
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .clickable { showPickerForIndex = idx }
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("W${w + 1}D${d + 1}")
                            HorizontalDivider(
                                modifier = Modifier.height(18.dp).width(1.dp),
                                thickness = DividerDefaults.Thickness,
                                color = DividerDefaults.color
                            )
                            Text(label, softWrap = false)
                            if (done) {
                                Text("  ✓")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Active: ${if (program.id == activeId) "yes" else "no"}  •  Start: ${program.startEpochDay}")
    }

    // Dialog
    if (showPickerForIndex != null) {
        TemplatePickerDialog(
            onDismiss = { showPickerForIndex = null },
            onPick = { chosenId ->
                val idx = showPickerForIndex!!
                val w = idx / program.daysPerWeek
                val d = idx % program.daysPerWeek
                val newRow = program.grid[w].toMutableList()
                newRow[d] = chosenId
                val newGrid = program.grid.toMutableList()
                newGrid[w] = newRow
                saveNow(program.copy(grid = newGrid))
                showPickerForIndex = null
            }
        )
    }
}
