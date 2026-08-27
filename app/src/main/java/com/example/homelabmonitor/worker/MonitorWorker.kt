package com.example.homelabmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.homelabmonitor.data.repository.appContainer
import com.example.homelabmonitor.widget.HomelabWidgetUpdater

class MonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        val settings = container.settingsStore.load()
        if (!settings.setupComplete || settings.endpoint.isBlank() || settings.token.isBlank()) {
            // WorkManager is scheduled at install time, but there is nothing to
            // sync until the user has connected a real homelab in the app.
            return Result.success()
        }
        return container.repository().fetchSnapshot().fold(
            onSuccess = { snapshot ->
                container.snapshotStore.save(snapshot)
                runCatching { HomelabWidgetUpdater.updateAll(applicationContext) }
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "homelab-monitor-periodic-sync"
    }
}
