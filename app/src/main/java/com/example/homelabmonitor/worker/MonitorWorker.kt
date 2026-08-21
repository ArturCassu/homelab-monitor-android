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
