package app.insidepacer.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
fun HistoryScreen(onBack: () -> Unit, onOpen: (SessionLog) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SessionRepo(ctx) }
    var items by remember { mutableStateOf(emptyList<SessionLog>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { items = repo.loadAll().sortedByDescending { it.startMillis } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            items = repo.loadAll().sortedByDescending { it.startMillis }
                        }
                    }) { Text("Refresh") }
                    TextButton(onClick = {
                        // Export and share both CSVs
                        scope.launch {
                            val (sessionsCsv, segsCsv) = repo.exportCsv()
                            val uris = arrayListOf(
                                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", sessionsCsv),
                                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", segsCsv)
                            )
                            val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "text/csv"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(share, "Export session CSVs"))
                        }
                    }) { Text("Export") }
                }
            )
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
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
