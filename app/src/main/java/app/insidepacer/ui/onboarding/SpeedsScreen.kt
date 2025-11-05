package app.insidepacer.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import app.insidepacer.core.formatSpeed
import app.insidepacer.core.speedFromUnits
import app.insidepacer.core.speedUnitLabel
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.Units
import app.insidepacer.ui.components.RpgCallout
import app.insidepacer.ui.components.RpgPanel
import app.insidepacer.ui.components.RpgSectionHeader
import app.insidepacer.ui.components.RpgTag
import kotlinx.coroutines.launch
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedsScreen(onContinue: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()

    val speeds by repo.speeds.collectAsState(initial = emptyList())
    val units by repo.units.collectAsState(initial = Units.MPH)
    var input by remember { mutableStateOf("") }
    var minSpeed by remember { mutableStateOf("") }
    var maxSpeed by remember { mutableStateOf("") }
    var increment by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                            text = formatSpeed(s, units),
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
                label = { Text("Add pace (${speedUnitLabel(units)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    input.toDoubleOrNull()?.let {
                        val display = round(it * 10.0) / 10.0
                        val mph = speedFromUnits(display, units)
                        val new = (speeds + mph).distinct().sorted()
                        scope.launch { repo.setSpeeds(new) }
                    }
                    input = ""
                }) { Text("Add pace") }

                OutlinedButton(onClick = { scope.launch { repo.setSpeeds(emptyList()) } }) {
                    Text("Clear all")
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = minSpeed,
                onValueChange = { minSpeed = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Min speed (${speedUnitLabel(units)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = maxSpeed,
                onValueChange = { maxSpeed = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Max speed (${speedUnitLabel(units)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = increment,
                onValueChange = { increment = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Increment (${speedUnitLabel(units)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val min = minSpeed.toDoubleOrNull()
                val max = maxSpeed.toDoubleOrNull()
                val inc = increment.toDoubleOrNull()
                if (min != null && max != null && inc != null && min <= max && inc > 0) {
                    val generatedSpeeds = mutableListOf<Double>()
                    var current = min
                    while (current <= max + 1e-6) {
                        val display = round(current * 10.0) / 10.0
                        generatedSpeeds.add(speedFromUnits(display, units))
                        current += inc
                    }
                    val new = (speeds + generatedSpeeds).distinct().sorted()
                    scope.launch { repo.setSpeeds(new) }
                    minSpeed = ""
                    maxSpeed = ""
                    increment = ""
                }
            }) {
                Text("Generate Speeds")
            }
        }

        RpgPanel(title = "Preferred units") {
            RpgSectionHeader("Select your guild standard")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RpgTag(
                    text = speedUnitLabel(Units.MPH),
                    selected = units == Units.MPH,
                    onClick = { scope.launch { repo.setUnits(Units.MPH) } }
                )
                RpgTag(
                    text = speedUnitLabel(Units.KMH),
                    selected = units == Units.KMH,
                    onClick = { scope.launch { repo.setUnits(Units.KMH) } }
                )
            }
        }

        Button(onClick = onContinue, enabled = speeds.isNotEmpty()) { Text("Enter the guild") }
    }
}
