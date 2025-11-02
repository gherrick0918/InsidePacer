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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.insidepacer.domain.Program
import java.time.LocalDate

@Composable
fun ProgramEditorScreen(programId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { ProgramRepo(ctx) }
    val progressRepo = remember { ProgramProgressRepo.getInstance(ctx) }
    val prefs = remember { ProgramPrefs(ctx) }
    val templateRepo = remember { TemplateRepo(ctx) }

    // load existing or stub
    var program by remember(programId) {
        mutableStateOf(programId?.let { repo.get(it) } ?: run {
            val start = LocalDate.now().toEpochDay()
            repo.create("New program", start, weeks = 4)
        })
    }

    var name by remember(program.name) { mutableStateOf(program.name) }
    var weeksText by remember(program.weeks) { mutableStateOf(program.weeks.toString()) }
    var showPickerForIndex by remember { mutableStateOf<Int?>(null) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val progress by progressRepo.progress.collectAsState()

    fun saveNow(updated: Program) {
        program = updated
        repo.save(updated)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                val weeks = weeksText.toIntOrNull() ?: program.weeks
                saveNow(program.copy(name = name, weeks = weeks))
                onDone()
            }) { Text("Save") }
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
        LaunchedEffect(weeks) {
            if (weeks != program.weeks && weeks > 0) {
                val newGrid = List(weeks) { w ->
                    if (w < program.grid.size) program.grid[w]
                    else List(program.daysPerWeek) { null as String? }
                }
                saveNow(program.copy(weeks = weeks, grid = newGrid))
            }
        }

        val programProgress = progress.firstOrNull { it.programId == program.id }

        val cells = program.weeks * program.daysPerWeek
        if (cells > 0) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(program.daysPerWeek),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed((0 until cells).toList()) { idx, _ ->
                    val w = idx / program.daysPerWeek
                    val d = idx % program.daysPerWeek
                    val tid = program.grid.getOrNull(w)?.getOrNull(d)
                    val label = when (tid) {
                        null -> "Rest"
                        else -> templateRepo.get(tid)?.name ?: "?"
                    }
                    val epochDay = program.startEpochDay + idx
                    val done = programProgress?.doneEpochDays?.contains(epochDay) == true

                    val color = when {
                        done -> MaterialTheme.colorScheme.tertiaryContainer
                        tid != null -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = color,
                        modifier = Modifier
                            .height(48.dp)
                            .clickable { showPickerForIndex = idx }
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("W${w + 1}D${d + 1}")
                                HorizontalDivider(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(1.dp),
                                    thickness = DividerDefaults.Thickness,
                                    color = DividerDefaults.color
                                )
                                Text(label, softWrap = false, maxLines = 1)
                                if (done) {
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.Check, contentDescription = "Completed")
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Active: ${if (program.id == activeId) "yes" else "no"}  •  Start: ${program.startEpochDay}")
    }

    if (showPickerForIndex != null) {
        TemplatePickerDialog(
            onDismiss = { showPickerForIndex = null },
            onPick = { chosenId ->
                val idx = showPickerForIndex!!
                val w = idx / program.daysPerWeek
                val d = idx % program.daysPerWeek

                val currentGrid = program.grid
                val newGrid = if (w >= currentGrid.size) {
                    currentGrid.plus(List(w - currentGrid.size + 1) { List(program.daysPerWeek) { null } })
                } else {
                    currentGrid
                }

                val newRow = newGrid[w].toMutableList()
                newRow[d] = chosenId
                val finalGrid = newGrid.toMutableList()
                finalGrid[w] = newRow
                saveNow(program.copy(grid = finalGrid))
                showPickerForIndex = null
            }
        )
    }
}
