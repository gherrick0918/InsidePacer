package app.insidepacer.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.insidepacer.data.SettingsRepo
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class HealthConnectUiState(
    val enabled: Boolean = false,
    val availability: HcAvailability = HcAvailability.NOT_SUPPORTED,
    val permissionGranted: Boolean = false,
)

internal sealed interface HealthConnectUiEvent {
    data class Message(val text: String) : HealthConnectUiEvent
}

internal class HealthConnectSettingsViewModel(
    private val appContext: Context,
    private val settingsRepo: SettingsRepo,
    private val healthConnectRepo: HealthConnectRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HealthConnectUiEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepo.healthConnectEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(enabled = enabled) }
                if (enabled) {
                    refreshStatusInternal()
                } else {
                    _uiState.update { state -> state.copy(permissionGranted = false) }
                }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch { refreshStatusInternal() }
    }

    fun setEnabled(enabled: Boolean, activity: ComponentActivity?) {
        viewModelScope.launch {
            if (!enabled) {
                settingsRepo.setHealthConnectEnabled(false)
                _uiState.update { it.copy(permissionGranted = false) }
                return@launch
            }

            val availability = healthConnectRepo.availability(appContext)
            if (availability == HcAvailability.NOT_SUPPORTED) {
                emitMessage("Health Connect is not supported on this device.")
                settingsRepo.setHealthConnectEnabled(false)
                _uiState.update { it.copy(enabled = false, availability = availability, permissionGranted = false) }
                return@launch
            }

            settingsRepo.setHealthConnectEnabled(true)
            _uiState.update { it.copy(availability = availability) }

            if (availability != HcAvailability.SUPPORTED_INSTALLED) {
                healthConnectRepo.ensureInstalled(appContext)
                refreshStatusInternal()
                return@launch
            }

            if (activity == null) {
                emitMessage("Unable to request permissions without an activity context.")
                refreshStatusInternal()
                return@launch
            }

            val granted = healthConnectRepo.requestWritePermission(activity)
            refreshStatusInternal()
            if (!granted) {
                emitMessage("Health Connect permission was not granted.")
            }
        }
    }

    fun ensureInstalled() {
        viewModelScope.launch {
            healthConnectRepo.ensureInstalled(appContext)
            refreshStatusInternal()
        }
    }

    fun requestPermission(activity: ComponentActivity?) {
        viewModelScope.launch {
            if (activity == null) {
                emitMessage("Unable to request permissions without an activity context.")
                return@launch
            }
            val availability = healthConnectRepo.availability(appContext)
            if (availability != HcAvailability.SUPPORTED_INSTALLED) {
                emitMessage("Install Health Connect to continue.")
                refreshStatusInternal()
                return@launch
            }
            val granted = healthConnectRepo.requestWritePermission(activity)
            refreshStatusInternal()
            if (!granted) {
                emitMessage("Health Connect permission was not granted.")
            }
        }
    }

    suspend fun simulateTestWrite(): Result<Unit> {
        refreshStatusInternal()
        val state = uiState.value
        if (!state.enabled) {
            return Result.failure(IllegalStateException("Enable Health Connect first."))
        }
        if (state.availability != HcAvailability.SUPPORTED_INSTALLED) {
            return Result.failure(IllegalStateException("Health Connect is not installed."))
        }
        val hasPermission = healthConnectRepo.hasWritePermission(appContext)
        if (!hasPermission) {
            return Result.failure(IllegalStateException("Grant Health Connect permission first."))
        }
        val end = Instant.now()
        val start = end.minusSeconds(10)
        return healthConnectRepo.writeWalkingSession(
            context = appContext,
            startTime = start,
            endTime = end,
            notes = "Debug test write",
        )
    }

    private suspend fun refreshStatusInternal() {
        val availability = healthConnectRepo.availability(appContext)
        val permissionGranted = availability == HcAvailability.SUPPORTED_INSTALLED &&
            healthConnectRepo.hasWritePermission(appContext)
        _uiState.update { it.copy(availability = availability, permissionGranted = permissionGranted) }
    }

    private suspend fun emitMessage(text: String) {
        _events.emit(HealthConnectUiEvent.Message(text))
    }

    class Factory(
        private val context: Context,
        private val settingsRepo: SettingsRepo,
        private val healthConnectRepo: HealthConnectRepo,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HealthConnectSettingsViewModel::class.java)) {
                return HealthConnectSettingsViewModel(
                    appContext = context.applicationContext,
                    settingsRepo = settingsRepo,
                    healthConnectRepo = healthConnectRepo,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
