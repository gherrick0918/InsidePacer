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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.Segment
import app.insidepacer.service.pauseSession
import app.insidepacer.service.resumeSession
import app.insidepacer.service.startSessionService
import app.insidepacer.service.stopSession
import kotlinx.coroutines.launch


@Composable
fun QuickSessionScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepo(ctx) }
    val units by settingsRepo.units.collectAsState(initial = app.insidepacer.data.Units.MPH)
    val speeds by settingsRepo.speeds.collectAsState(initial = emptyList())
    val preChangeSeconds by settingsRepo.preChangeSeconds.collectAsState(initial = 10)
    val voiceEnabled by settingsRepo.voiceEnabled.collectAsState(initial = true)

    var segments by remember { mutableStateOf(emptyList<Segment>()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("60") }

    var running by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }


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

        if (running) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Session running…", fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = {
                            ctx.stopSession()
                            running = false
                            paused = false
                        }) { Text("Stop") }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = {
                            if (paused) {
                                ctx.resumeSession()
                            } else {
                                ctx.pauseSession()
                            }
                            paused = !paused
                        }) { Text(if (paused) "Resume" else "Pause") }
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
                        ctx.startSessionService(
                            segments = segments,
                            units = units,
                            preChange = preChangeSeconds,
                            voiceOn = voiceEnabled
                        )
                        running = true
                        paused = false
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