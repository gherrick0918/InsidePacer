package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
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
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(emptyList<Program>()) }
    val activeId by prefs.activeProgramId.collectAsState(initial = null)

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
                    ListItem(
                        headlineContent = { Text(p.name + if (isActive) " • Active" else "") },
                        supportingContent = { Text("${p.weeks} weeks • starts epochDay ${p.startEpochDay}") },
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
