package com.mipatrimonio.app

import android.app.Application
import android.content.Context
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.backup.BackupFileStore
import com.mipatrimonio.app.data.backup.BackupRepository
import com.mipatrimonio.app.data.backup.BackupService
import com.mipatrimonio.app.data.export.CsvExportFileStore
import com.mipatrimonio.app.data.export.CsvExportRepository
import com.mipatrimonio.app.data.export.CsvExportService
import com.mipatrimonio.app.data.importer.ImportFileStore
import com.mipatrimonio.app.data.importer.TradeRepublicImportRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.work.RecurringReminderScheduler
import com.mipatrimonio.app.data.sync.AndroidKeystoreSessionStore
import com.mipatrimonio.app.data.sync.SupabaseHttpTransport
import com.mipatrimonio.app.data.sync.SyncAuthClient
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.data.sync.SyncScheduler
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.notifications.PendingAutomationNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Inyección de dependencias manual: suficiente para el tamaño actual y sin magia de generación de código. */
@OptIn(FlowPreview::class)
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    private val notificationPreferences = context.applicationContext.getSharedPreferences(
        "notification_diagnostics",
        Context.MODE_PRIVATE,
    )
    val ledger = LedgerRepository(database)
    val settings = SettingsRepository(context.applicationContext)
    private val syncSessionStore = AndroidKeystoreSessionStore(context.applicationContext)
    val sync = SyncRepository(
        database, settings, ledger,
        authFactory = { url, key -> SyncAuthClient(SupabaseHttpTransport(url, key), syncSessionStore) },
        onOutboxEnqueued = { SyncScheduler.enqueueOutbox(context.applicationContext) },
    )
    val pendingAutomationNotifier = PendingAutomationNotifier(context.applicationContext, settings)
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
            ledger.updateDefaultCategoryCatalogIfNeeded(settings)
            recurring.generatePending(RecurringRepository.generationLimit(java.time.LocalDate.now()))
            recurringReminderScheduler.scheduleAll()
        },
    )
    val csvExportFiles = CsvExportFileStore(context.applicationContext.contentResolver)
    val csvExport = CsvExportService(CsvExportRepository(database))
    val importFiles = ImportFileStore(context.applicationContext.contentResolver)
    val tradeRepublicImport = TradeRepublicImportRepository(database)
    val notifications = NotificationRepository(
        database,
        NotificationEngine(listOf(GenericSpanishParser())),
        initialDiagnosticTextEnabled = notificationPreferences.getBoolean(DIAGNOSTIC_TEXT_KEY, false),
        persistDiagnosticTextEnabled = { enabled ->
            notificationPreferences.edit().putBoolean(DIAGNOSTIC_TEXT_KEY, enabled).apply()
        },
        hasSyncSession = sync::hasSession,
        createInvestmentPurchase = { productId, date, amountMinor, accountId, note ->
            sync.crearCompra(
                productoId = productId,
                fecha = date,
                importeMinor = amountMinor,
                unidades = null,
                comisionMinor = null,
                cuentaId = accountId,
                origen = "notificacion",
                nota = note,
            )
        },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            ledger.updateDefaultCategoryCatalogIfNeeded(settings)
        }
        scope.launch {
            settings.settings.map { it.syncLastSuccessAt }.distinctUntilChanged().drop(1).collect { lastSuccess ->
                if (lastSuccess != null) notifications.reprocessPending()
            }
        }
        recurringReminderScheduler.createChannel()
        pendingAutomationNotifier.createChannel()
        RecurringReminderScheduler.enqueuePeriodic(context.applicationContext)
        SyncScheduler.schedule(context.applicationContext)
        clearLegacyMarketSecrets(context.applicationContext)
        scope.launch {
            recurring.generatePending(
                RecurringRepository.generationLimit(java.time.LocalDate.now()),
            )
            recurringReminderScheduler.scheduleAll()
        }
        scope.launch {
            combine(ledger.accounts, ledger.transactions, ledger.transfers) { _, _, _ -> Unit }
                .drop(1).debounce(30_000).collect { SyncScheduler.enqueueAccountPublish(context.applicationContext) }
        }
    }

    private companion object {
        const val DIAGNOSTIC_TEXT_KEY = "diagnostic_text_enabled"
        const val LEGACY_MARKET_SECRETS_CLEANED_KEY = "legacy_market_secrets_cleaned"

        fun clearLegacyMarketSecrets(context: Context) {
            val cleanupPreferences = context.getSharedPreferences("app_cleanup", Context.MODE_PRIVATE)
            if (cleanupPreferences.getBoolean(LEGACY_MARKET_SECRETS_CLEANED_KEY, false)) return
            val preferences = context.getSharedPreferences("quote_secrets", Context.MODE_PRIVATE)
            if (preferences.all.isNotEmpty()) preferences.edit().clear().apply()
            runCatching {
                java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            }.getOrNull()?.let { keyStore ->
                runCatching {
                    if (keyStore.containsAlias("mi_patrimonio_quote_secrets")) {
                        keyStore.deleteEntry("mi_patrimonio_quote_secrets")
                    }
                }
            }
            cleanupPreferences.edit().putBoolean(LEGACY_MARKET_SECRETS_CLEANED_KEY, true).apply()
        }
    }
}

class MiPatrimonioApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
