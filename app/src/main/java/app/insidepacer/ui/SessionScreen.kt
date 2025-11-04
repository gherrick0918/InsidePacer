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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.insidepacer.di.Singleton
import app.insidepacer.ui.components.WorkoutPlan
import app.insidepacer.ui.theme.Spacings

@Composable
fun SessionScreen() {
    val context = LocalContext.current
    val sessionScheduler = remember { Singleton.getSessionScheduler(context) }
    val sessionState by sessionScheduler.state.collectAsState()

    if (!sessionState.active) {
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
        Text(text = sessionState.nextChangeInSec.toString(), style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(Spacings.large))

        Text(text = "Current Speed", style = MaterialTheme.typography.titleMedium)
        Text(text = sessionState.speed.toString(), style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(Spacings.large))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacings.medium)) {
            Button(onClick = { sessionScheduler.togglePause() }) {
                Text(text = if (sessionState.isPaused) "Resume" else "Pause")
            }
            Button(onClick = { sessionScheduler.stop() }) {
                Text(text = "Stop")
            }
        }
        Spacer(modifier = Modifier.height(Spacings.large))

        if (sessionState.segments.isNotEmpty()) {
            WorkoutPlan(segments = sessionState.segments, currentSegment = sessionState.currentSegment)
        }
    }
}
