package app.insidepacer.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.core.formatDistance
import app.insidepacer.core.formatDistancePrecise
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.StatisticsRepository
import app.insidepacer.data.Units
import app.insidepacer.domain.*
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import app.insidepacer.ui.components.RpgTag
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val ctx = LocalContext.current
    val statsRepo = remember { StatisticsRepository(ctx) }
    val settings = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()
    val units by settings.units.collectAsState(initial = Units.MPH)
    
    var overallStats by remember { mutableStateOf<WorkoutStatistics?>(null) }
    var personalRecords by remember { mutableStateOf<List<PersonalRecord>>(emptyList()) }
    var weeklyTrends by remember { mutableStateOf<List<PeriodStatistics>>(emptyList()) }
    var recentWorkouts by remember { mutableStateOf<List<RecentWorkout>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadStatistics() {
        isLoading = true
        scope.launch {
            try {
                overallStats = statsRepo.getOverallStatistics()
                personalRecords = statsRepo.getPersonalRecords()
                weeklyTrends = statsRepo.getWeeklyTrends()
                recentWorkouts = statsRepo.getRecentWorkouts(5)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStatistics()
    }

    if (isLoading && overallStats == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header with refresh button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Statistics Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { loadStatistics() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // Overall Statistics
        item {
            overallStats?.let { stats ->
                OverallStatisticsCard(stats, units)
            } ?: RpgCallout("No workout data available yet.")
        }

        // Personal Records
        if (personalRecords.isNotEmpty()) {
            item {
                PersonalRecordsCard(personalRecords, units)
            }
        }

        // Weekly Trends
        if (weeklyTrends.isNotEmpty()) {
            item {
                WeeklyTrendsCard(weeklyTrends, units)
            }
        }

        // Recent Workouts
        if (recentWorkouts.isNotEmpty()) {
            item {
                RecentWorkoutsCard(recentWorkouts, units)
            }
        }
    }
}

@Composable
private fun OverallStatisticsCard(stats: WorkoutStatistics, units: Units) {
    RpgPanel(title = "Overall Performance", subtitle = "Your complete training journey") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    icon = Icons.Default.EmojiEvents,
                    label = "Total Workouts",
                    value = stats.totalWorkouts.toString(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatCard(
                    icon = Icons.Default.Timer,
                    label = "Total Time",
                    value = formatDuration(stats.totalTimeSeconds),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    icon = Icons.Default.Route,
                    label = "Total Distance",
                    value = formatDistance(stats.totalDistanceMiles, units),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatCard(
                    icon = Icons.Default.Speed,
                    label = "Avg Speed",
                    value = if (stats.averageSpeedMph > 0) formatSpeed(stats.averageSpeedMph, units) else "N/A",
                    modifier = Modifier.weight(1f)
                )
            }

            if (stats.abortedWorkouts > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Completed: ${stats.completedWorkouts} • Stopped: ${stats.abortedWorkouts}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonalRecordsCard(records: List<PersonalRecord>, units: Units) {
    RpgPanel(title = "Personal Records", subtitle = "Your greatest achievements") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            records.forEach { record ->
                PersonalRecordItem(record, units)
            }
        }
    }
}

@Composable
private fun PersonalRecordItem(record: PersonalRecord, units: Units) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()) }
    val date = Instant.ofEpochMilli(record.dateMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (record.type) {
                        RecordType.FASTEST_SPEED -> "Fastest Average Speed"
                        RecordType.LONGEST_DURATION -> "Longest Duration"
                        RecordType.LONGEST_DISTANCE -> "Longest Distance"
                        RecordType.FASTEST_PACE -> "Fastest Pace"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    date.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                when (record.type) {
                    RecordType.FASTEST_SPEED, RecordType.FASTEST_PACE -> formatSpeed(record.value, units)
                    RecordType.LONGEST_DURATION -> formatDuration(record.value.toLong())
                    RecordType.LONGEST_DISTANCE -> formatDistancePrecise(record.value, units)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WeeklyTrendsCard(trends: List<PeriodStatistics>, units: Units) {
    RpgPanel(title = "Weekly Trends", subtitle = "Last 4 weeks of activity") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            trends.forEach { period ->
                WeeklyTrendItem(period, units)
            }
        }
    }
}

@Composable
private fun WeeklyTrendItem(period: PeriodStatistics, units: Units) {
    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    period.periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                RpgTag(text = "${period.workoutCount} workouts")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Time: ${formatDuration(period.totalTimeSeconds)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Distance: ${formatDistance(period.totalDistanceMiles, units)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RecentWorkoutsCard(workouts: List<RecentWorkout>, units: Units) {
    RpgPanel(title = "Recent Activity", subtitle = "Your latest 5 workouts") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            workouts.forEach { workout ->
                RecentWorkoutItem(workout, units)
            }
        }
    }
}

@Composable
private fun RecentWorkoutItem(workout: RecentWorkout, units: Units) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()) }
    val date = Instant.ofEpochMilli(workout.sessionLog.startMillis)
        .atZone(ZoneId.systemDefault())

    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                date.format(dateFormatter),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    formatDuration(workout.sessionLog.totalSeconds),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "•",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatSpeed(workout.averageSpeedMph, units),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "•",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatDistancePrecise(workout.distanceMiles, units),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
