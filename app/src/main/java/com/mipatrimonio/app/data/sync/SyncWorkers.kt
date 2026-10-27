package com.mipatrimonio.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mipatrimonio.app.MiPatrimonioApplication
import java.util.concurrent.TimeUnit

class SyncOutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as? MiPatrimonioApplication)?.container?.sync ?: return Result.failure()
        if (!repository.hasSession()) return Result.success()
        return try {
            when (repository.processNextOutbox()) {
                OutboxResult.EMPTY, OutboxResult.BLOCKED -> Result.success()
                OutboxResult.SENT -> { SyncScheduler.enqueueOutbox(applicationContext); Result.success() }
                OutboxResult.RETRY -> Result.retry()
            }
        } catch (_: SyncSessionExpiredException) { Result.failure() }
    }
}

class PeriodicSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as? MiPatrimonioApplication)?.container?.sync ?: return Result.failure()
        if (!repository.hasSession()) return Result.success()
        return try { repository.synchronize(); Result.success() }
        catch (_: SyncSessionExpiredException) { Result.failure() }
        catch (_: Exception) { Result.retry() }
    }
}

object SyncScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enqueueOutbox(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncOutboxWorker>().setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_outbox", ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueAccountPublish(context: Context) {
        val request = OneTimeWorkRequestBuilder<PeriodicSyncWorker>().setInitialDelay(30, TimeUnit.SECONDS)
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_accounts_debounce", ExistingWorkPolicy.REPLACE, request)
    }

    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(1, TimeUnit.HOURS).setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync_periodic", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        val opening = OneTimeWorkRequestBuilder<PeriodicSyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_on_open", ExistingWorkPolicy.REPLACE, opening)
    }
}
