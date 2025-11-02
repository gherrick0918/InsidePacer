package app.insidepacer.ui.programs

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

@Composable
fun TemplatePickerDialog(
    onDismiss: () -> Unit,
    onPick: (templateId: String?) -> Unit   // null = Rest
) {
    val ctx = LocalContext.current
    val repo = remember { TemplateRepo(ctx) }
    var items by remember { mutableStateOf(emptyList<Template>()) }

    LaunchedEffect(Unit) { items = repo.loadAll() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Assign to day") },
        text = {
            Column {
                OutlinedButton(onClick = { onPick(null) }) { Text("Rest day") }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Spacer(Modifier.height(8.dp))
                Text("Templates")
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) Text("No templates yet")
                else LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(items) { t ->
                        ListItem(
                            headlineContent = { Text(t.name) },
                            supportingContent = { Text("${t.segments.size} segments") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = { TextButton(onClick = { onPick(t.id) }) { Text("Select") } }
                        )
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )
                    }
                }
            }
        }
    )
}
