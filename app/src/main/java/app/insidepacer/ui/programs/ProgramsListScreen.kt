package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramRepo
import app.insidepacer.domain.Program
import androidx.compose.material3.MaterialTheme.colorScheme

@Composable
fun ProgramsListScreen(
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenToday: () -> Unit,
    onGenerate: (String?) -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { ProgramRepo(ctx) }
    val prefs = remember { ProgramPrefs(ctx) }
    val programs by repo.programs.collectAsState(initial = emptyList())
    val activeId: String? by prefs.activeProgramId.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Campaigns", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = onNew) { Text("New") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onGenerate(null) }) { Text("Generate") }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(programs) { program ->
                ProgramRow(program, program.id == activeId, onEdit, onOpenToday) { onGenerate(program.id) }
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: Program,
    isActive: Boolean,
    onEdit: (String) -> Unit,
    onOpenToday: () -> Unit,
    onRecalculate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(program.name, fontWeight = FontWeight.Bold)
            if (isActive) {
                Text(" (Active)", color = colorScheme.primary)
            }
        }
        Text("${program.weeks} weeks, ${program.daysPerWeek} days/wk")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEdit(program.id) }) { Text("Edit") }
            OutlinedButton(onClick = onRecalculate) { Text("Recalculate") }
            if (isActive) {
                Button(onClick = onOpenToday) { Text("Today’s Quest") }
            }
        }
    }
}
