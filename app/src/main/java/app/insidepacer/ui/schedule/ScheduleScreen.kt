
package app.insidepacer.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.ui.components.CalendarView
import app.insidepacer.ui.components.RpgCallout
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun ScheduleScreen() {
    val ctx = LocalContext.current
    val programPrefs = remember { ProgramPrefs(ctx) }
    val programRepo = remember { ProgramRepo(ctx) }
    val progressRepo = remember { ProgramProgressRepo.getInstance(ctx) }
    val templateRepo = remember { TemplateRepo(ctx) }

    val activeId by programPrefs.activeProgramId.collectAsState(initial = null)
    val program = remember(activeId) { activeId?.let { programRepo.get(it) } }
    val progress by progressRepo.progress.collectAsState()

    var month by remember { mutableStateOf(YearMonth.now()) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (program == null) {
            RpgCallout("No active program. Go to Campaigns to set one.")
        } else {
            Text("Program: ${program.name}")
            CalendarView(
                month = month,
                onMonthChanged = { month = it },
                fullDayCell = { date, modifier ->
                    val dayIndex = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(program.startEpochDay), date).toInt()
                    val w = dayIndex / program.daysPerWeek
                    val d = dayIndex % program.daysPerWeek
                    val isValidDay = dayIndex >= 0 && w < program.grid.size && d < program.grid[w].size
                    val tid = if (isValidDay) program.grid[w][d] else null
                    val isDone = progress.firstOrNull { it.programId == program.id }?.doneEpochDays?.contains(date.toEpochDay()) == true

                    val label = when {
                        tid != null -> templateRepo.get(tid)?.name ?: "?"
                        else -> "Rest"
                    }

                    val isWorkout = tid != null
                    val color = when {
                        isWorkout && isDone -> MaterialTheme.colorScheme.tertiaryContainer
                        isWorkout && !isDone -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        color = color,
                        border = if (date.isEqual(LocalDate.now())) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        tonalElevation = 1.dp,
                        modifier = modifier
                    ) {
                        Box(Modifier.padding(2.dp)) {
                            Text(
                                text = "${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            if (isValidDay) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            if(isDone) {
                                Icon(
                                    Icons.Default.Check, "Done",
                                    modifier = Modifier.align(Alignment.BottomCenter).size(16.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
