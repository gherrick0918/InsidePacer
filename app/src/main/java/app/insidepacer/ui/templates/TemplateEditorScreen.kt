package app.insidepacer.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import app.insidepacer.domain.SessionState
import app.insidepacer.domain.Template
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import app.insidepacer.ui.components.RpgTag
import kotlinx.coroutines.launch
import java.util.Locale

private fun hms(total: Int): String {
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String?,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { TemplateRepo(ctx) }
    val settings = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()

    val existing: Template? = remember(templateId) { templateId?.let { repo.get(it) } }

    var name by remember { mutableStateOf(existing?.name ?: "New template") }
    val speeds by settings.speeds.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<Double?>(null) }
    var secsText by remember { mutableStateOf("60") }
    val segments = remember { mutableStateListOf<Segment>() }

    LaunchedEffect(existing) {
        segments.clear(); existing?.segments?.let { segments.addAll(it) }
    }

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())

    val voiceEnabled by settings.voiceEnabled.collectAsState(initial = true)
    val preChange by settings.preChangeSeconds.collectAsState(initial = 10)
    LaunchedEffect(voiceEnabled) { cue.setVoiceEnabled(voiceEnabled) }


    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RpgPanel(
            title = if (existing == null) "Forge new tome" else "Edit tome",
            subtitle = "Name your routine and decide how it should unfold."
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tome name") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    scope.launch {
                        if (existing == null) repo.create(name, segments.toList())
                        else repo.save(existing.copy(name = name, segments = segments.toList()))
                        onBack()
                    }
                }) { Text("Save tome") }
                OutlinedButton(onClick = onBack) { Text("Cancel") }
            }
        }

        RpgPanel(title = "Craft intervals") {
            if (speeds.isEmpty()) {
                RpgCallout("Add speeds in the registry before forging template segments.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    items(speeds) { sp ->
                        RpgTag(
                            text = String.format(Locale.getDefault(), "%.1f", sp),
                            selected = selected == sp,
                            onClick = { selected = sp }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = secsText,
                onValueChange = { secsText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Duration (seconds)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(0.5f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canAdd = selected != null && secsText.toIntOrNull()?.let { it > 0 } == true
                Button(onClick = { segments += Segment(selected!!, secsText.toInt()) }, enabled = canAdd) { Text("Add segment") }
                OutlinedButton(onClick = { if (segments.isNotEmpty()) segments.removeLast() }, enabled = segments.isNotEmpty()) { Text("Undo last") }
                OutlinedButton(onClick = { segments.clear() }, enabled = segments.isNotEmpty()) { Text("Clear all") }
            }
        }

        RpgPanel(
            title = "Segment ledger",
            subtitle = if (segments.isEmpty()) "No entries yet." else "${segments.size} segments recorded."
        ) {
            if (segments.isEmpty()) {
                RpgCallout("Your ledger is empty. Add at least one segment to craft a full routine.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 220.dp)
                ) {
                    items(segments) { seg ->
                        Surface(
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Pace ${String.format(Locale.getDefault(), "%.1f", seg.speed)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text("${seg.seconds}s", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        val isRunning = state.active
        RpgPanel(title = "Trial run") {
            RpgSectionHeader("Playback status")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Speed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(if (state.speed == 0.0) "--" else String.format(Locale.getDefault(), "%.1f", state.speed))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Next change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(hms(state.nextChangeInSec))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Elapsed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(hms(state.elapsedSec))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (segments.isNotEmpty()) scheduler.start(segments.toList(), preChange) },
                       enabled = !isRunning && segments.isNotEmpty()) { Text("Run test") }
                OutlinedButton(onClick = { scheduler.togglePause() }, enabled = isRunning) { Text("Pause/Resume") }
                OutlinedButton(onClick = { scheduler.skipToNext(segments.toList()) }, enabled = isRunning) { Text("Skip") }
                OutlinedButton(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Stop") }
            }

            TextButton(onClick = onBack) { Text("Return without testing") }
        }
    }
}
