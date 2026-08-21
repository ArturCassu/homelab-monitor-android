package com.example.homelabmonitor.data.repository

import android.content.Context

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore = SecureSettingsStore(appContext)
    val snapshotStore = SnapshotStore(appContext)
    val repositoryFactory = RepositoryFactory(settingsStore)

    fun repository(): MonitorRepository = repositoryFactory.create()
}
