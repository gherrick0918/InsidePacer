package app.insidepacer.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SessionRepo
import app.insidepacer.domain.SessionLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (SessionLog) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SessionRepo(ctx) }
    var items by remember { mutableStateOf(emptyList<SessionLog>()) }

    LaunchedEffect(Unit) { items = repo.loadAll().sortedByDescending { it.startMillis } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = {
                        // very light “refresh”
                        items = repo.loadAll().sortedByDescending { it.startMillis }
                    }) { Text("Refresh") }
                }
            )
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No sessions yet")
            }
        } else {
            val sdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(items) { log ->
                    ListItem(
                        headlineContent = { Text("${sdf.format(Date(log.startMillis))} • ${log.totalSeconds/60} min") },
                        supportingContent = { Text("${log.segments.size} segments") },
                        trailingContent = { TextButton(onClick = { onOpen(log) }) { Text("View") } }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(log: SessionLog, onBack: () -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Session details") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
    }) { inner ->
        Column(Modifier.padding(inner).padding(16.dp)) {
            Text(sdf.format(Date(log.startMillis)))
            Text("Duration: ${log.totalSeconds/60} min ${log.totalSeconds%60}s")
            Spacer(Modifier.height(12.dp))
            Text("Segments")
            Spacer(Modifier.height(8.dp))
            log.segments.forEachIndexed { i, s ->
                ListItem(headlineContent = { Text("#${i+1}  ${s.speed} • ${s.seconds}s") })
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
    }
}
