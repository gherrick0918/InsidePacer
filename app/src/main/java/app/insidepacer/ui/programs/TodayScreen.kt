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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.data.computeStreaks
import app.insidepacer.data.dayIndexFor
import app.insidepacer.data.inRange
import app.insidepacer.service.pauseSession
import app.insidepacer.service.resumeSession
import app.insidepacer.service.startSessionService
import app.insidepacer.service.stopSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(onOpenPrograms: () -> Unit) {
    val ctx = LocalContext.current
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

    var running by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }

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
            val templateId = program.grid[w][d]
            val epochDay = program.startEpochDay + dayIndex
            val templateName = templateId?.let { templateRepo.get(it)?.name } ?: "Rest"

            val streaks = program.let { computeStreaks(it, progressRepo) }

            Column(
                Modifier.fillMaxSize().padding(16.dp),
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
                val streakValue = streaks
                if (streakValue != null) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Streak ${streakValue.current} • Best ${streakValue.longest}") }
                    )
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
                            running = true
                            paused = false
                        },
                        enabled = !running && templateId != null
                    ) {
                        Text(if (isDone) "Run again" else if (selectedDate == LocalDate.now()) "Run today" else "Run this day")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!running) return@OutlinedButton
                            if (paused) {
                                ctx.resumeSession()
                            } else {
                                ctx.pauseSession()
                            }
                            paused = !paused
                        },
                        enabled = running
                    ) { Text(if (paused) "Resume" else "Pause") }

                    OutlinedButton(
                        onClick = {
                            ctx.stopSession()
                            running = false
                            paused = false
                        },
                        enabled = running
                    ) { Text("Stop") }
                }

                if (templateId == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Rest day — assign a template in Campaigns if you want a workout.")
                }
            }
        }
    }
}
