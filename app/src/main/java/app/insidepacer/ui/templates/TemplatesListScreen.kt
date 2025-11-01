package app.insidepacer.ui.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Template
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesListScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { TemplateRepo(ctx) }
    var items by remember { mutableStateOf(emptyList<Template>()) }
    var toDelete by remember { mutableStateOf<Template?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() { items = repo.loadAll() }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Templates") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { refresh() }) { Text("Refresh") }
                    TextButton(onClick = onNew) { Text("New") }
                }
            )
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No templates yet")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(items) { t ->
                    ListItem(
                        headlineContent = { Text(t.name) },
                        supportingContent = { Text("${t.segments.size} segments") },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onEdit(t.id) }) { Text("Edit") }
                                TextButton(onClick = {
                                    scope.launch {
                                        // duplicate
                                        val copy = t.copy(id = repo.newId(), name = t.name + " (copy)")
                                        repo.save(copy)
                                        refresh()
                                    }
                                }) { Text("Duplicate") }
                                TextButton(onClick = { toDelete = t }) { Text("Delete") }
                            }
                        }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }

        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { toDelete = null },
                title = { Text("Delete template?") },
                text = { Text("This will remove '${toDelete!!.name}'.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { repo.delete(toDelete!!.id); toDelete = null; refresh() }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
            )
        }
    }
}
