package com.mipatrimonio.app.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.mipatrimonio.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.rules.ExternalResource

/**
 * Ajustes en memoria, propios de cada test. El DataStore real es un singleton de proceso (se filtraba
 * entre tests y colgaba la suite) y en Windows su escritura por renombrado de fichero choca con las
 * lecturas concurrentes ("el archivo está siendo utilizado por otro proceso").
 */
class SettingsStoreRule : ExternalResource() {
    private lateinit var store: InMemoryPreferencesDataStore

    lateinit var repository: SettingsRepository
        private set

    override fun before() {
        store = InMemoryPreferencesDataStore()
        repository = SettingsRepository(store)
    }

    /** Nuevo repositorio sobre los mismos datos, para comprobar que los valores se leen de nuevo. */
    fun reopen(): SettingsRepository {
        repository = SettingsRepository(store)
        return repository
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value).toPreferences()
            state.value = updated
            updated
        }
}
