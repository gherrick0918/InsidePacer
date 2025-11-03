package app.insidepacer.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.insidepacer.data.ProfileRepo
import app.insidepacer.domain.UserProfile
import app.insidepacer.ui.CM_PER_INCH
import app.insidepacer.ui.KG_PER_POUND
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun ProfileScreen() {
    val ctx = LocalContext.current
    val repo = remember { ProfileRepo(ctx) }
    val scope = rememberCoroutineScope()

    val profile by repo.profile.collectAsState(initial = UserProfile())
    var working by remember(profile) { mutableStateOf(profile) }
    var status by remember { mutableStateOf<String?>(null) }

    // Text state holders for the UI
    var ageText by remember { mutableStateOf("") }
    var feetText by remember { mutableStateOf("") }
    var inchesText by remember { mutableStateOf("") }
    var heightCmText by remember { mutableStateOf("") }
    var weightLbsText by remember { mutableStateOf("") }
    var weightKgText by remember { mutableStateOf("") }
    var targetWeightLbsText by remember { mutableStateOf("") }
    var targetWeightKgText by remember { mutableStateOf("") }
    var preferredDaysPerWeekText by remember { mutableStateOf("") }
    var sessionMinMinText by remember { mutableStateOf("") }
    var sessionMaxMinText by remember { mutableStateOf("") }

    // This effect synchronizes the UI text state from the 'working' model.
    // It runs on initial load and whenever 'working' is changed (e.g. by switching units).
    LaunchedEffect(working) {
        ageText = working.age.toString()

        val totalInches = working.heightCm / CM_PER_INCH
        feetText = (totalInches / 12).toInt().toString()
        inchesText = (totalInches % 12).roundToInt().toString()
        heightCmText = working.heightCm.toString()

        weightLbsText = String.format(Locale.US, "%.1f", working.weightKg / KG_PER_POUND)
        weightKgText = String.format(Locale.US, "%.1f", working.weightKg)

        targetWeightLbsText = working.targetWeightKg?.let { String.format(Locale.US, "%.1f", it / KG_PER_POUND) } ?: ""
        targetWeightKgText = working.targetWeightKg?.let { String.format(Locale.US, "%.1f", it) } ?: ""

        preferredDaysPerWeekText = working.preferredDaysPerWeek.toString()
        sessionMinMinText = working.sessionMinMin.toString()
        sessionMaxMinText = working.sessionMaxMin.toString()
    }

    val heightMeters = working.heightCm / 100.0
    val bmi = if (heightMeters > 0) working.weightKg / heightMeters.pow(2.0) else 0.0
    val useImperial = working.units.equals("mph", ignoreCase = true)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("User profile", style = MaterialTheme.typography.titleMedium)
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp)
            )
            if (useImperial) {
                OutlinedTextField(
                    value = feetText,
                    onValueChange = { feetText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Height (ft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = inchesText,
                    onValueChange = { inchesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("in") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            } else {
                OutlinedTextField(
                    value = heightCmText,
                    onValueChange = { heightCmText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (useImperial) {
                OutlinedTextField(
                    value = weightLbsText,
                    onValueChange = { weightLbsText = it },
                    label = { Text("Weight (lbs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            } else {
                OutlinedTextField(
                    value = weightKgText,
                    onValueChange = { weightKgText = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (useImperial) {
                OutlinedTextField(
                    value = targetWeightLbsText,
                    onValueChange = { targetWeightLbsText = it },
                    label = { Text("Target weight (lbs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            } else {
                OutlinedTextField(
                    value = targetWeightKgText,
                    onValueChange = { targetWeightKgText = it },
                    label = { Text("Target weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = preferredDaysPerWeekText,
                onValueChange = { preferredDaysPerWeekText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Days per week") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = sessionMinMinText,
                onValueChange = { sessionMinMinText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Session min (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )
            OutlinedTextField(
                value = sessionMaxMinText,
                onValueChange = { sessionMaxMinText = it.filter { ch -> ch.isDigit() } },
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

        Text("Units")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = useImperial,
                onClick = { working = working.copy(units = "mph") },
                label = { Text("mph / lbs / ft") }
            )
            FilterChip(
                selected = !useImperial,
                onClick = { working = working.copy(units = "kmh") },
                label = { Text("kmh / kg / cm") }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start date: ${LocalDate.ofEpochDay(working.startEpochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)}")
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
                try {
                    val age = ageText.toIntOrNull()?.coerceIn(10, 99) ?: working.age
                    val heightCm = if (useImperial) {
                        val feet = feetText.toIntOrNull() ?: 0
                        val inches = inchesText.toIntOrNull() ?: 0
                        ((feet * 12 + inches) * CM_PER_INCH).roundToInt()
                    } else {
                        heightCmText.toIntOrNull()?.coerceIn(120, 220) ?: working.heightCm
                    }
                    val weightKg = if (useImperial) {
                        (weightLbsText.toDoubleOrNull() ?: 0.0) * KG_PER_POUND
                    } else {
                        weightKgText.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: working.weightKg
                    }
                    val targetWeightKg = if (useImperial) {
                        targetWeightLbsText.toDoubleOrNull()?.let { it * KG_PER_POUND }
                    } else {
                        targetWeightKgText.toDoubleOrNull()
                    }
                    val preferredDaysPerWeek = preferredDaysPerWeekText.toIntOrNull()?.coerceIn(2, 7) ?: working.preferredDaysPerWeek
                    val sessionMinMin = sessionMinMinText.toIntOrNull()?.coerceIn(10, sessionMaxMinText.toIntOrNull() ?: working.sessionMaxMin) ?: working.sessionMinMin
                    val sessionMaxMin = sessionMaxMinText.toIntOrNull()?.let { maxOf(it, sessionMinMin) } ?: working.sessionMaxMin

                    val updatedProfile = working.copy(
                        age = age,
                        heightCm = heightCm,
                        weightKg = weightKg,
                        targetWeightKg = targetWeightKg,
                        preferredDaysPerWeek = preferredDaysPerWeek,
                        sessionMinMin = sessionMinMin,
                        sessionMaxMin = sessionMaxMin
                    )
                    repo.save(updatedProfile)
                    working = updatedProfile
                    status = "Profile saved"
                } catch (e: Exception) {
                    status = "Error: ${e.message}"
                }
            }
        }) { Text("Save profile") }
    }
}
