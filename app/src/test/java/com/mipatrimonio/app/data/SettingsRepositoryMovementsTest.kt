package com.mipatrimonio.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryMovementsTest {
    @Test
    fun `valores por defecto y persistencia de ajustes de movimientos`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)
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

        val persisted = SettingsRepository(context).settings.first {
            it.movementsCalculationMode == MovementsCalculationMode.INGRESOS_MENSUALES
        }
        assertEquals(setOf("a", "b"), persisted.movementsIncludedAccountIds)
        assertFalse(persisted.movementsAllAccounts)
        assertFalse(persisted.movementsDailyBalance)
        assertTrue(persisted.movementsHideFuture)
        assertTrue(persisted.movementsIgnoreTransfers)
    }
}
