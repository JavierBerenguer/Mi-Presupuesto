package com.mipatrimonio.app

import android.app.Application
import android.content.Context
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Inyección de dependencias manual: suficiente para el tamaño actual y sin magia de generación de código. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    val ledger = LedgerRepository(database)
    val investments = InvestmentRepository(database)
    val settings = SettingsRepository(context.applicationContext)
    val notifications = NotificationRepository(database, NotificationEngine(listOf(GenericSpanishParser())))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { ledger.seedDefaultCategoriesIfEmpty() }
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
