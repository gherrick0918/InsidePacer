package app.insidepacer.ui.quick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.SettingsRepo
import app.insidepacer.di.Singleton
import app.insidepacer.domain.Segment
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.service.startSessionService
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSessionScreen() {
    val ctx = LocalContext.current
    var sessionScheduler by remember { mutableStateOf<SessionScheduler?>(null) }
    LaunchedEffect(key1 = ctx) {
        sessionScheduler = Singleton.getSessionScheduler(ctx)
    }

    val sessionState by sessionScheduler?.state?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    val settingsRepo = remember { SettingsRepo(ctx) }
    val units by settingsRepo.units.collectAsState(initial = app.insidepacer.data.Units.MPH)
    val speeds by settingsRepo.speeds.collectAsState(initial = emptyList())
    val preChangeSeconds by settingsRepo.preChangeSeconds.collectAsState(initial = 10)
    val voiceEnabled by settingsRepo.voiceEnabled.collectAsState(initial = true)
    val beepEnabled by settingsRepo.beepEnabled.collectAsState(initial = true)
    val hapticsEnabled by settingsRepo.hapticsEnabled.collectAsState(initial = false)

    var segments by remember { mutableStateOf(emptyList<Segment>()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("60") }

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

        if (sessionState?.active == true) {
            Column {
                val totalDuration = sessionState?.segments?.sumOf { it.seconds.toDouble() } ?: 0.0
                val remaining = totalDuration - (sessionState?.elapsedSec ?: 0)
                Text("Session in progress: ${formatDuration(sessionState?.elapsedSec ?: 0)} / ${formatDuration(totalDuration.toInt())}")
                Text("Time left: ${formatDuration(remaining.toInt())}")
                Text("Current Speed: ${formatSpeed((sessionState?.speed ?: 0f).toDouble(), units)} • Next change in ${formatDuration(sessionState?.nextChangeInSec ?: 0)}")
            }
        }

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
                    Text("Segment ${i + 1}: ${formatSpeed(seg.speed.toDouble(), units)} for ${formatDuration(seg.seconds)}")
                    Row {
                        IconButton(
                            onClick = {
                                val s = segments.toMutableList()
                                if (i > 0) {
                                    val temp = s[i - 1]
                                    s[i - 1] = s[i]
                                    s[i] = temp
                                }
                                segments = s
                            },
                            enabled = i > 0
                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move segment ${i + 1} up") }
                        IconButton(
                            onClick = {
                                val s = segments.toMutableList()
                                if (i < s.size - 1) {
                                    val temp = s[i + 1]
                                    s[i + 1] = s[i]
                                    s[i] = temp
                                }
                                segments = s
                            },
                            enabled = i < segments.size - 1
                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move segment ${i + 1} down") }
                        IconButton(onClick = {
                            val s = segments.toMutableList()
                            s.removeAt(i)
                            segments = s
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete segment ${i + 1}") }
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
                FlowRow(
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
                            Text(formatSpeed(speed.toDouble(), units))
                        }
                    }
                }
            }
        }

        val totalSeconds = segments.sumOf { it.seconds }
        if (totalSeconds > 0) {
            Text("Total duration: ${formatDuration(totalSeconds)}")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (sessionState?.active == true) {
                OutlinedButton(
                    onClick = { sessionScheduler?.togglePause() },
                    modifier = Modifier.semantics {
                        stateDescription = if (sessionState?.isPaused == true) "Session is paused" else "Session is running"
                    }
                ) { Text(if (sessionState?.isPaused == true) "Resume" else "Pause") }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = { sessionScheduler?.stop() },
                ) { Text("Stop") }
            } else {
                Button(
                    onClick = {
                        ctx.startSessionService(
                            segments = segments,
                            units = units,
                            preChange = preChangeSeconds,
                            voiceOn = voiceEnabled,
                            beepOn = beepEnabled,
                            hapticsOn = hapticsEnabled
                        )
                    },
                    enabled = segments.isNotEmpty(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start")
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    enabled = segments.isNotEmpty()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }
    }
}
