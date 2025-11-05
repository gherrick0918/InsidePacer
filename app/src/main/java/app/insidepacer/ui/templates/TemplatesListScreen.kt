package app.insidepacer.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.data.Units
import app.insidepacer.domain.Template
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesListScreen(onNew: () -> Unit, onEdit: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { TemplateRepo(ctx) }
    val settings = remember { SettingsRepo(ctx) }
    var items by remember { mutableStateOf(emptyList<Template>()) }
    var toDelete by remember { mutableStateOf<Template?>(null) }
    val scope = rememberCoroutineScope()
    val units by settings.units.collectAsState(initial = Units.MPH)

    fun refresh() { items = repo.loadAll() }
    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RpgPanel(
            title = "Training tomes",
            subtitle = "Manage your saved interval adventures."
        ) {
            RpgSectionHeader(
                text = "Library",
                actions = {
                    OutlinedButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        Text("Refresh", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onNew) {
                        Icon(Icons.Default.Add, contentDescription = "New")
                        Text("New tome", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            )

            if (items.isEmpty()) {
                RpgCallout("The library shelves are bare. Forge your first template to store a routine.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items) { t ->
                        Surface(
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium)
                                val totalDuration = t.segments.sumOf { it.seconds }
                                val preview = t.segments.take(3).joinToString(" • ") { seg ->
                                    "${formatSpeed(seg.speed, units)} × ${formatDuration(seg.seconds)}"
                                }
                                Text(
                                    "${t.segments.size} segments • ${formatDuration(totalDuration)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (preview.isNotEmpty()) {
                                    Text(preview, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        }
                    }
                }
            }
        }

        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { toDelete = null },
                title = { Text("Delete tome?") },
                text = { Text("This will remove '${toDelete!!.name}'.") },
                confirmButton = {
                    TextButton(onClick = { scope.launch { repo.delete(toDelete!!.id); toDelete = null; refresh() } }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
            )
        }
    }
}
