package app.insidepacer.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.util.UUID

private fun hms(total: Int): String {
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

@Composable
fun SessionRunScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionRepo = remember { SessionRepo(ctx) }
    val settingsRepo = remember { SettingsRepo(ctx) }
    val units by settingsRepo.units.collectAsState(initial = app.insidepacer.data.Units.MPH)
    val preChangeSeconds by settingsRepo.preChangeSeconds.collectAsState(initial = 10)

    val cue = remember { CuePlayer(ctx) }
    DisposableEffect(Unit) { onDispose { cue.release() } }
    val scheduler = remember { SessionScheduler(cue) }
    val state by scheduler.state.collectAsState(initial = SessionState())

    val demo = remember {
        listOf(
            Segment(1.8, 60),
            Segment(2.2, 60),
            Segment(1.8, 60)
        )
    }

    val isRunning = state.active

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Speed: ${state.speed}")
            state.upcomingSpeed?.let { Text("Up next: $it") }
            Text("Next change in: ${hms(state.nextChangeInSec)}")
            Text("Elapsed: ${hms(state.elapsedSec)}")
            Spacer(Modifier.height(16.dp))
            Row {
                Button(
                    onClick = {
                        scheduler.start(demo, units, preChangeSeconds) { startMs, endMs, elapsedSec, aborted ->
                            scope.launch {
                                val realized = sessionRepo.realizedSegments(demo, elapsedSec)
                                sessionRepo.append(
                                    SessionLog(
                                        id = UUID.randomUUID().toString(),
                                        programId = null,
                                        startMillis = startMs,
                                        endMillis = endMs,
                                        totalSeconds = elapsedSec,
                                        segments = realized,
                                        aborted = aborted
                                    )
                                )
                            }
                        }
                    },
                    enabled = !isRunning
                ) { Text("Start demo") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Stop") }
            }
        }
    }
}
