package app.insidepacer.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.Units
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import app.insidepacer.ui.components.RpgTag
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

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RpgPanel(
            title = "Pace registry",
            subtitle = "List every steady speed you wish to train with. Tap a tag to remove it."
        ) {
            if (speeds.isEmpty()) {
                RpgCallout("No paces recorded yet. Add one below to begin.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(speeds) { s ->
                        RpgTag(
                            text = String.format(Locale.getDefault(), "%.1f", s),
                            onClick = {
                                scope.launch { repo.setSpeeds(speeds.filter { it != s }) }
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Add pace (${units.name.lowercase(Locale.getDefault())})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    input.toDoubleOrNull()?.let {
                        val new = (speeds + it).distinct().sorted()
                        scope.launch { repo.setSpeeds(new) }
                    }
                    input = ""
                }) { Text("Add pace") }

                OutlinedButton(onClick = { scope.launch { repo.setSpeeds(emptyList()) } }) {
                    Text("Clear all")
                }
            }
        }

        RpgPanel(title = "Preferred units") {
            RpgSectionHeader("Select your guild standard")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RpgTag(
                    text = "mph",
                    selected = units == Units.MPH,
                    onClick = { scope.launch { repo.setUnits(Units.MPH) } }
                )
                RpgTag(
                    text = "km/h",
                    selected = units == Units.KMH,
                    onClick = { scope.launch { repo.setUnits(Units.KMH) } }
                )
            }
        }

        Button(onClick = onContinue, enabled = speeds.isNotEmpty()) { Text("Enter the guild") }
    }
}
