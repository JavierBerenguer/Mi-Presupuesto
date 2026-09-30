package com.mipatrimonio.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mipatrimonio.app.MiPatrimonioApplication
import com.mipatrimonio.app.data.quotes.QuoteFailure
import java.util.concurrent.TimeUnit

class QuoteRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as MiPatrimonioApplication).container.quotes.refreshAll()
    }.fold(
        onSuccess = { summary ->
            if (summary.failures.containsKey(QuoteFailure.SIN_CONEXION)) Result.retry() else Result.success()
        },
        onFailure = { Result.retry() },
    )

    companion object {
        private const val UNIQUE_WORK = "quote-refresh-daily"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<QuoteRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
