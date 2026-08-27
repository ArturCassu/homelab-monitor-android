package com.example.homelabmonitor.data.repository

import com.example.homelabmonitor.data.model.AppSettings

class RepositoryFactory(private val settingsStore: SecureSettingsStore) {
    fun create(): MonitorRepository {
        val settings = settingsStore.load()
        return when {
            settings.endpoint.isBlank() -> ConfigurationErrorRepository("Configure o endpoint da API.")
            settings.token.isBlank() -> ConfigurationErrorRepository("Configure o token da API.")
            else -> HttpMonitorRepository(settings)
        }
    }

    fun create(settings: AppSettings): MonitorRepository = when {
        settings.endpoint.isBlank() -> ConfigurationErrorRepository("Configure o endpoint da API.")
        settings.token.isBlank() -> ConfigurationErrorRepository("Configure o token da API.")
        else -> HttpMonitorRepository(settings)
    }
}
