package com.mipatrimonio.app.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.mipatrimonio.app.data.repository.SettingsRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * DataStore de ajustes propio de cada test. El DataStore de producción es un singleton de proceso
 * ligado al primer Context que lo usa; en Robolectric eso lo compartía entre tests y bloqueaba la suite.
 */
class SettingsStoreRule : ExternalResource() {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    lateinit var repository: SettingsRepository
        private set

    override fun before() {
        dir = Files.createTempDirectory("settings-test").toFile()
        open()
    }

    override fun after() {
        close()
        dir.deleteRecursively()
    }

    /** Cierra el DataStore y lo vuelve a abrir sobre el mismo fichero, para comprobar persistencia real. */
    fun reopen(): SettingsRepository {
        close()
        open()
        return repository
    }

    private fun close() {
        val job = scope.coroutineContext[Job]!!
        scope.cancel()
        runBlocking { job.join() }
    }

    private fun open() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(dir, "settings.preferences_pb")
        }
        repository = SettingsRepository(store)
    }
}
