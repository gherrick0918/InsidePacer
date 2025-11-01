package app.insidepacer.ui.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionState
import app.insidepacer.domain.Template
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (existing == null) "New template" else "Edit template") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            if (existing == null) repo.create(name, segments.toList())
                            else repo.save(existing.copy(name = name, segments = segments.toList()))
                            onBack()
                        }
                    }) { Text("Save") }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Template name") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            Text("Pick a speed:")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(speeds) { sp ->
                    FilterChip(selected = selected == sp, onClick = { selected = sp }, label = { Text(String.format(Locale.getDefault(), "%.1f", sp)) })
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = secsText,
                onValueChange = { secsText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canAdd = selected != null && secsText.toIntOrNull()?.let { it > 0 } == true
                Button(onClick = {
                    segments += Segment(selected!!, secsText.toInt())
                }, enabled = canAdd) { Text("Add segment") }
                OutlinedButton(onClick = { if (segments.isNotEmpty()) segments.removeLast() }, enabled = segments.isNotEmpty()) { Text("Remove last") }
                OutlinedButton(onClick = { segments.clear() }, enabled = segments.isNotEmpty()) { Text("Clear all") }
            }

            Spacer(Modifier.height(12.dp))
            Text("Segments (${segments.size})")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(segments) { seg ->
                    ListItem(headlineContent = { Text("${seg.speed} • ${seg.seconds}s") })
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }

            val isRunning = state.active
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Speed: ${state.speed}")
                Text("Next in: ${hms(state.nextChangeInSec)}")
                Text("Elapsed: ${hms(state.elapsedSec)}")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (segments.isNotEmpty()) scheduler.start(segments.toList()) }, enabled = !isRunning && segments.isNotEmpty()) { Text("Run test") }
                    OutlinedButton(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Stop") }
                }
            }
        }
    }
}
