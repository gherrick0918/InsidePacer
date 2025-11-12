package app.insidepacer.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import com.insidepacer.health.HEALTH_CONNECT_PACKAGE_NAME
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import com.insidepacer.health.HealthConnectRepoImpl
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun SettingsScreen(
    healthConnectRepo: HealthConnectRepo = HealthConnectRepoImpl(),
    showDeveloperOptions: Boolean = BuildConfig.DEBUG,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val activity = context as? ComponentActivity
    val repo = remember { SettingsRepo(context) }
    val scope = rememberCoroutineScope()

    val voiceEnabled by repo.voiceEnabled.collectAsState(initial = true)
    val preChangeSec by repo.preChangeSeconds.collectAsState(initial = 10)
    val beepEnabled by repo.beepEnabled.collectAsState(initial = true)
    val hapticsEnabled by repo.hapticsEnabled.collectAsState(initial = false)
    val units by repo.units.collectAsState(initial = Units.MPH)
    val debugNotifSubtext by repo.debugShowNotifSubtext.collectAsState(initial = BuildConfig.DEBUG)

    val healthConnectViewModel: HealthConnectSettingsViewModel = viewModel(
        factory = HealthConnectSettingsViewModel.Factory(
            context = context,
            settingsRepo = repo,
            healthConnectRepo = healthConnectRepo,
        )
    )
    val healthConnectState by healthConnectViewModel.uiState.collectAsState()

    LaunchedEffect(healthConnectViewModel) {
        healthConnectViewModel.events.collect { event ->
            if (event is HealthConnectUiEvent.Message) {
                Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Settings") })
        }
    ) { innerPadding ->
        val navigationPadding = WindowInsets.navigationBars.asPaddingValues()
        val startPadding = max(
            innerPadding.calculateStartPadding(layoutDirection).value,
            navigationPadding.calculateStartPadding(layoutDirection).value
        ).dp
        val endPadding = max(
            innerPadding.calculateEndPadding(layoutDirection).value,
            navigationPadding.calculateEndPadding(layoutDirection).value
        ).dp
        val bottomPadding = max(
            innerPadding.calculateBottomPadding().value,
            navigationPadding.calculateBottomPadding().value
        ).dp
        val topPadding = (innerPadding.calculateTopPadding() - 12.dp).coerceAtLeast(0.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = startPadding,
                top = topPadding,
                end = endPadding,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (BackupFeatureConfig.enabled) {
                item(key = "backup") {
                    CardContainer {
                        val backupViewModel: BackupSettingsViewModel = viewModel(
                            factory = BackupSettingsViewModel.Factory(context)
                        )
                        BackupSettingsCard(viewModel = backupViewModel)
                    }
                }
            }

            item(key = "coach") {
                CardContainer {
                    CoachSettingsCard(
                        voiceEnabled = voiceEnabled,
                        onVoiceChanged = { on -> scope.launch { repo.setVoiceEnabled(on) } },
                        beepEnabled = beepEnabled,
                        onBeepChanged = { on -> scope.launch { repo.setBeepEnabled(on) } },
                        hapticsEnabled = hapticsEnabled,
                        onHapticsChanged = { on -> scope.launch { repo.setHapticsEnabled(on) } },
                        preChangeSeconds = preChangeSec,
                        onPreChangeChanged = { sec -> scope.launch { repo.setPreChangeSeconds(sec) } },
                        units = units,
                        onUnitsChanged = { unit -> scope.launch { repo.setUnits(unit) } },
                    )
                }
            }

            item(key = "integrations") {
                CardContainer {
                    IntegrationsCard(
                        state = healthConnectState,
                        onToggleChanged = { enabled ->
                            healthConnectViewModel.setEnabled(enabled, activity)
                        },
                        onGrantPermission = {
                            if (healthConnectState.availability == HcAvailability.SUPPORTED_NOT_INSTALLED) {
                                healthConnectViewModel.ensureInstalled()
                            } else {
                                healthConnectViewModel.requestPermission(activity)
                            }
                        },
                        onOpenHealthConnect = {
                            openHealthConnect(context)
                        }
                    )
                }
            }

            if (showDeveloperOptions) {
                item(key = "developer") {
                    CardContainer(modifier = Modifier.testTag("developerCard")) {
                        DeveloperCard(
                            debugNotifSubtext = debugNotifSubtext,
                            onDebugNotifChanged = { on -> scope.launch { repo.setDebugShowNotifSubtext(on) } },
                            onSimulateHealthConnect = {
                                scope.launch {
                                    val result = healthConnectViewModel.simulateTestWrite()
                                    val message = result.fold(
                                        onSuccess = { "Simulated Health Connect write completed." },
                                        onFailure = { it.message ?: "Health Connect write failed." }
                                    )
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun CoachSettingsCard(
    voiceEnabled: Boolean,
    onVoiceChanged: (Boolean) -> Unit,
    beepEnabled: Boolean,
    onBeepChanged: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    preChangeSeconds: Int,
    onPreChangeChanged: (Int) -> Unit,
    units: Units,
    onUnitsChanged: (Units) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Coach settings", style = MaterialTheme.typography.titleMedium)

        SettingsSwitchRow(label = "Voice prompts", checked = voiceEnabled, onCheckedChange = onVoiceChanged)
        SettingsSwitchRow(label = "Beep cues", checked = beepEnabled, onCheckedChange = onBeepChanged)
        SettingsSwitchRow(label = "Haptics", checked = hapticsEnabled, onCheckedChange = onHapticsChanged)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pre-change warning: ${formatDuration(preChangeSeconds)}")
            Slider(
                value = preChangeSeconds.toFloat(),
                onValueChange = { v -> onPreChangeChanged(v.toInt()) },
                valueRange = 3f..30f,
                steps = 27,
            )
            Text("You’ll hear voice cues like \"Speed change in ${formatDuration(preChangeSeconds)} to ${formatSpeed(4.5, units)}\".")
        }

        HorizontalDivider()

        Text("Preferred units", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Display & export", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = units == Units.MPH,
                    onClick = { onUnitsChanged(Units.MPH) },
                    label = { Text(speedUnitLabel(Units.MPH)) }
                )
                androidx.compose.material3.FilterChip(
                    selected = units == Units.KMH,
                    onClick = { onUnitsChanged(Units.KMH) },
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
    }
}

@Composable
private fun IntegrationsCard(
    state: HealthConnectUiState,
    onToggleChanged: (Boolean) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Integrations", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Health Connect")
            Switch(checked = state.enabled, onCheckedChange = onToggleChanged)
        }

        val statusText = when (state.availability) {
            HcAvailability.SUPPORTED_INSTALLED -> if (state.permissionGranted) {
                "Installed • Permission granted"
            } else {
                "Installed • Needs permission"
            }
            HcAvailability.SUPPORTED_NOT_INSTALLED -> "Not installed"
            HcAvailability.NOT_SUPPORTED -> "Not supported"
        }
        Text(statusText, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onGrantPermission,
                enabled = state.enabled && state.availability != HcAvailability.NOT_SUPPORTED &&
                    (!state.permissionGranted || state.availability == HcAvailability.SUPPORTED_NOT_INSTALLED)
            ) {
                Text("Grant permission")
            }
            Button(
                onClick = onOpenHealthConnect,
                enabled = state.availability != HcAvailability.NOT_SUPPORTED
            ) {
                Text("Open Health Connect")
            }
        }
    }
}

@Composable
private fun DeveloperCard(
    debugNotifSubtext: Boolean,
    onDebugNotifChanged: (Boolean) -> Unit,
    onSimulateHealthConnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Developer", style = MaterialTheme.typography.titleMedium)
        SettingsSwitchRow(
            label = "Show diagnostic subtext on notification",
            checked = debugNotifSubtext,
            onCheckedChange = onDebugNotifChanged
        )
        Button(onClick = onSimulateHealthConnect) {
            Text("Simulate HC write (10s)")
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openHealthConnect(context: Context) {
    val packageManager = context.packageManager
    val packageName = HEALTH_CONNECT_PACKAGE_NAME
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        context.startActivity(launchIntent)
        return
    }
    val webIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(webIntent)
}
