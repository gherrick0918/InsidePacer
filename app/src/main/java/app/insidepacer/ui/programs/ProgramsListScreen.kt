package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.domain.Program
import kotlinx.coroutines.launch

@Composable
fun ProgramsListScreen(
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenToday: () -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { ProgramRepo(ctx) }
    val prefs = remember { ProgramPrefs(ctx) }
    val progRepo = remember { ProgramProgressRepo.getInstance(ctx) }
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(emptyList<Program>()) }
    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val progress by progRepo.progress.collectAsState()

    fun refresh() { items = repo.loadAll() }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { refresh() }) { Text("Refresh") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onNew) { Text("New") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onOpenToday) { Text("Today") }
        }
        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No programs yet") }
        } else {
            LazyColumn {
                items(items) { p ->
                    val isActive = p.id == activeId
                    val doneCount = progress.firstOrNull { it.programId == p.id }?.doneEpochDays?.size ?: 0
                    val totalCount = p.weeks * p.daysPerWeek

                    ListItem(
                        headlineContent = { Text(p.name + if (isActive) " • Active" else "") },
                        supportingContent = {
                            Text("Starts epochDay ${p.startEpochDay} • $doneCount / $totalCount days completed")
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { onEdit(p.id) }) { Text("Edit") }
                                TextButton(onClick = { scope.launch { prefs.setActiveProgramId(p.id) } }) { Text("Set active") }
                            }
                        }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }
    }
}
