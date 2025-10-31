package app.insidepacer.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.Units
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedsScreen(onContinue: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()

    val speeds by repo.speeds.collectAsState(initial = emptyList())
    val units by repo.units.collectAsState(initial = Units.MPH)
    var input by remember { mutableStateOf("") }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("InsidePacer") }) }) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Available speeds", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            if (speeds.isEmpty()) {
                Text("No speeds yet")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speeds) { s ->
                        AssistChip(
                            onClick = { /* no-op */ },
                            label = { Text(String.format(Locale.getDefault(), "%.1f", s)) },
                            trailingIcon = {
                                Text("×",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { scope.launch { repo.setSpeeds(speeds.filter { it != s }) } })
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Add speed (${units.name.lowercase()})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    input.toDoubleOrNull()?.let {
                        val new = (speeds + it).distinct().sorted()
                        scope.launch { repo.setSpeeds(new) }
                    }
                    input = ""
                }) { Text("Add") }

                OutlinedButton(onClick = { scope.launch { repo.setSpeeds(emptyList()) } }) {
                    Text("Clear")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Units")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = units == Units.MPH, onClick = { scope.launch { repo.setUnits(Units.MPH) } }, label = { Text("mph") })
                FilterChip(selected = units == Units.KMH, onClick = { scope.launch { repo.setUnits(Units.KMH) } }, label = { Text("km/h") })
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onContinue, enabled = speeds.isNotEmpty()) { Text("Continue") }
        }
    }
}