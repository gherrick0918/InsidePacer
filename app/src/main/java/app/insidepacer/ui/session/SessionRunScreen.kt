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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.Segment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.insidepacer.service.pauseSession
import app.insidepacer.service.resumeSession
import app.insidepacer.service.startSessionService
import app.insidepacer.service.stopSession

@Composable
fun SessionRunScreen() {
    val ctx = LocalContext.current
    val settingsRepo = remember { SettingsRepo(ctx) }
    val units by settingsRepo.units.collectAsState(initial = app.insidepacer.data.Units.MPH)
    val preChangeSeconds by settingsRepo.preChangeSeconds.collectAsState(initial = 10)
    val voiceEnabled by settingsRepo.voiceEnabled.collectAsState(initial = true)

    var running by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }

    val demo = remember {
        listOf(
            Segment(1.8, 60),
            Segment(2.2, 60),
            Segment(1.8, 60)
        )
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (running) "Demo session running…" else "Start the demo session")
            Spacer(Modifier.height(16.dp))
            Row {
                Button(
                    onClick = {
                        ctx.startSessionService(
                            segments = demo,
                            units = units,
                            preChange = preChangeSeconds,
                            voiceOn = voiceEnabled
                        )
                        running = true
                        paused = false
                    },
                    enabled = !running
                ) { Text("Start demo") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (paused) {
                            ctx.resumeSession()
                        } else {
                            ctx.pauseSession()
                        }
                        paused = !paused
                    },
                    enabled = running
                ) { Text(if (paused) "Resume" else "Pause") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        ctx.stopSession()
                        running = false
                        paused = false
                    },
                    enabled = running
                ) { Text("Stop") }
            }
        }
    }
}
