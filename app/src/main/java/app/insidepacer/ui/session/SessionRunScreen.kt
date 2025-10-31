package app.insidepacer.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import java.util.Locale

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
                Button(onClick = { scheduler.start(demo) }, enabled = !isRunning) { Text("Start demo") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { scheduler.stop() }, enabled = isRunning) { Text("Stop") }
            }
        }
    }
}
