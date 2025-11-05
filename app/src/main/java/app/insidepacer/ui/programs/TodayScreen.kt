package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.data.dayIndexFor
import app.insidepacer.data.inRange
import app.insidepacer.data.templateIdAt
import app.insidepacer.di.Singleton
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.service.startSessionService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(onOpenPrograms: () -> Unit) {
    val ctx = LocalContext.current
    var sessionScheduler by remember { mutableStateOf<SessionScheduler?>(null) }
    LaunchedEffect(ctx) {
        sessionScheduler = Singleton.getSessionScheduler(ctx)
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

    val prefs = remember { ProgramPrefs(ctx) }
    val programRepo = remember { ProgramRepo(ctx) }
    val templateRepo = remember { TemplateRepo(ctx) }
    val progressRepo = remember { ProgramProgressRepo.getInstance(ctx) }
    val settingsRepo = remember { SettingsRepo(ctx) }

    val activeId by prefs.activeProgramId.collectAsState(initial = null)
    val program = remember(activeId) { activeId?.let { programRepo.get(it) } }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val dayIndex = program?.let { dayIndexFor(it, selectedDate.toEpochDay()) }

    val progress by progressRepo.progress.collectAsState()
    val isDone = if (program != null && dayIndex != null && inRange(program, dayIndex)) {
        val epoch = program.startEpochDay + dayIndex
        progress.firstOrNull { it.programId == program.id }?.doneEpochDays?.contains(epoch) == true
    } else {
        false
    }

    val voiceEnabled by settingsRepo.voiceEnabled.collectAsState(initial = true)
    val preChange by settingsRepo.preChangeSeconds.collectAsState(initial = 10)
    val units by settingsRepo.units.collectAsState(initial = app.insidepacer.data.Units.MPH)

    when {
        program == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active program")
                TextButton(onClick = onOpenPrograms) { Text("Choose a program") }
            }
        }

        dayIndex == null || !inRange(program, dayIndex) -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Program not active on this day")
            }
        }

        else -> {
            val w = dayIndex / program.daysPerWeek
            val d = dayIndex % program.daysPerWeek
            val templateId = program.templateIdAt(w, d)
            val epochDay = program.startEpochDay + dayIndex
            val template = templateId?.let { templateRepo.get(it) }
            val templateName = template?.name ?: "Rest"

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { selectedDate = selectedDate.minusDays(1) }) { Text("<") }
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(
                        onClick = { selectedDate = selectedDate.plusDays(1) },
                        enabled = selectedDate.isBefore(LocalDate.now().plusDays(90))
                    ) { Text(">") }
                }

                Text("Week ${w + 1}, Day ${d + 1}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assignment: $templateName")
                    Spacer(Modifier.width(8.dp))
                    if (isDone) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Completed") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                        )
                    }
                }
                template?.let {
                    Spacer(Modifier.height(8.dp))
                    val totalSeconds = it.segments.sumOf { it.seconds }
                    Text("Workout duration: ${formatDuration(totalSeconds)}")
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (templateId == null) return@Button
                            val segments = templateRepo.get(templateId)?.segments ?: emptyList()
                            if (segments.isEmpty()) return@Button
                            ctx.startSessionService(
                                segments = segments,
                                units = units,
                                preChange = preChange,
                                voiceOn = voiceEnabled,
                                programId = program.id,
                                epochDay = epochDay
                            )
                        },
                        enabled = templateId != null && sessionState?.active != true
                    ) {
                        Text(if (isDone) "Run again" else if (selectedDate == LocalDate.now()) "Run today" else "Run this day")
                    }

                    if (sessionState?.active == true) {
                         OutlinedButton(
                            onClick = { sessionScheduler?.togglePause() },
                        ) { Text(if (sessionState?.isPaused == true) "Resume" else "Pause") }

                        OutlinedButton(
                            onClick = { sessionScheduler?.stop() },
                        ) { Text("Stop") }
                    }
                }

                if (sessionState?.active == true) {
                    val totalDuration = sessionState?.segments?.sumOf { it.seconds.toDouble() } ?: 0.0
                    val remaining = totalDuration - (sessionState?.elapsedSec ?: 0)
                    Text("Session in progress: ${formatDuration(sessionState?.elapsedSec ?: 0)} / ${formatDuration(totalDuration.toInt())}")
                    Text("Time left: ${formatDuration(remaining.toInt())}")
                    Text("Current Speed: ${formatSpeed((sessionState?.speed ?: 0f).toDouble(), units)} • Next change in ${formatDuration(sessionState?.nextChangeInSec ?: 0)}")
                }

                if (templateId == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Rest day — assign a template in Campaigns if you want a workout.")
                }
            }
        }
    }
}
