package com.mipatrimonio.app.data.sync

import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InvestmentMovementRulesTest {
    private val date = LocalDate.of(2026, 10, 1)
    private val quoted = product(fuente = "yahoo")
    private val manual = product(fuente = "manual")
    private val account = Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 0, useInApp = true)

    @Test fun `compra cotizada exige participaciones positivas y manual las permite vacias`() {
        assertFails { validate(InvestmentMovementType.PURCHASE, quoted, units = null) }
        assertFails { validate(InvestmentMovementType.PURCHASE, quoted, units = BigDecimal.ZERO) }
        validate(InvestmentMovementType.PURCHASE, manual, units = null)
    }

    @Test fun `producto es obligatorio no borrado y fecha no futura`() {
        assertFails {
            InvestmentMovementRules.validate(
                InvestmentMovementType.PURCHASE, null, date, 100, BigDecimal.ONE, null,
                null, null, emptyList(), date,
            )
        }
        assertFails { validate(InvestmentMovementType.PURCHASE, quoted.copy(deletedAt = "now")) }
        assertFails {
            InvestmentMovementRules.validate(
                InvestmentMovementType.PURCHASE, quoted, date.plusDays(1), 100, BigDecimal.ONE, null,
                null, null, emptyList(), date,
            )
        }
    }

    @Test fun `venta cotizada admite cero pero no negativo ni mas de lo disponible`() {
        validate(InvestmentMovementType.SALE, quoted, units = BigDecimal.ZERO)
        assertFails { validate(InvestmentMovementType.SALE, quoted, units = BigDecimal("-1")) }
        assertFails { validate(InvestmentMovementType.SALE, quoted, units = BigDecimal("2")) }
    }

    @Test fun `venta manual admite participaciones vacias`() {
        validate(InvestmentMovementType.SALE, manual, units = null)
    }

    @Test fun `dividendo no pide unidades ni comision y efectivo solo admite dividendo`() {
        validate(InvestmentMovementType.DIVIDEND, product(tipo = "efectivo"), units = null)
        assertFails { validate(InvestmentMovementType.FEE, product(tipo = "efectivo"), units = null) }
        assertFails { validate(InvestmentMovementType.DIVIDEND, quoted, units = BigDecimal.ONE) }
    }

    @Test fun `comision rechaza deuda e importes no positivos`() {
        assertFails { validate(InvestmentMovementType.FEE, product(tipo = "deuda"), units = null) }
        assertFails { validate(InvestmentMovementType.FEE, quoted, units = null, amount = 0) }
    }

    @Test fun `nota es opcional y se recorta`() {
        val result = InvestmentMovementRules.validate(
            InvestmentMovementType.DIVIDEND, quoted, date, 100, null, null,
            null, null, emptyList(), date, note = "  alquiler  ",
        )
        assertEquals("alquiler", result.note)
    }

    @Test fun `comision incluida debe ser no negativa y menor que importe`() {
        assertFails { validate(InvestmentMovementType.PURCHASE, quoted, commission = -1) }
        assertFails { validate(InvestmentMovementType.PURCHASE, quoted, commission = 100) }
    }

    @Test fun `retencion es opcional no negativa solo en dividendo y venta`() {
        validate(InvestmentMovementType.DIVIDEND, quoted, units = null, withholding = 30)
        validate(InvestmentMovementType.SALE, manual, units = null, withholding = 0)
        assertFails { validate(InvestmentMovementType.DIVIDEND, quoted, units = null, withholding = -1) }
        assertFails { validate(InvestmentMovementType.PURCHASE, manual, units = null, withholding = 30) }
        assertFails { validate(InvestmentMovementType.FEE, quoted, units = null, withholding = 30) }
    }

    @Test fun `cuenta valida y obligatoriedad se comprueban`() {
        assertFails { validate(InvestmentMovementType.DIVIDEND, quoted, units = null, requireAccount = true, accountId = null) }
        validate(InvestmentMovementType.DIVIDEND, quoted, units = null, accountId = "a", account = account.copy(useInApp = false))
        assertFails { validate(InvestmentMovementType.DIVIDEND, quoted, units = null, accountId = "a", account = account.copy(currency = "USD")) }
        assertFails { validate(InvestmentMovementType.DIVIDEND, quoted, units = null, accountId = "a", account = account.copy(archived = true)) }
    }

    @Test fun `tolerancia ajusta a todas`() {
        val result = validate(
            InvestmentMovementType.SALE, quoted, units = BigDecimal("10.00005"),
            movements = listOf(movement("buy", "compra", "10")),
        )
        assertEquals(BigDecimal("1E+1"), result.units)
    }

    @Test fun `orden del dia aplica compra venta y finalmente ajuste`() {
        val rows = listOf(
            movement("sale", "venta", "2"),
            movement("adjust", "ajuste", "5"),
            movement("buy", "compra", "10"),
        )
        assertEquals(BigDecimal("5"), InvestmentMovementRules.unitsAt("p", date, rows))
    }

    @Test fun `traspasos salientes y entrantes y ajuste calculan unidades`() {
        val rows = listOf(
            movement("buy", "compra", "10"),
            movement("out", "traspaso", "3", productId = "p", destination = "q", destinationUnits = "4"),
            movement("in", "traspaso", "2", productId = "q", destination = "p", destinationUnits = "5"),
            movement("adjust", "ajuste", "20"),
        )
        assertEquals(0, BigDecimal("20").compareTo(InvestmentMovementRules.unitsAt("p", date, rows)))
    }

    @Test fun `movimiento editado se excluye del disponible`() {
        val rows = listOf(movement("buy", "compra", "10"), movement("sale", "venta", "8"))
        assertEquals(0, BigDecimal("10").compareTo(InvestmentMovementRules.unitsAt("p", date, rows, "sale")))
    }

    private fun validate(
        type: InvestmentMovementType, product: SyncProductoEntity, units: BigDecimal? = BigDecimal.ONE,
        commission: Long? = null, amount: Long = 100, movements: List<SyncMovimientoEntity> = emptyList(),
        requireAccount: Boolean = false, accountId: String? = "a", account: Account? = this.account,
        withholding: Long? = null,
    ) = InvestmentMovementRules.validate(
        type, product, date, amount, units, commission, accountId, account, movements, date,
        requireAccount = requireAccount, withholdingMinor = withholding,
    )

    private fun assertFails(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }

    private fun product(tipo: String = "fondo", fuente: String? = "manual") =
        SyncProductoEntity("p", "profile", "Producto", null, tipo, "EUR", null, null, "now", fuente)

    private fun movement(
        id: String, type: String, units: String, productId: String = "p",
        destination: String? = null, destinationUnits: String? = null,
    ) = SyncMovimientoEntity(
        id, "profile", productId, date.toEpochDay(), type, units, 100, null, null,
        "escritorio", null, null, "now", destination, destinationUnits,
    )
}
