package com.alertamayor.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.domain.model.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val mode: AppMode? = null,
    val serverUrl: String = "",
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnPassword: String = "",
    val deviceId: String = "",
    val linkedPeerId: String = "",
    val linkedPeerName: String = "",
    val theme: String = "system",
)

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val coordinator = container.callCoordinator

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val mode = settings.mode.first()
            val url = settings.serverUrl.first()
            val turnUrl = settings.turnUrl.first()
            val turnUsername = settings.turnUsername.first()
            val turnPassword = [REDACTED]
            val id = settings.deviceId.first()
            val linkedId = settings.linkedPeerId.first()
            val linkedName = settings.linkedPeerName.first()
            val theme = settings.theme.first()
            _state.value = SettingsUiState(mode, url, turnUrl, turnUsername, turnPassword, id, linkedId, linkedName, theme)
        }
    }

    fun saveMode(mode: AppMode) {
        viewModelScope.launch {
            settings.saveMode(mode)
            // I-004: aplicar el modo en caliente al coordinator
            coordinator.setMode(mode)
            _state.value = _state.value.copy(mode = mode)
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            settings.saveServerUrl(url)
            // I-004: reconectar la señalización con la nueva URL
            coordinator.setServerUrl(url)
            _state.value = _state.value.copy(serverUrl = url)
        }
    }

    fun saveTurnConfig(url: String, username: String, password: String) {
        viewModelScope.launch {
            settings.saveTurnConfig(url, username, password)
            // Aplicar en caliente al coordinator (igual que setServerUrl/setMode)
            coordinator.setTurnConfig(url, username, password)
            _state.value = _state.value.copy(
                turnUrl = url.trim(),
                turnUsername = username.trim(),
                turnPassword = [REDACTED]
            )
        }
    }

    fun resetLinkAndDeviceId() {
        viewModelScope.launch {
            settings.resetLinkAndDeviceId()
            val id = settings.deviceId.first()
            _state.value = _state.value.copy(
                deviceId = id,
                linkedPeerId = "",
                linkedPeerName = ""
            )
        }
    }

    /** Guarda la preferencia de tema ("system" | "light" | "dark") en DataStore. */
    fun saveTheme(theme: String) {
        viewModelScope.launch {
            settings.saveTheme(theme)
            _state.value = _state.value.copy(theme = theme)
        }
    }
}
