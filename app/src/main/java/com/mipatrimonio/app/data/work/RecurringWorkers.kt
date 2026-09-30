package com.mipatrimonio.app.data.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mipatrimonio.app.MainActivity
import com.mipatrimonio.app.MiPatrimonioApplication
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.domain.calc.RecurringGenerator
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class RecurringGenerationWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val container = (applicationContext as MiPatrimonioApplication).container
        container.recurring.generatePending(RecurringRepository.generationLimit(LocalDate.now()))
        container.recurringReminderScheduler.scheduleAll()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

class RecurringReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val ruleId = inputData.getString(KEY_RULE_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(
            (ruleId + inputData.getLong(KEY_DATE, 0L)).hashCode(),
            notification,
        )
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "recurring_reminders"
        const val KEY_RULE_ID = "rule_id"
        const val KEY_DATE = "date"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}

class RecurringReminderScheduler(
    private val context: Context,
    private val repository: RecurringRepository,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RecurringReminderWorker.CHANNEL_ID,
                context.getString(R.string.recurring_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.recurring_notification_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    suspend fun scheduleAll() {
        repository.rules.first().forEach { schedule(it) }
    }

    fun cancel(ruleId: String) {
        workManager.cancelAllWorkByTag(tag(ruleId))
    }

    fun schedule(rule: RecurringRule) {
        cancel(rule.id)
        if (rule.archived || rule.reminder == ReminderOption.NO) return
        val current = now()
        val today = current.toLocalDate()
        val reminderLead = if (rule.reminder == ReminderOption.PERSONALIZADO) {
            rule.reminderCustomDays?.coerceAtLeast(0) ?: 0
        } else {
            2
        }
        val horizon = today.plusDays(62L + reminderLead)
        val occurrences = RecurringGenerator.pendingDates(
            rule.copy(lastGeneratedDate = today.minusDays(1), archived = false),
            horizon,
        )
        occurrences.forEach { occurrence ->
            val reminderDate = RecurringGenerator.reminderDate(
                occurrence,
                rule.reminder,
                rule.reminderCustomDays,
            ) ?: return@forEach
            if (reminderDate.isBefore(today)) return@forEach
            val preferred = reminderDate.atTime(9, 0).atZone(current.zone)
            val scheduled = if (preferred.isAfter(current)) preferred else current
            val body = rule.description.ifBlank { context.getString(R.string.recurring_notification_generic_body) }
            val data = Data.Builder()
                .putString(RecurringReminderWorker.KEY_RULE_ID, rule.id)
                .putLong(RecurringReminderWorker.KEY_DATE, occurrence.toEpochDay())
                .putString(RecurringReminderWorker.KEY_TITLE, context.getString(R.string.recurring_notification_title))
                .putString(RecurringReminderWorker.KEY_BODY, body)
                .build()
            val delay = Duration.between(current, scheduled).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(tag(rule.id))
                .build()
            workManager.enqueueUniqueWork(
                "recurring-reminder-${rule.id}-${occurrence.toEpochDay()}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    companion object {
        private const val PERIODIC_WORK = "recurring-generation-daily"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringGenerationWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun tag(ruleId: String) = "recurring-rule-$ruleId"
    }
}
