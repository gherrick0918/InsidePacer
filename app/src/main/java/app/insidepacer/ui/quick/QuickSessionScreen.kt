package app.insidepacer.ui.quick

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.launch
import java.util.UUID


@Composable
fun QuickSessionScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionRepo = remember { SessionRepo(ctx) }
    val settingsRepo = remember { SettingsRepo(ctx) }
    val units by settingsRepo.units.collectAsState(initial = app.insidepacer.data.Units.MPH)
    val speeds by settingsRepo.speeds.collectAsState(initial = emptyList())
    val preChangeSeconds by settingsRepo.preChangeSeconds.collectAsState(initial = 10)
    val voiceEnabled by settingsRepo.voiceEnabled.collectAsState(initial = true)

    var segments by remember { mutableStateOf(emptyList<Segment>()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("60") }

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    LaunchedEffect(voiceEnabled) { cue.setVoiceEnabled(voiceEnabled) }

    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())
    val isRunning = state.active


    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset session?") },
            text = { Text("This will clear the current session. The session will not be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { segments = emptyList() }
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Quick Session", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        if (isRunning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Speed: ${state.speed}", fontSize = 24.sp)
                    state.upcomingSpeed?.let { Text("Up next: $it", fontSize = 18.sp) }
                    Text(
                        "Time remaining in segment: ${state.nextChangeInSec}",
                        fontSize = 18.sp
                    )
                    Text("Total time: ${state.elapsedSec}", fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = { scheduler.stop() }) { Text("Stop") }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { scheduler.togglePause() }) { Text(if (state.active) "Pause" else "Resume") }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { scheduler.skip() }) { Text("Skip") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(segments) { i, seg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Segment ${i + 1}: ${seg.speed} for ${seg.seconds}s")
                        Row {
                            IconButton(onClick = {
                                val s = segments.toMutableList()
                                if (i > 0) {
                                    val temp = s[i - 1]
                                    s[i - 1] = s[i]
                                    s[i] = temp
                                }
                                segments = s
                            }) { Icon(Icons.Default.ArrowUpward, "") }
                            IconButton(onClick = {
                                val s = segments.toMutableList()
                                if (i < s.size - 1) {
                                    val temp = s[i + 1]
                                    s[i + 1] = s[i]
                                    s[i] = temp
                                }
                                segments = s
                            }) { Icon(Icons.Default.ArrowDownward, "") }
                            IconButton(onClick = {
                                val s = segments.toMutableList()
                                s.removeAt(i)
                                segments = s
                            }) { Icon(Icons.Default.Delete, "") }
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text("Duration (s)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Add Segment:")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speeds.forEach { speed ->
                            Button(onClick = {
                                val seconds = duration.toIntOrNull() ?: 0
                                if (seconds > 0) {
                                    segments = segments + Segment(speed, seconds)
                                }
                            }) {
                                Text("$speed")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        scheduler.start(segments, units, preChangeSeconds) { startMs, endMs, elapsedSec, aborted ->
                            scope.launch {
                                val realized = sessionRepo.realizedSegments(segments, elapsedSec)
                                sessionRepo.append(
                                    SessionLog(
                                        id = UUID.randomUUID().toString(),
                                        programId = null,
                                        startMillis = startMs,
                                        endMillis = endMs,
                                        totalSeconds = elapsedSec,
                                        aborted = aborted,
                                        segments = realized
                                    )
                                )
                            }
                        }
                    },
                    enabled = segments.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Icon(Icons.Default.PlayArrow, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start")
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    enabled = segments.isNotEmpty()
                ) {
                    Icon(Icons.Default.Refresh, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }
    }
}