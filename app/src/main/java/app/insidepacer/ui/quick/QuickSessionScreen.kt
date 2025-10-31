package app.insidepacer.ui.quick

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionState
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSessionScreen(onEditSpeeds: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val speeds by repo.speeds.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())

    var selected by remember { mutableStateOf<Double?>(null) }
    var secsText by remember { mutableStateOf("60") }
    val segments = remember { mutableStateListOf<Segment>() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quick session", style = MaterialTheme.typography.headlineSmall)
        if (speeds.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Add at least one speed to get started.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onEditSpeeds) { Text("Add speeds") }
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // simple speed picker (buttons)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speeds.forEach { sp ->
                    FilterChip(selected = selected == sp, onClick = { selected = sp }, label = { Text(String.format(Locale.getDefault(), "%.1f", sp)) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = secsText,
            onValueChange = { secsText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Seconds") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(140.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val sp = selected; val secs = secsText.toIntOrNull();
                if (sp != null && secs != null && secs > 0) segments += Segment(sp, secs)
            }) { Text("Add segment") }
            OutlinedButton(onClick = { if (segments.isNotEmpty()) segments.removeLast() }) { Text("Remove last") }
            OutlinedButton(onClick = { segments.clear() }) { Text("Clear all") }
        }

        Spacer(Modifier.height(12.dp))
        Text("Segments (${segments.size})")
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(segments) { idx, seg ->
                ListItem(headlineContent = { Text("#${idx + 1}  ${seg.speed}  •  ${seg.seconds}s") })
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
                Button(onClick = { if (segments.isNotEmpty()) scheduler.start(segments.toList()) }, enabled = !isRunning && segments.isNotEmpty()) { Text("Start") }
                OutlinedButton(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Stop") }
                TextButton(onClick = onEditSpeeds) { Text("Edit speeds") }
            }
        }
    }
}
