package app.insidepacer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.insidepacer.BuildConfig
import app.insidepacer.backup.BackupFeatureConfig
import app.insidepacer.backup.ui.BackupSettingsCard
import app.insidepacer.backup.ui.BackupSettingsViewModel
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatSpeed
import app.insidepacer.core.speedUnitLabel
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.Units
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
  val ctx = LocalContext.current
  val repo = remember { SettingsRepo(ctx) }
  val scope = rememberCoroutineScope()

  val voiceEnabled by repo.voiceEnabled.collectAsState(initial = true)
  val preChangeSec by repo.preChangeSeconds.collectAsState(initial = 10)
  val beepEnabled by repo.beepEnabled.collectAsState(initial = true)
  val hapticsEnabled by repo.hapticsEnabled.collectAsState(initial = false)
  val units by repo.units.collectAsState(initial = Units.MPH)
  val debugNotifSubtext by repo.debugShowNotifSubtext.collectAsState(initial = BuildConfig.DEBUG)

  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    if (BackupFeatureConfig.enabled) {
      val backupViewModel: BackupSettingsViewModel = viewModel(factory = BackupSettingsViewModel.Factory(ctx))
      BackupSettingsCard(viewModel = backupViewModel)
    }

    Text("Coach settings", style = MaterialTheme.typography.titleMedium)

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text("Voice prompts")
      Switch(checked = voiceEnabled, onCheckedChange = { on ->
        scope.launch { repo.setVoiceEnabled(on) }
      })
    }

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text("Beep cues")
      Switch(checked = beepEnabled, onCheckedChange = { on ->
        scope.launch { repo.setBeepEnabled(on) }
      })
    }

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text("Haptics")
      Switch(checked = hapticsEnabled, onCheckedChange = { on ->
        scope.launch { repo.setHapticsEnabled(on) }
      })
    }

    Column {
      Text("Pre-change warning: ${formatDuration(preChangeSec)}")
      Slider(
        value = preChangeSec.toFloat(),
        onValueChange = { v -> scope.launch { repo.setPreChangeSeconds(v.toInt()) } },
        valueRange = 3f..30f,
        steps = 27
      )
      Text(
        "You’ll hear voice cues like \"Speed change in ${formatDuration(preChangeSec)} to ${formatSpeed(4.5, units)}\".",
      )
    }

    HorizontalDivider()

    Text("Preferred units", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("Display & export", style = MaterialTheme.typography.bodyLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = units == Units.MPH,
          onClick = { scope.launch { repo.setUnits(Units.MPH) } },
          label = { Text(speedUnitLabel(Units.MPH)) }
        )
        FilterChip(
          selected = units == Units.KMH,
          onClick = { scope.launch { repo.setUnits(Units.KMH) } },
          label = { Text(speedUnitLabel(Units.KMH)) }
        )
      }
    }
    val previewText = remember(units) {
      val sampleSpeedMph = 3.2
      val sampleDuration = 45 * 60 + 10
      "${formatSpeed(sampleSpeedMph, units)} • ${formatDuration(sampleDuration)}"
    }
    Text(previewText, style = MaterialTheme.typography.bodyMedium)

    if (BuildConfig.DEBUG) {
      HorizontalDivider()
      Text("Developer", style = MaterialTheme.typography.titleMedium)
      Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Show diagnostic subtext on notification")
        Switch(checked = debugNotifSubtext, onCheckedChange = { on ->
          scope.launch { repo.setDebugShowNotifSubtext(on) }
        })
      }
    }
  }
}
