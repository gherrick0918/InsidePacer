package app.insidepacer.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Segment
import app.insidepacer.domain.Template
import app.insidepacer.service.startSessionService
import kotlinx.coroutines.launch

@Composable
fun TemplateEditorScreen(id: String?, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TemplateRepo(context) }
    var template by remember { mutableStateOf<Template?>(null) }
    var name by remember { mutableStateOf("") }
    var segments by remember { mutableStateOf(listOf<Segment>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        if (id != null) {
            val t = repo.get(id)
            if (t != null) {
                template = t
                name = t.name
                segments = t.segments
            }
        } else {
            name = "New Template"
            segments = listOf(Segment(2.0, 60))
        }
    }

    val settings = remember { SettingsRepo(context) }
    val voiceEnabled by settings.voiceEnabled.collectAsState(initial = true)
    val preChange by settings.preChangeSeconds.collectAsState(initial = 10)
    val units by settings.units.collectAsState(initial = app.insidepacer.data.Units.MPH)

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Template Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(segments) { i, seg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = seg.speed.toString(),
                        onValueChange = {
                            val speed = it.toDoubleOrNull() ?: 0.0
                            val newSegs = segments.toMutableList()
                            newSegs[i] = newSegs[i].copy(speed = speed)
                            segments = newSegs
                        },
                        label = { Text("Speed") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = seg.seconds.toString(),
                        onValueChange = {
                            val secs = it.toIntOrNull() ?: 0
                            val newSegs = segments.toMutableList()
                            newSegs[i] = newSegs[i].copy(seconds = secs)
                            segments = newSegs
                        },
                        label = { Text("Seconds") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(onClick = {
                        val newSegs = segments.toMutableList()
                        if (i > 0) {
                            val temp = newSegs[i - 1]
                            newSegs[i - 1] = newSegs[i]
                            newSegs[i] = temp
                            segments = newSegs
                        }
                    }) { Icon(Icons.Default.ArrowUpward, "") }
                    IconButton(onClick = {
                        val newSegs = segments.toMutableList()
                        if (i < newSegs.size - 1) {
                            val temp = newSegs[i + 1]
                            newSegs[i + 1] = newSegs[i]
                            newSegs[i] = temp
                            segments = newSegs
                        }
                    }) { Icon(Icons.Default.ArrowDownward, "") }
                    IconButton(onClick = {
                        val newSegs = segments.toMutableList()
                        newSegs.removeAt(i)
                        segments = newSegs
                    }) { Icon(Icons.Default.Delete, "") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                Button(onClick = {
                    val newSegs = segments.toMutableList()
                    newSegs.add(Segment(2.0, 60))
                    segments = newSegs
                }) { Icon(Icons.Default.Add, "") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                scope.launch {
                    val t = template
                    if (t != null) {
                        repo.save(t.copy(name = name, segments = segments))
                    } else {
                        repo.create(name, segments)
                    }
                    onNavigateBack()
                }
            }) { Text("Save") }

            Button(onClick = {
                context.startSessionService(
                    segments = segments,
                    units = units,
                    preChange = preChange,
                    voiceOn = voiceEnabled
                )
            }) {
                Icon(Icons.Default.PlayArrow, "")
                Spacer(Modifier.width(8.dp))
                Text("Test")
            }
        }
    }
}
