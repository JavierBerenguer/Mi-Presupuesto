package com.mipatrimonio.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.work.RecurringReminderScheduler
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Inyección de dependencias manual: suficiente para el tamaño actual y sin magia de generación de código. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    private val notificationPreferences = context.applicationContext.getSharedPreferences(
        "notification_diagnostics",
        Context.MODE_PRIVATE,
    )
    val ledger = LedgerRepository(database)
    val investments = InvestmentRepository(database)
    val settings = SettingsRepository(context.applicationContext)
    val recurring = RecurringRepository(database)
    val recurringReminderScheduler = RecurringReminderScheduler(context.applicationContext, recurring)
    val notifications = NotificationRepository(
        database,
        NotificationEngine(listOf(GenericSpanishParser())),
        initialDiagnosticTextEnabled = notificationPreferences.getBoolean(DIAGNOSTIC_TEXT_KEY, false),
        persistDiagnosticTextEnabled = { enabled ->
            notificationPreferences.edit().putBoolean(DIAGNOSTIC_TEXT_KEY, enabled).apply()
        },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { ledger.seedDefaultCategoriesIfEmpty() }
        recurringReminderScheduler.createChannel()
        RecurringReminderScheduler.enqueuePeriodic(context.applicationContext)
        scope.launch {
            recurring.generatePending(java.time.LocalDate.now())
            recurringReminderScheduler.scheduleAll()
        }
    }

    private companion object {
        const val DIAGNOSTIC_TEXT_KEY = "diagnostic_text_enabled"
    }
}

class MiPatrimonioApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
