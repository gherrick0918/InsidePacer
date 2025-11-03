package app.insidepacer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.insidepacer.data.SettingsRepo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
  val ctx = LocalContext.current
  val repo = remember { SettingsRepo(ctx) }
  val scope = rememberCoroutineScope()

  val voiceEnabled by repo.voiceEnabled.collectAsState(initial = true)
  val preChangeSec by repo.preChangeSeconds.collectAsState(initial = 10)

  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Coach settings", style = MaterialTheme.typography.titleMedium)

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text("Voice prompts")
      Switch(checked = voiceEnabled, onCheckedChange = { on ->
        scope.launch { repo.setVoiceEnabled(on) }
      })
    }

    Column {
      Text("Pre-change warning: $preChangeSec s")
      Slider(
        value = preChangeSec.toFloat(),
        onValueChange = { v -> scope.launch { repo.setPreChangeSeconds(v.toInt()) } },
        valueRange = 3f..30f,
        steps = 27
      )
      Text("You’ll hear voice cues like \"Speed change in 10 seconds to 5.5\" and \"Change speed now to 5.5\".")
    }
  }
}
