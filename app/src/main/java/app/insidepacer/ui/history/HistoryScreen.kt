package app.insidepacer.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.insidepacer.data.SessionRepo
import app.insidepacer.domain.SessionLog
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = {
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
                    }) { Icon(Icons.Default.Share, "Export") }
                    IconButton(onClick = { showConfirm = true }) { Icon(Icons.Default.ClearAll, "Clear") }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (items.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sessions yet")
                }
            } else {
                val sdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(items) { log ->
                        val mins = log.totalSeconds / 60
                        val secs = log.totalSeconds % 60
                        ListItem(
                            headlineContent = {
                                Text("${sdf.format(Date(log.startMillis))} • ${mins} min${if (secs > 0) " ${secs}s" else ""}${if (log.aborted) " • (stopped)" else ""}")
                            },
                            supportingContent = { Text("${log.segments.size} segments") },
                            trailingContent = { TextButton(onClick = { onOpen(log) }) { Text("View") } }
                        )
                        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
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
            .padding(16.dp)) {
        Text(sdf.format(Date(log.startMillis)))
        Text("Duration: ${mins} min${if (secs > 0) " ${secs}s" else ""}${if (log.aborted) " (stopped)" else ""}")
        Spacer(Modifier.height(12.dp))

        Text("Segments")
        Spacer(Modifier.height(8.dp))
        log.segments.forEachIndexed { i, s ->
            ListItem(headlineContent = { Text("#${i + 1}  ${s.speed} • ${s.seconds}s") })
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}
