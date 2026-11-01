package com.mipatrimonio.app.ui.portfolio

import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseValidationTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val product = SyncProductoEntity("p1", "profile", "Fondo", null, "fondo", "EUR", null, null, "now")
    private val account = Account("a1", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1)

    @Test fun `rechaza importe no positivo comision igual y cuenta no eur`() {
        assertTrue(validate("0", "", account).isFailure)
        assertTrue(validate("100", "100", account).isFailure)
        assertTrue(validate("100", "", account.copy(currency = "USD")).isFailure)
    }

    @Test fun `convierte y conserva datos correctos`() {
        val value = validatePurchase("p1", today, "100,25", "2.5", "0,25", "a1", " nota ", listOf(product), listOf(account), today).getOrThrow()
        assertEquals(10_025L, value.amountMinor)
        assertEquals(25L, value.commissionMinor)
        assertEquals("2.5", value.units?.toPlainString())
        assertEquals("nota", value.note)
    }

    private fun validate(amount: String, commission: String, selectedAccount: Account) =
        validatePurchase("p1", today, amount, "", commission, selectedAccount.id, "", listOf(product), listOf(selectedAccount), today)
}
