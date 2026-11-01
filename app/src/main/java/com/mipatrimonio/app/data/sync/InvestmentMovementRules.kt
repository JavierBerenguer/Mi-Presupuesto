package com.mipatrimonio.app.data.sync

import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class InvestmentMovementType(val wireName: String) {
    PURCHASE("compra"), SALE("venta"), DIVIDEND("dividendo"), FEE("comision");

    companion object {
        fun fromWireName(value: String): InvestmentMovementType = entries.firstOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("El tipo de movimiento no se puede modificar en el móvil")
    }
}

data class InvestmentMovementValues(
    val type: InvestmentMovementType,
    val product: SyncProductoEntity,
    val date: LocalDate,
    val amountMinor: Long,
    val units: BigDecimal?,
    val commissionMinor: Long?,
    val accountId: String?,
    val note: String?,
)

object InvestmentMovementRules {
    private val typeOrder = mapOf(
        "compra" to 0, "comision" to 1, "dividendo" to 2,
        "venta" to 3, "traspaso" to 4, "ajuste" to 6,
    )

    fun validate(
        type: InvestmentMovementType,
        product: SyncProductoEntity?,
        date: LocalDate,
        amountMinor: Long,
        units: BigDecimal?,
        commissionMinor: Long?,
        accountId: String?,
        account: Account?,
        movements: List<SyncMovimientoEntity>,
        today: LocalDate,
        editingId: String? = null,
        requireAccount: Boolean = false,
        allowMissingPurchaseUnits: Boolean = false,
        note: String? = null,
    ): InvestmentMovementValues {
        require(product != null) { "Selecciona un producto" }
        require(product.deletedAt == null) { "El producto ya no está disponible" }
        validateProductType(type, product.tipo)
        require(!date.isAfter(today)) { "La fecha no puede ser futura" }
        require(amountMinor > 0) { "El importe debe ser mayor que cero" }
        if (type == InvestmentMovementType.PURCHASE || type == InvestmentMovementType.SALE) {
            require(commissionMinor == null || commissionMinor >= 0) { "La comisión no puede ser negativa" }
            require(commissionMinor == null || commissionMinor < amountMinor) { "La comisión debe ser menor que el importe" }
            validateUnits(type, product, units, allowMissingPurchaseUnits)
        } else {
            require(units == null || units.compareTo(BigDecimal.ZERO) == 0) { "Este movimiento no admite participaciones" }
            require(commissionMinor == null) { "Este movimiento no admite comisión incluida" }
        }
        if (requireAccount) require(accountId != null) { "Selecciona una cuenta de liquidez" }
        if (accountId != null) {
            require(account != null) { "La cuenta no existe" }
            require(account.useInApp) { "La cuenta de liquidez no tiene uso en app" }
            require(account.currency == "EUR") { "La cuenta de liquidez debe estar en EUR" }
            require(!account.archived) { "La cuenta de liquidez está archivada" }
        }
        var validatedUnits = units
        if (type == InvestmentMovementType.SALE && units != null) {
            val available = unitsAt(product.id, date, movements, editingId)
            val tolerance = maxOf(BigDecimal("0.0001"), available.abs().multiply(BigDecimal("0.000001")))
            require(units <= available.add(tolerance)) {
                saleExceedsMessage(date, product.corto ?: product.nombre, available, units)
            }
            validatedUnits = clampSaleUnits(units, available)
        }
        return InvestmentMovementValues(
            type, product, date, amountMinor,
            if (type == InvestmentMovementType.DIVIDEND || type == InvestmentMovementType.FEE) BigDecimal.ZERO else validatedUnits,
            commissionMinor, accountId, note?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    fun unitsAt(
        productId: String,
        date: LocalDate,
        movements: List<SyncMovimientoEntity>,
        excludingId: String? = null,
    ): BigDecimal {
        var total = BigDecimal.ZERO
        movements.asSequence()
            .filter { it.id != excludingId && it.deletedAt == null && it.fecha <= date.toEpochDay() }
            .filter { it.productoId == productId || it.destinoId == productId }
            .sortedWith(compareBy<SyncMovimientoEntity>({ it.fecha }, { typeOrder[it.tipo] ?: 5 }, { it.id }))
            .forEach { movement ->
                val units = movement.unidades.toDecimalOrZero()
                if (movement.productoId == productId) {
                    total = when (movement.tipo) {
                        "compra" -> total.add(units)
                        "venta", "traspaso" -> total.subtract(units)
                        "ajuste" -> units
                        else -> total
                    }
                }
                if (movement.tipo == "traspaso" && movement.destinoId == productId) {
                    total = total.add(movement.unidadesDestino.toDecimalOrZero())
                }
            }
        return total.stripTrailingZeros()
    }

    fun clampSaleUnits(requested: BigDecimal, available: BigDecimal): BigDecimal {
        val tolerance = maxOf(BigDecimal("0.0001"), available.abs().multiply(BigDecimal("0.000001")))
        return if (requested > available && requested <= available.add(tolerance)) available else requested
    }

    private fun validateProductType(type: InvestmentMovementType, productType: String) {
        require(productType != "deuda") { "Los productos de deuda no admiten este movimiento" }
        if (productType == "efectivo") {
            require(type == InvestmentMovementType.DIVIDEND) { "Los productos de efectivo solo admiten dividendos" }
        }
    }

    private fun validateUnits(
        type: InvestmentMovementType,
        product: SyncProductoEntity,
        units: BigDecimal?,
        allowMissingPurchaseUnits: Boolean,
    ) {
        val quoted = product.fuente != "manual"
        if (quoted && !(allowMissingPurchaseUnits && type == InvestmentMovementType.PURCHASE)) {
            require(units != null) { "Indica las participaciones" }
        }
        if (units != null) {
            if (type == InvestmentMovementType.PURCHASE && quoted) {
                require(units > BigDecimal.ZERO) { "Las participaciones deben ser mayores que cero" }
            } else if (type == InvestmentMovementType.SALE) {
                require(units >= BigDecimal.ZERO) { "Las participaciones no pueden ser negativas" }
            }
        }
    }

    private fun saleExceedsMessage(date: LocalDate, product: String, available: BigDecimal, requested: BigDecimal): String =
        "El ${date.format(SPANISH_DATE)} solo tenías ${MoneyMath.formatQuantity(available)} participaciones de «$product» y estás vendiendo " +
            "${MoneyMath.formatQuantity(requested)}. Para venderlo todo, pulsa «Todas»."

    private val SPANISH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}

private fun String?.toDecimalOrZero(): BigDecimal = this?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: BigDecimal.ZERO
