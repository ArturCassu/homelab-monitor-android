package com.example.homelabmonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.homelabmonitor.data.model.AppSettings
import com.example.homelabmonitor.data.model.MockSnapshotFactory
import com.example.homelabmonitor.data.model.MonitorUiState
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
        refresh()
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
                        lastError = throwable.message ?: "Não foi possível atualizar o homelab.",
                    )
                }
            }
        }
    }

    fun saveSettings(endpoint: String, token: String, useMockData: Boolean) {
        val settings = AppSettings(
            endpoint = endpoint.trim(),
            token = token,
            useMockData = useMockData,
        )
        container.settingsStore.save(settings)
        _uiState.update { it.copy(settings = settings) }
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
