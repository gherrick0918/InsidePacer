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

@Composable
fun SessionRunScreen(){
    val ctx = LocalContext.current
    val scheduler = remember { SessionScheduler(CuePlayer(ctx)) }
    val state by scheduler.state.collectAsState(initial = SessionState())

    val demo = remember {
        listOf(
            Segment(speed = 1.8, seconds = 60),
            Segment(speed = 2.2, seconds = 60),
            Segment(speed = 1.8, seconds = 60)
        )
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center){
        Column(horizontalAlignment = Alignment.CenterHorizontally){
            Text("Speed: ${'$'}{state.speed}")
            Text("Next change in: ${'$'}{state.nextChangeInSec}s")
            Text("Elapsed: ${'$'}{state.elapsedSec}s")
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = { scheduler.start(demo) }){ Text("Start demo") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { scheduler.stop() }){ Text("Stop") }
            }
        }
    }
}
