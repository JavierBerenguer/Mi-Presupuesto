package com.mipatrimonio.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.backup.BackupFileStore
import com.mipatrimonio.app.data.backup.BackupRepository
import com.mipatrimonio.app.data.backup.BackupService
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.work.RecurringReminderScheduler
import com.mipatrimonio.app.data.work.QuoteRefreshWorker
import com.mipatrimonio.app.data.quotes.CoinGeckoQuoteProvider
import com.mipatrimonio.app.data.quotes.CoinGeckoSearchService
import com.mipatrimonio.app.data.quotes.KeystoreSecretStore
import com.mipatrimonio.app.data.quotes.QuoteRepository
import com.mipatrimonio.app.data.quotes.TwelveDataQuoteProvider
import com.mipatrimonio.app.data.quotes.TwelveDataAssetService
import com.mipatrimonio.app.data.quotes.OpenFigiService
import com.mipatrimonio.app.data.quotes.UrlConnectionHttpClient
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
    val quoteSecrets = KeystoreSecretStore(context.applicationContext)
    private val quoteHttp = UrlConnectionHttpClient()
    val openFigi = OpenFigiService(quoteHttp, quoteSecrets)
    val coinGeckoSearch = CoinGeckoSearchService(quoteHttp, quoteSecrets)
    val twelveDataAssets = TwelveDataAssetService(quoteHttp, quoteSecrets)
    val quotes = QuoteRepository(
        database,
        investments,
        listOf(
            TwelveDataQuoteProvider(quoteHttp, quoteSecrets),
            CoinGeckoQuoteProvider(quoteHttp, quoteSecrets),
        ),
    )
    val settings = SettingsRepository(context.applicationContext)
    val recurring = RecurringRepository(database)
    val recurringReminderScheduler = RecurringReminderScheduler(context.applicationContext, recurring)
    val backupFiles = BackupFileStore(context.applicationContext.contentResolver)
    val backup = BackupService(
        BackupRepository(
            database,
            settings,
            context.applicationContext.packageManager
                .getPackageInfo(context.applicationContext.packageName, 0).versionName.orEmpty(),
        ),
        afterRestore = {
            recurring.generatePending(RecurringRepository.generationLimit(java.time.LocalDate.now()))
            recurringReminderScheduler.scheduleAll()
        },
    )
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
        QuoteRefreshWorker.enqueuePeriodic(context.applicationContext)
        scope.launch {
            recurring.generatePending(
                RecurringRepository.generationLimit(java.time.LocalDate.now()),
            )
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
