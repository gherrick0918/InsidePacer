package app.insidepacer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.Units
import app.insidepacer.di.Singleton
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.components.WorkoutPlan
import app.insidepacer.ui.theme.Spacings

@Composable
fun SessionScreen() {
    val context = LocalContext.current
    var sessionScheduler by remember { mutableStateOf<SessionScheduler?>(null) }
    LaunchedEffect(context) {
        sessionScheduler = Singleton.getSessionScheduler(context)
    }
    val sessionState by sessionScheduler?.state?.collectAsState() ?: return

    if (sessionScheduler == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (sessionState?.active != true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No active session")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacings.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Time remaining in segment", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Spacings.small))
        Text(text = formatDuration(sessionState?.nextChangeInSec ?: 0), style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(Spacings.large))

        Text(text = "Current Speed", style = MaterialTheme.typography.titleMedium)
        Text(text = formatSpeed((sessionState?.speed ?: 0f).toDouble(), sessionState?.units ?: Units.MPH), style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(Spacings.large))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacings.medium)) {
            Button(onClick = { sessionScheduler?.togglePause() }) {
                Text(text = if (sessionState?.isPaused == true) "Resume" else "Pause")
            }
            Button(onClick = { sessionScheduler?.stop() }) {
                Text(text = "Stop")
            }
        }
        Spacer(modifier = Modifier.height(Spacings.large))

        if (!sessionState?.segments.isNullOrEmpty()) {
            WorkoutPlan(segments = sessionState?.segments ?: emptyList(), currentSegment = sessionState?.currentSegment ?: 0, units = sessionState?.units ?: Units.MPH)
        }
    }
}
