package app.insidepacer.ui.quick

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import app.insidepacer.ui.components.RpgTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private fun hms(total: Int): String {
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSessionScreen(
    onEditSpeeds: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val speeds by repo.speeds.collectAsState(initial = emptyList())
    val appCtx = ctx.applicationContext
    val sessionRepo = remember { SessionRepo(appCtx) }

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())

    var selected by remember { mutableStateOf<Double?>(null) }
    var secsText by remember { mutableStateOf("60") }
    val segments = remember { mutableStateListOf<Segment>() }

    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RpgPanel(title = "Forge your intervals", subtitle = "Choose a saved pace and how long it should last.") {
            if (speeds.isEmpty()) {
                RpgCallout("Add at least one pace in the registry before crafting a session.")
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
                modifier = Modifier.width(200.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canAdd = selected != null && secsText.toIntOrNull()?.let { it > 0 } == true
                Button(onClick = { segments += Segment(selected!!, secsText.toInt()) }, enabled = canAdd) { Text("Add to scroll") }
                OutlinedButton(onClick = { if (segments.isNotEmpty()) segments.removeLast() }, enabled = segments.isNotEmpty()) {
                    Text("Undo last")
                }
                OutlinedButton(onClick = { segments.clear() }, enabled = segments.isNotEmpty()) { Text("Clear all") }
            }
        }

        RpgPanel(
            title = "Segment scroll",
            subtitle = if (segments.isEmpty()) "No intervals forged yet." else "${segments.size} intervals prepared."
        ) {
            if (segments.isEmpty()) {
                RpgCallout("Your scroll is empty. Add segments to begin your quest.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(segments) { seg ->
                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pace ${String.format(Locale.getDefault(), "%.1f", seg.speed)}", style = MaterialTheme.typography.bodyLarge)
                                Text("${seg.seconds}s", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        val isRunning = state.active
        RpgPanel(title = "Run console") {
            RpgSectionHeader("Current status")
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (segments.isNotEmpty()) {
                            val plan = segments.toList()
                            scheduler.start(plan) { startMs, endMs, elapsedSec, aborted ->
                                val realized = sessionRepo.realizedSegments(plan, elapsedSec)
                                val log = app.insidepacer.domain.SessionLog(
                                    id = "sess_${startMs}",
                                    startMillis = startMs,
                                    endMillis = endMs,
                                    totalSeconds = elapsedSec,
                                    segments = realized,
                                    aborted = aborted
                                )
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        sessionRepo.append(log)
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isRunning && segments.isNotEmpty()
                ) { Text("Begin quest") }
                OutlinedButton(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Halt") }
            }

            TextButton(onClick = onEditSpeeds) { Text("Edit pace registry") }
        }
    }
}
