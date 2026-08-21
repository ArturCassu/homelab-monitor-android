package com.example.homelabmonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.homelabmonitor.data.model.AppSettings
import com.example.homelabmonitor.data.model.MockSnapshotFactory
import com.example.homelabmonitor.data.model.MonitorUiState
import com.example.homelabmonitor.data.repository.appContainer
import com.example.homelabmonitor.widget.HomelabWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer()
    private val _uiState = MutableStateFlow(
        MonitorUiState(
            snapshot = container.snapshotStore.read() ?: MockSnapshotFactory.create(),
            settings = container.settingsStore.load(),
        ),
    )
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    init {
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
}
