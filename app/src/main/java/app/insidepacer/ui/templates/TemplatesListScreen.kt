package app.insidepacer.ui.templates

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Template
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesListScreen(onNew: () -> Unit, onEdit: (String) -> Unit) {
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
                actions = {
                    IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (items.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("No templates yet") }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(items) { t ->
                        ListItem(
                            headlineContent = { Text(t.name) },
                            supportingContent = { Text("${t.segments.size} segments") },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onEdit(t.id) }) { Text("Edit") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            val copy = t.copy(id = repo.newId(), name = t.name + " (copy)")
                                            repo.save(copy); refresh()
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
                        TextButton(onClick = { scope.launch { repo.delete(toDelete!!.id); toDelete = null; refresh() } }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
                )
            }
        }
    }
}
