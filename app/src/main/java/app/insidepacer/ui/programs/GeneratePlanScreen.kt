package app.insidepacer.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProfileRepo
import app.insidepacer.data.ProgramGenerator
import app.insidepacer.data.ProgramPrefs
import app.insidepacer.data.ProgramRepo
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.TemplateRepo
import app.insidepacer.domain.Program
import app.insidepacer.domain.UserProfile
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.pow

@Composable
fun GeneratePlanScreen(onDone: (Program) -> Unit) {
    val ctx = LocalContext.current
    val profileRepo = remember { ProfileRepo(ctx) }
    val programRepo = remember { ProgramRepo(ctx) }
    val templateRepo = remember { TemplateRepo(ctx) }
    val generator = remember { ProgramGenerator(templateRepo, programRepo) }
    val prefs = remember { ProgramPrefs(ctx) }
    val settings = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()

    val profile by profileRepo.profile.collectAsState(initial = UserProfile())
    val speeds by settings.speeds.collectAsState(initial = emptyList())

    var planName by remember { mutableStateOf("Adaptive Plan") }
    var weeksText by remember { mutableStateOf("8") }
    var status by remember { mutableStateOf<String?>(null) }
    var overwriteByName by remember { mutableStateOf(true) }

    val heightMeters = profile.heightCm / 100.0
    val bmi = if (heightMeters > 0) profile.weightKg / heightMeters.pow(2.0) else 0.0
    val risk = profile.age >= 55 || bmi >= 30.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Generate campaign", style = MaterialTheme.typography.titleMedium)
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Text("Profile snapshot", fontWeight = FontWeight.Bold)
        Text(
            "Age: ${profile.age} • Height: ${profile.heightCm} cm • Weight: ${String.format(Locale.getDefault(), "%.1f", profile.weightKg)} kg"
        )
        profile.targetWeightKg?.let {
            Text("Target weight: ${String.format(Locale.getDefault(), "%.1f", it)} kg")
        }
        Text("Days/week: ${profile.preferredDaysPerWeek} • Session range: ${profile.sessionMinMin}-${profile.sessionMaxMin} min")
        Text("Level: ${profile.level} • Units: ${profile.units}")
        Text(
            String.format(
                Locale.getDefault(),
                "BMI: %.1f (%s risk)",
                bmi,
                if (risk) "reduced intensity" else "full intensity"
            )
        )
        Text("Plan start: ${LocalDate.ofEpochDay(profile.startEpochDay)}")

        Divider()
        Text(
            "Saved speeds (${speeds.size}): " +
                if (speeds.isEmpty()) "None" else speeds.joinToString(", ") {
                    String.format(Locale.getDefault(), "%.1f", it)
                }
        )
        if (speeds.isEmpty()) {
            Text("Add speeds on the Pace Registry screen to enable plan generation.", color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = planName,
            onValueChange = { planName = it },
            label = { Text("Plan name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = weeksText,
            onValueChange = { weeksText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Weeks") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = speeds.isNotEmpty(),
                onClick = {
                    status = "Generating…"
                    scope.launch {
                        try {
                            val weeksValue = weeksText.toIntOrNull() ?: 8
                            val overwriteId = if (overwriteByName) programRepo.findByName(planName)?.id else null
                            val output = generator.generate(
                                name = planName,
                                weeks = weeksValue,
                                inx = ProgramGenerator.Input(
                                    startEpochDay = profile.startEpochDay,
                                    daysPerWeek = profile.preferredDaysPerWeek,
                                    sessionMinMin = profile.sessionMinMin,
                                    sessionMaxMin = profile.sessionMaxMin,
                                    level = profile.level,
                                    speeds = speeds,
                                    age = profile.age,
                                    heightCm = profile.heightCm,
                                    weightKg = profile.weightKg
                                ),
                                overwriteProgramId = overwriteId
                            )
                            prefs.setActiveProgramId(output.program.id)
                            status = "Created ${output.program.name} (${output.program.weeks} weeks)."
                            onDone(output.program)
                        } catch (t: Throwable) {
                            status = "Failed: ${t.message ?: t::class.simpleName}"
                        }
                    }
                }
            ) { Text("Generate & Save") }

            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { weeksText = "8"; planName = "Adaptive Plan" }) { Text("Reset fields") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = overwriteByName, onCheckedChange = { overwriteByName = it })
            Text("Overwrite program with same name")
        }

        Text("The generator creates templates for each assignment using your saved speeds, then saves the program and marks it active.")
    }
}
