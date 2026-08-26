package com.example.homelabmonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.homelabmonitor.data.model.AppSettings
import com.example.homelabmonitor.data.model.MockSnapshotFactory
import com.example.homelabmonitor.data.model.MonitorUiState
import com.example.homelabmonitor.data.repository.EndpointConfig
import com.example.homelabmonitor.data.repository.userFacingConnectionError
import com.example.homelabmonitor.data.repository.appContainer
import com.example.homelabmonitor.update.AppUpdateRepository
import com.example.homelabmonitor.update.AppUpdateState
import com.example.homelabmonitor.widget.HomelabWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer()
    private val updateRepository = AppUpdateRepository(application)
    private val _uiState = MutableStateFlow(
        MonitorUiState(
            snapshot = container.snapshotStore.read() ?: MockSnapshotFactory.create(),
            settings = container.settingsStore.load(),
        ),
    )
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()
    private val _updateState = MutableStateFlow(updateRepository.currentState())
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    init {
        updateRepository.cleanupCache()
        if (_uiState.value.settings.setupComplete) refresh()
    }

    fun connect(endpointInput: String, tokenInput: String) {
        val parsed = EndpointConfig.parse(endpointInput)
        if (parsed.isFailure) {
            _uiState.update { it.copy(setupError = parsed.exceptionOrNull()?.message ?: "Host inválido.") }
            return
        }
        val token = tokenInput.trim()
        if (token.isBlank()) {
            _uiState.update { it.copy(setupError = "Informe o token emitido pelo agente do homelab.") }
            return
        }

        val pendingSettings = AppSettings(
            endpoint = parsed.getOrThrow().baseUrl,
            token = token,
            useMockData = false,
            setupComplete = false,
        )
        _uiState.update { it.copy(isConnecting = true, setupError = null) }
        viewModelScope.launch {
            container.repositoryFactory.create(pendingSettings).fetchSnapshot()
                .onSuccess { snapshot ->
                    val savedSettings = pendingSettings.copy(setupComplete = true)
                    container.settingsStore.save(savedSettings)
                    container.snapshotStore.save(snapshot)
                    _uiState.update {
                        it.copy(
                            settings = savedSettings,
                            snapshot = snapshot,
                            isConnecting = false,
                            setupError = null,
                            lastError = null,
                        )
                    }
                    runCatching { HomelabWidgetUpdater.updateAll(getApplication()) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isConnecting = false, setupError = userFacingConnectionError(throwable))
                    }
                }
        }
    }

    fun enterDemo() {
        val settings = AppSettings(useMockData = true, setupComplete = true)
        val snapshot = MockSnapshotFactory.create()
        container.settingsStore.save(settings)
        container.snapshotStore.save(snapshot)
        _uiState.update {
            it.copy(settings = settings, snapshot = snapshot, setupError = null, lastError = null)
        }
        viewModelScope.launch { runCatching { HomelabWidgetUpdater.updateAll(getApplication()) } }
    }

    fun changeServer() {
        val settings = _uiState.value.settings.copy(setupComplete = false)
        container.settingsStore.save(settings)
        _uiState.update { it.copy(settings = settings, setupError = null) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, lastError = null) }
            val result = container.repository().fetchSnapshot()
            result.onSuccess { snapshot ->
                container.snapshotStore.save(snapshot)
                _uiState.update {
                    it.copy(snapshot = snapshot, isRefreshing = false, lastError = null)
                }
                runCatching { HomelabWidgetUpdater.updateAll(getApplication()) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        lastError = userFacingConnectionError(throwable),
                    )
                }
            }
        }
    }

    fun saveSettings(endpoint: String, token: String, useMockData: Boolean) {
        val normalizedEndpoint = if (useMockData && endpoint.isBlank()) {
            ""
        } else {
            val parsed = EndpointConfig.parse(endpoint)
            if (parsed.isFailure) {
                _uiState.update {
                    it.copy(lastError = parsed.exceptionOrNull()?.message ?: "Host inválido.")
                }
                return
            }
            parsed.getOrThrow().baseUrl
        }
        if (!useMockData && token.isBlank()) {
            _uiState.update { it.copy(lastError = "Informe o token da API antes de desativar o modo demonstração.") }
            return
        }
        val settings = AppSettings(
            endpoint = normalizedEndpoint,
            token = token.trim(),
            useMockData = useMockData,
            setupComplete = true,
        )
        container.settingsStore.save(settings)
        _uiState.update { it.copy(settings = settings, lastError = null) }
        refresh()
    }

    fun checkForAppUpdate() {
        if (_updateState.value.isChecking || _updateState.value.isDownloading) return
        viewModelScope.launch {
            _updateState.update {
                it.copy(isChecking = true, available = null, message = null, error = null)
            }
            updateRepository.fetchLatest()
                .onSuccess { manifest ->
                    val current = _updateState.value.currentVersionCode
                    _updateState.update {
                        if (manifest.versionCode > current) {
                            it.copy(isChecking = false, available = manifest, message = null)
                        } else {
                            it.copy(
                                isChecking = false,
                                available = null,
                                message = "Você já está na versão ${it.currentVersionName}.",
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _updateState.update {
                        it.copy(
                            isChecking = false,
                            error = throwable.message ?: "Não foi possível verificar atualizações.",
                        )
                    }
                }
        }
    }

    fun downloadAndPrepareUpdate() {
        val manifest = _updateState.value.available ?: return
        if (_updateState.value.isDownloading) return
        viewModelScope.launch {
            _updateState.update { it.copy(isDownloading = true, error = null, message = null) }
            updateRepository.downloadAndPrepare(manifest)
                .onSuccess { uri ->
                    _updateState.update { it.copy(isDownloading = false, installerUri = uri) }
                }
                .onFailure { throwable ->
                    _updateState.update {
                        it.copy(
                            isDownloading = false,
                            error = throwable.message ?: "Não foi possível preparar a atualização.",
                        )
                    }
                }
        }
    }

    fun clearInstallerUri() {
        _updateState.update { it.copy(installerUri = null) }
    }

    fun reportUpdateError(message: String) {
        _updateState.update { it.copy(error = message) }
    }
}
