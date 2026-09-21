package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import java.math.BigDecimal
import java.math.RoundingMode

class InvalidOperationException(message: String) : IllegalArgumentException(message)

/**
 * Posición derivada de un activo en una cartera (nunca se guarda).
 *
 * Método de coste: precio medio ponderado.
 *  - Compra: cantidad += q; coste += q·precio + comisión (la comisión se capitaliza en el coste).
 *  - Venta: coste retirado = coste · q / cantidad; plusvalía realizada += (q·precio − comisión) − coste retirado.
 *  - Dividendo: neto = q·importe − retención. No altera cantidad ni coste.
 *  - Comisión suelta: se acumula en `feesMinor`, fuera del coste.
 * Los importes monetarios internos son BigDecimal en la divisa del activo.
 */
data class Position(
    val quantity: BigDecimal,
    val costBasis: BigDecimal,
    val realizedPnl: BigDecimal,
    val dividendsNet: BigDecimal,
    val otherFees: BigDecimal,
) {
    val averagePrice: BigDecimal?
        get() = if (quantity.signum() == 0) null else costBasis.divide(quantity, MoneyMath.CONTEXT)

    val isOpen: Boolean get() = quantity.signum() > 0
}

data class PositionValuation(
    val position: Position,
    val price: BigDecimal?,
    val marketValue: BigDecimal?,
    val unrealizedPnl: BigDecimal?,
    /** Rentabilidad simple no realizada = plusvalía latente / coste. No es TWR ni MWR. Null sin precio o sin coste. */
    val unrealizedReturnPct: BigDecimal?,
)

object PositionCalculator {
    private val ZERO = BigDecimal.ZERO

    /** Valida una operación aislada; devuelve un mensaje o null. */
    fun validate(op: InvestmentOperation): String? = when {
        op.quantity.signum() <= 0 -> "La cantidad debe ser positiva"
        op.unitPrice.signum() < 0 -> "El precio no puede ser negativo"
        op.feesMinor < 0 -> "Las comisiones no pueden ser negativas"
        else -> null
    }

    /** Calcula la posición aplicando las operaciones en el orden dado (fecha, luego creación). */
    fun compute(operations: List<InvestmentOperation>): Position {
        var qty = ZERO
        var cost = ZERO
        var realized = ZERO
        var dividends = ZERO
        var fees = ZERO
        val ordered = operations.sortedWith(compareBy({ it.date }, { it.createdAt }))
        for (op in ordered) {
            validate(op)?.let { throw InvalidOperationException(it) }
            val gross = op.quantity.multiply(op.unitPrice)
            val opFees = MoneyMath.toDecimal(op.feesMinor, op.currency)
            when (op.type) {
                OperationType.COMPRA -> {
                    qty = qty.add(op.quantity)
                    cost = cost.add(gross).add(opFees)
                }
                OperationType.VENTA -> {
                    if (op.quantity > qty) {
                        throw InvalidOperationException("No se puede vender más de lo que se posee")
                    }
                    val removedCost = cost.multiply(op.quantity).divide(qty, MoneyMath.CONTEXT)
                    realized = realized.add(gross.subtract(opFees).subtract(removedCost))
                    qty = qty.subtract(op.quantity)
                    cost = if (qty.signum() == 0) ZERO else cost.subtract(removedCost)
                }
                OperationType.DIVIDENDO -> dividends = dividends.add(gross.subtract(opFees))
                OperationType.COMISION -> fees = fees.add(gross)
            }
        }
        return Position(qty, cost, realized, dividends, fees)
    }

    fun value(position: Position, price: BigDecimal?): PositionValuation {
        if (price == null) return PositionValuation(position, null, null, null, null)
        val market = position.quantity.multiply(price)
        val unrealized = market.subtract(position.costBasis)
        val pct = if (position.costBasis.signum() == 0) null
        else unrealized.divide(position.costBasis, MoneyMath.CONTEXT)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_EVEN)
        return PositionValuation(position, price, market, unrealized, pct)
    }
}
