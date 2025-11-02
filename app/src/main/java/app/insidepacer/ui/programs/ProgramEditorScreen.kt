package app.insidepacer.ui.programs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramRepo
import app.insidepacer.domain.Program
import java.time.LocalDate

@Composable
fun ProgramEditorScreen(programId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { ProgramRepo(ctx) }
    val existing = remember(programId) { programId?.let { repo.get(it) } }

    var name by remember { mutableStateOf(existing?.name ?: "New program") }
    var weeksText by remember { mutableStateOf(existing?.weeks?.toString() ?: "4") }
    var startEpoch by remember { mutableStateOf(existing?.startEpochDay ?: LocalDate.now().toEpochDay()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                if (existing == null) {
                    repo.create(name, startEpoch, weeksText.toIntOrNull() ?: 4)
                } else {
                    repo.save(existing.copy(name = name))
                }
                onDone()
            }) { Text("Save") }
        }

        OutlinedTextField(name, { name = it }, label = { Text("Program name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            weeksText, { weeksText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Weeks") }, modifier = Modifier.width(160.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text("Start epochDay: $startEpoch  (date picker in a later rev)")
        Spacer(Modifier.height(12.dp))

        val weeks = (weeksText.toIntOrNull() ?: 4)
        val cells = weeks * 7

        Text("Plan grid (preview)")
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items((0 until cells).toList()) { _ ->
                Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.height(36.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("—") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("R7 will assign templates (or Rest) to cells and enable Run Today.")
    }
}
