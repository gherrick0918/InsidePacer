package app.insidepacer.ui.programs

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun hms(total: Int): String {
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

@Composable
fun TodayScreen(onOpenPrograms: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ProgramPrefs(ctx) }
    val repo = remember { ProgramRepo(ctx) }
    val tplRepo = remember { TemplateRepo(ctx) }
    val progRepo = remember { ProgramProgressRepo.getInstance(ctx) }
    val sessionRepo = remember { SessionRepo(ctx) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val program = remember(activeId) { activeId?.let { repo.get(it) } }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dayIndex = program?.let { (selectedDate.toEpochDay() - it.startEpochDay).toInt() } ?: null

    val progress by progRepo.progress.collectAsState()
    val isDone = if (program != null && dayIndex != null) {
        progress.firstOrNull { it.programId == program.id }?.doneEpochDays?.contains(program.startEpochDay + dayIndex) == true
    } else false

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())
    val running = state.active

    val settings = remember { SettingsRepo(ctx) }
    val voiceEnabled by settings.voiceEnabled.collectAsState(initial = true)
    val preChange by settings.preChangeSeconds.collectAsState(initial = 10)
    val units by settings.units.collectAsState(initial = app.insidepacer.data.Units.MPH)
    LaunchedEffect(voiceEnabled) { cue.setVoiceEnabled(voiceEnabled) }


    when {
        program == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active program")
                TextButton(onClick = onOpenPrograms) { Text("Choose a program") }
            }
        }

        dayIndex!! < 0 || dayIndex >= program.weeks * program.daysPerWeek ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Program not active on this day") }

        else -> {
            val w = dayIndex / program.daysPerWeek
            val d = dayIndex % program.daysPerWeek
            val tid = program.grid[w][d]
            val epochDay = program.startEpochDay + dayIndex
            val tmplName = tid?.let { tplRepo.get(it)?.name } ?: "Rest"

            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { selectedDate = selectedDate.minusDays(1) }) { Text("<") }
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(onClick = { selectedDate = selectedDate.plusDays(1) }, enabled = selectedDate.isBefore(LocalDate.now())) { Text(">") }
                }

                Text("Week ${w + 1}, Day ${d + 1}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assignment: $tmplName")
                    Spacer(Modifier.width(8.dp))
                    if (isDone) {
                        AssistChip(onClick = {}, label = { Text("Completed") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Speed: ${state.speed}")
                Text("Next in: ${hms(state.nextChangeInSec)}")
                Text("Elapsed: ${hms(state.elapsedSec)}")
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = {
                      if (tid == null) return@Button
                      val segments = tplRepo.get(tid)?.segments ?: emptyList()
                      if (segments.isEmpty()) return@Button
                      scheduler.start(segments, units, preChange) { sMs, eMs, elapsedSec, aborted ->
                          val realized = sessionRepo.realizedSegments(segments, elapsedSec)
                          val log = SessionLog(
                              id = "sess_${sMs}",
                              programId = program.id,
                              startMillis = sMs,
                              endMillis = eMs,
                              totalSeconds = elapsedSec,
                              segments = realized,
                              aborted = aborted
                          )
                          CoroutineScope(Dispatchers.IO).launch {
                              try {
                                  sessionRepo.append(log)
                              } catch (e: Throwable) {
                                  Log.e("TodayScreen", "Failed to save session", e)
                              }
                              if (!aborted) {
                                  progRepo.markDone(program.id, epochDay)
                              }
                          }
                      }
                    },
                    enabled = !running && tid != null
                  ) { Text(if (isDone) "Run again" else if (selectedDate == LocalDate.now()) "Run today" else "Run this day") }

                  OutlinedButton(onClick = { scheduler.togglePause() }, enabled = running) { Text("Pause/Resume") }
                  OutlinedButton(onClick = { scheduler.skip() }, enabled = running && tid != null) { Text("Skip") }
                  OutlinedButton(onClick = { scheduler.stop() }, enabled = running) { Text("Stop") }
                }

                if (tid == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Rest day — assign a template in Campaigns if you want a workout.")
                }
            }
        }
    }
}
