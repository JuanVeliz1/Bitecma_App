package com.bitecma.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.utils.NetworkMonitor

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AppState.loadSession(applicationContext)
        DataManager.loadCache(applicationContext)
        AppState.hasNetwork = NetworkMonitor.isConnected(applicationContext)

        if (AppState.forceOffline) return Result.success()
        if (!AppState.hasNetwork) return Result.retry()

        if (AppState.authToken.isNullOrBlank()) {
            runCatching { DataManager.ensureAuthenticatedOnlineSession(applicationContext) }
            return Result.success()
        }

        val synced = runCatching { DataManager.syncAllFromServer(applicationContext) }.getOrDefault(false)
        return if (synced) Result.success() else Result.retry()
    }
}
