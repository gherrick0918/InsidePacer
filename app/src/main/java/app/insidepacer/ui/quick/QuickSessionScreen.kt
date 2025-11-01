package app.insidepacer.ui.quick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSessionScreen(onEditSpeeds: () -> Unit, onOpenHistory: () -> Unit, onOpenTemplates: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val speeds by repo.speeds.collectAsState(initial = emptyList())

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())

    var selected by remember { mutableStateOf<Double?>(null) }
    var secsText by remember { mutableStateOf("60") }
    val segments = remember { mutableStateListOf<Segment>() }

    val sessionRepo = remember { SessionRepo(ctx) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("InsidePacer") }) }
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp).fillMaxSize()) {
            Text("Quick session", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))

            if (speeds.isEmpty()) {
                Text("No saved speeds.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onEditSpeeds) { Text("Add speeds") }
                return@Column
            }

            Text("Pick a speed:")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(speeds) { sp ->
                    FilterChip(
                        selected = selected == sp,
                        onClick = { selected = sp },
                        label = { Text(String.format(Locale.getDefault(), "%.1f", sp)) }
                    )
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
                    val sp = selected!!
                    val secs = secsText.toInt()
                    segments += Segment(sp, secs)
                }, enabled = canAdd) { Text("Add segment") }

                OutlinedButton(onClick = { if (segments.isNotEmpty()) segments.removeLast() }, enabled = segments.isNotEmpty()) {
                    Text("Remove last")
                }
                OutlinedButton(onClick = { segments.clear() }, enabled = segments.isNotEmpty()) {
                    Text("Clear all")
                }
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
                    Button(
                        onClick = {
                            if (segments.isNotEmpty()) {
                                val plan = segments.toList()
                                scheduler.start(plan) { startMs, endMs, elapsedSec, aborted ->
                                    val realized = sessionRepo.realizedSegments(plan, elapsedSec)
                                    val log = SessionLog(
                                        id = "sess_${startMs}",
                                        startMillis = startMs,
                                        endMillis = endMs,
                                        totalSeconds = elapsedSec,
                                        segments = realized,
                                        aborted = aborted
                                    )
                                    // fire-and-forget append on main-safe scope
                                    scope.launch {
                                        sessionRepo.append(log)
                                    }
                                }
                            }
                        },
                        enabled = !isRunning && segments.isNotEmpty()
                    ) { Text("Start") }

                    OutlinedButton(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Stop") }
                    TextButton(onClick = onEditSpeeds) { Text("Edit speeds") }
                    TextButton(onClick = onOpenHistory) { Text("History") }
                    TextButton(onClick = onOpenTemplates) { Text("Templates") }
                }
            }
        }
    }
}