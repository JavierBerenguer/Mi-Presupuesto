package com.mipatrimonio.app.data

import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryMovementsTest {
    @get:Rule
    val settingsRule = SettingsStoreRule()

    @Test
    fun `valores por defecto y persistencia de ajustes de movimientos`() = runTest {
        val repository = settingsRule.repository
        val defaults = repository.settings.first()
        assertTrue(defaults.movementsIncludedAccountIds.isEmpty())
        assertTrue(defaults.movementsAllAccounts)
        assertEquals(MovementsCalculationMode.SALDO_ACTUAL, defaults.movementsCalculationMode)
        assertTrue(defaults.movementsDailyBalance)
        assertFalse(defaults.movementsHideFuture)
        assertFalse(defaults.movementsIgnoreTransfers)

        repository.setMovementsIncludedAccountIds(setOf("a", "b"))
        repository.setMovementsAllAccounts(false)
        repository.setMovementsCalculationMode(MovementsCalculationMode.INGRESOS_MENSUALES)
        repository.setMovementsDailyBalance(false)
        repository.setMovementsHideFuture(true)
        repository.setMovementsIgnoreTransfers(true)

        val persisted = settingsRule.reopen().settings.first {
            it.movementsCalculationMode == MovementsCalculationMode.INGRESOS_MENSUALES
        }
        assertEquals(setOf("a", "b"), persisted.movementsIncludedAccountIds)
        assertFalse(persisted.movementsAllAccounts)
        assertFalse(persisted.movementsDailyBalance)
        assertTrue(persisted.movementsHideFuture)
        assertTrue(persisted.movementsIgnoreTransfers)
    }

    @Test
    fun `version del catalogo se exporta aplica y persiste`() = runTest {
        val repository = settingsRule.repository
        assertEquals(0, repository.settings.first().categoryCatalogVersion)
        repository.setCategoryCatalogVersion(2)

        assertEquals(2, settingsRule.reopen().settings.first().categoryCatalogVersion)
        assertEquals(2, repository.exportForBackup()["category_catalog_version"])

        repository.applyBackup(mapOf("category_catalog_version" to 1))
        assertEquals(1, repository.settings.first().categoryCatalogVersion)
        repository.applyBackup(emptyMap())
        assertEquals(0, repository.settings.first().categoryCatalogVersion)
    }
}
