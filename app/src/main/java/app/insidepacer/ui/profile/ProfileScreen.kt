package app.insidepacer.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.insidepacer.data.ProfileRepo
import app.insidepacer.domain.UserProfile
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.pow

@Composable
fun ProfileScreen() {
    val ctx = LocalContext.current
    val repo = remember { ProfileRepo(ctx) }
    val scope = rememberCoroutineScope()

    val profile by repo.profile.collectAsState(initial = UserProfile())
    var working by remember(profile) { mutableStateOf(profile) }
    var status by remember { mutableStateOf<String?>(null) }

    val heightMeters = working.heightCm / 100.0
    val bmi = if (heightMeters > 0) working.weightKg / heightMeters.pow(2.0) else 0.0

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("User profile", style = MaterialTheme.typography.titleMedium)
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = working.age.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { working = working.copy(age = it.coerceIn(10, 99)) } },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp)
            )
            OutlinedTextField(
                value = working.heightCm.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { working = working.copy(heightCm = it.coerceIn(120, 220)) } },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(150.dp)
            )
            OutlinedTextField(
                value = String.format(Locale.getDefault(), "%.1f", working.weightKg),
                onValueChange = { value -> value.toDoubleOrNull()?.let { working = working.copy(weightKg = it.coerceIn(30.0, 250.0)) } },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = working.targetWeightKg?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "",
                onValueChange = { value ->
                    working = working.copy(targetWeightKg = value.toDoubleOrNull())
                },
                label = { Text("Target weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = working.preferredDaysPerWeek.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { working = working.copy(preferredDaysPerWeek = it.coerceIn(2, 7)) } },
                label = { Text("Days per week") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = working.sessionMinMin.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { working = working.copy(sessionMinMin = it.coerceIn(10, working.sessionMaxMin)) } },
                label = { Text("Session min (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )
            OutlinedTextField(
                value = working.sessionMaxMin.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { working = working.copy(sessionMaxMin = maxOf(it, working.sessionMinMin)) } },
                label = { Text("Session max (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )
        }

        Text("Level")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = working.level.equals("Beginner", ignoreCase = true),
                onClick = { working = working.copy(level = "Beginner") },
                label = { Text("Beginner") }
            )
            FilterChip(
                selected = working.level.equals("Intermediate", ignoreCase = true),
                onClick = { working = working.copy(level = "Intermediate") },
                label = { Text("Intermediate") }
            )
        }

        Text("Units: ${working.units} (display only)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = working.units.equals("mph", ignoreCase = true),
                onClick = { working = working.copy(units = "mph") },
                label = { Text("mph") }
            )
            FilterChip(
                selected = working.units.equals("kmh", ignoreCase = true),
                onClick = { working = working.copy(units = "kmh") },
                label = { Text("km/h") }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start epoch day: ${working.startEpochDay}")
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = {
                val today = LocalDate.now().toEpochDay()
                working = working.copy(startEpochDay = today)
            }) { Text("Use today") }
        }

        Text(String.format(Locale.getDefault(), "BMI: %.1f", bmi))

        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                repo.save(working)
                status = "Profile saved"
            }
        }) { Text("Save profile") }
    }
}
