package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.*
import app.insidepacer.domain.*
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val progRepo = remember { ProgramProgressRepo(ctx) }
    val sessionRepo = remember { SessionRepo(ctx.applicationContext) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val program = remember(activeId) { activeId?.let { repo.get(it) } }
    val today = LocalDate.now().toEpochDay()
    val dayIndex = program?.let { (today - it.startEpochDay).toInt() } ?: null

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())
    val running = state.active

    when {
        program == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active program")
                TextButton(onClick = onOpenPrograms) { Text("Choose a program") }
            }
        }

        dayIndex!! < 0 || dayIndex >= program.weeks * program.daysPerWeek ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Program not active today") }

        else -> {
            val w = dayIndex / program.daysPerWeek
            val d = dayIndex % program.daysPerWeek
            val tid = program.grid[w][d]
            val epochDay = program.startEpochDay + dayIndex
            val isDone = remember(program.id, epochDay) { progRepo.isDone(program.id, epochDay) }
            val tmplName = tid?.let { tplRepo.get(it)?.name } ?: "Rest"

            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Week ${w + 1}, Day ${d + 1}")
                Text("Assignment: $tmplName")
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
                            // Start + onFinish append to history and mark done
                            val startMs = System.currentTimeMillis()
                            scheduler.start(segments) { sMs, eMs, elapsedSec, aborted ->
                                val realized = sessionRepo.realizedSegments(segments, elapsedSec)
                                val log = SessionLog(
                                    id = "sess_${sMs}",
                                    startMillis = sMs,
                                    endMillis = eMs,
                                    totalSeconds = elapsedSec,
                                    segments = realized,
                                    aborted = aborted
                                )
                                CoroutineScope(Dispatchers.IO).launch {
                                    try { sessionRepo.append(log) } catch (_: Throwable) {}
                                    if (!aborted) progRepo.markDone(program.id, epochDay)
                                }
                            }
                        },
                        enabled = !running && tid != null
                    ) { Text("Run today") }

                    OutlinedButton(
                        onClick = { CoroutineScope(Dispatchers.IO).launch { progRepo.clearDone(program.id, epochDay) } },
                        enabled = isDone
                    ) { Text("Mark undone") }
                }

                if (tid == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Rest day — assign a template in Campaigns if you want a workout.")
                }
            }
        }
    }
}
