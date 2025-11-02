package app.insidepacer.ui.programs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.insidepacer.data.*
import app.insidepacer.domain.Program
import java.time.LocalDate

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
    var weeksText by remember { mutableStateOf(program.weeks.toString()) }
    var selectedWeek by remember { mutableStateOf(0) } // 0-based
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var showPickerForIndex by remember { mutableStateOf<Int?>(null) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)

    fun saveNow(updated: Program = program) { program = updated; repo.save(updated) }

    // Reload when coming back so ✓ reflects latest progress
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(program.id) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) program = repo.get(program.id) ?: program
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Expand/contract grid when weeks changes
    val weeks = weeksText.toIntOrNull() ?: program.weeks
    LaunchedEffect(weeks) {
        if (weeks != program.weeks) {
            val newGrid = List(weeks) { w ->
                if (w < program.grid.size) program.grid[w]
                else List(program.daysPerWeek) { null as String? }
            }
            saveNow(program.copy(weeks = weeks, grid = newGrid))
            if (selectedWeek >= weeks) selectedWeek = weeks - 1
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Top bar actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { saveNow(program.copy(name = name)); onDone() }) { Text("Save") }
        }

        OutlinedTextField(name, { value -> name = value }, label = { Text("Program name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                weeksText, { value -> weeksText = value.filter { ch -> ch.isDigit() } },
                label = { Text("Weeks") }, modifier = Modifier.width(140.dp)
            )
            Spacer(Modifier.width(16.dp))
            // Week picker (0..weeks-1)
            Text("Week:")
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(weeks) { w ->
                    FilterChip(
                        selected = selectedWeek == w,
                        onClick = { selectedWeek = w },
                        label = { Text("W${w + 1}") }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Week tools
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val w = selectedWeek
                val cleared = List(program.daysPerWeek) { null as String? }
                val newGrid = program.grid.toMutableList(); newGrid[w] = cleared
                saveNow(program.copy(grid = newGrid))
            }) { Text("Clear week W${selectedWeek + 1}") }

            OutlinedButton(onClick = {
                val from = selectedWeek
                val to = from + 1
                if (to < program.weeks) {
                    val newGrid = program.grid.toMutableList()
                    newGrid[to] = program.grid[from].toList()
                    saveNow(program.copy(grid = newGrid))
                    selectedWeek = to
                }
            }) { Text("Copy W${selectedWeek + 1} → W${selectedWeek + 2}") }
        }

        Spacer(Modifier.height(12.dp))
        Text("Tap a day to assign • ⋮ for more")

        Spacer(Modifier.height(8.dp))
        val dayOffset = selectedWeek * program.daysPerWeek
        LazyVerticalGrid(
            columns = GridCells.Fixed(program.daysPerWeek),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed((0 until program.daysPerWeek).toList()) { d, _ ->
                val idx = dayOffset + d
                val w = selectedWeek
                val tid = program.grid[w][d]
                val label = when (tid) {
                    null -> "Rest"
                    else -> tplRepo.get(tid)?.name ?: "?"
                }
                val epochDay = program.startEpochDay + idx
                val done = progressRepo.isDone(program.id, epochDay)

                val isWalk = tid != null
                val color = when {
                    isWalk && done -> MaterialTheme.colorScheme.tertiaryContainer // Walk Done
                    isWalk && !done -> MaterialTheme.colorScheme.secondaryContainer // Walk Assigned
                    !isWalk && done -> MaterialTheme.colorScheme.surface // Rest Done (just a past day)
                    !isWalk && !done -> MaterialTheme.colorScheme.primary // Rest Assigned
                    else -> MaterialTheme.colorScheme.surface
                }

                Surface(
                    color = color,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .height(56.dp)
                        .clickable { showPickerForIndex = idx }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("D${d + 1}")
                            HorizontalDivider(
                                Modifier
                                    .height(18.dp)
                                    .width(1.dp)
                            )
                            Text(label, softWrap = false)
                            // This is now indicated by color
                            Spacer(Modifier.width(8.dp))
                            // overflow menu trigger
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expanded = true }) { Text("⋮") }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Assign…") },
                                        onClick = { expanded = false; showPickerForIndex = idx }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Set Rest") },
                                        onClick = {
                                            expanded = false
                                            val newRow = program.grid[w].toMutableList(); newRow[d] = null
                                            val newGrid = program.grid.toMutableList(); newGrid[w] = newRow
                                            saveNow(program.copy(grid = newGrid))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy previous day") },
                                        onClick = {
                                            expanded = false
                                            val prevIdx = if (d > 0) idx - 1 else (if (w > 0) (w - 1) * program.daysPerWeek + (program.daysPerWeek - 1) else -1)
                                            if (prevIdx >= 0) {
                                                val pw = prevIdx / program.daysPerWeek
                                                val pd = prevIdx % program.daysPerWeek
                                                val src = program.grid[pw][pd]
                                                val newRow = program.grid[w].toMutableList(); newRow[d] = src
                                                val newGrid = program.grid.toMutableList(); newGrid[w] = newRow
                                                saveNow(program.copy(grid = newGrid))
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (done) "Mark undone" else "Mark done") },
                                        onClick = {
                                            expanded = false
                                            if (done) progressRepo.clearDone(program.id, epochDay)
                                            else progressRepo.markDone(program.id, epochDay)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear day") },
                                        onClick = {
                                            expanded = false
                                            val newRow = program.grid[w].toMutableList(); newRow[d] = null
                                            val newGrid = program.grid.toMutableList(); newGrid[w] = newRow
                                            saveNow(program.copy(grid = newGrid))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Active: ${if (program.id == activeId) "yes" else "no"} • Start: ${program.startEpochDay}")
    }

    // Template picker
    if (showPickerForIndex != null) {
        TemplatePickerDialog(
            onDismiss = { showPickerForIndex = null },
            onPick = { chosenId ->
                val idx = showPickerForIndex!!
                val w = idx / program.daysPerWeek
                val d = idx % program.daysPerWeek
                val newRow = program.grid[w].toMutableList(); newRow[d] = chosenId
                val newGrid = program.grid.toMutableList(); newGrid[w] = newRow
                saveNow(program.copy(grid = newGrid))
                showPickerForIndex = null
            }
        )
    }
}
