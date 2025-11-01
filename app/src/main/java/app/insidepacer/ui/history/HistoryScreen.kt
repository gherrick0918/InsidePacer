package app.insidepacer.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.insidepacer.data.SessionRepo
import app.insidepacer.domain.SessionLog
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import app.insidepacer.ui.components.RpgTag
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpen: (SessionLog) -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { SessionRepo(ctx) }
    var items by remember { mutableStateOf(emptyList<SessionLog>()) }
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() { items = repo.loadAll().sortedByDescending { it.startMillis } }
    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RpgPanel(title = "Run ledger", subtitle = "Review and export your completed quests.") {
            RpgSectionHeader(
                text = "Archive",
                actions = {
                    OutlinedButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        Text("Refresh", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val (sessionsCsv, segsCsv) = repo.exportCsv()
                            val uris = arrayListOf(
                                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", sessionsCsv),
                                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", segsCsv),
                            )
                            val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "text/csv"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(share, "Export session CSVs"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                        Text("Export", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { showConfirm = true }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear")
                        Text("Clear", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            )

            if (items.isEmpty()) {
                RpgCallout("No journeys recorded yet. Complete a session to populate the ledger.")
            } else {
                val sdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items) { log ->
                        val mins = log.totalSeconds / 60
                        val secs = log.totalSeconds % 60
                        Surface(
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "${sdf.format(Date(log.startMillis))}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${mins} min${if (secs > 0) " ${secs}s" else ""}${if (log.aborted) " • stopped" else ""}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RpgTag(text = "${log.segments.size} segments")
                                    TextButton(onClick = { onOpen(log) }) { Text("View chronicle") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("Clear history?") },
                text = { Text("This will remove all saved sessions. This can’t be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            repo.clear()
                            items = emptyList()
                            showConfirm = false
                        }
                    }) { Text("Clear") }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    log: SessionLog,
    onBack: () -> Unit // kept for API symmetry; the shell handles the top bar/back
) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
    val mins = log.totalSeconds / 60
    val secs = log.totalSeconds % 60

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RpgPanel(
            title = "Session chronicle",
            subtitle = sdf.format(Date(log.startMillis))
        ) {
            Text(
                "Duration: ${mins} min${if (secs > 0) " ${secs}s" else ""}${if (log.aborted) " • stopped" else ""}",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        RpgPanel(title = "Realized segments") {
            if (log.segments.isEmpty()) {
                RpgCallout("No segments recorded for this run.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(log.segments) { s ->
                        Surface(
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${s.speed}", style = MaterialTheme.typography.bodyLarge)
                                Text("${s.seconds}s", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
