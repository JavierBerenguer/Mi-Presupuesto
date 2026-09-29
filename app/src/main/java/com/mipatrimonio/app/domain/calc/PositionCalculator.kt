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
    val realizedCostBasis: BigDecimal = BigDecimal.ZERO,
    val capitalizedFees: BigDecimal = BigDecimal.ZERO,
) {
    val averagePrice: BigDecimal?
        get() = if (quantity.signum() == 0) null else costBasis.divide(quantity, MoneyMath.CONTEXT)

    val isOpen: Boolean get() = quantity.signum() > 0

    /** Comisiones de compras y ventas más las operaciones de comisión sueltas. */
    val totalFees: BigDecimal get() = capitalizedFees.add(otherFees)

    /** Rentabilidad simple realizada sobre el coste retirado. Null cuando todavía no se ha vendido. */
    val realizedReturnPct: BigDecimal?
        get() = if (realizedCostBasis.signum() <= 0) null else realizedPnl
            .divide(realizedCostBasis, MoneyMath.CONTEXT)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_EVEN)
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

    /** Calcula la posición aplicando las operaciones por fecha, hora y, como desempate, creación. */
    fun compute(operations: List<InvestmentOperation>): Position {
        var qty = ZERO
        var cost = ZERO
        var realized = ZERO
        var dividends = ZERO
        var fees = ZERO
        var realizedCostBasis = ZERO
        var capitalizedFees = ZERO
        val ordered = operations.sortedWith(compareBy({ it.date }, { it.time }, { it.createdAt }))
        for (op in ordered) {
            validate(op)?.let { throw InvalidOperationException(it) }
            val gross = op.quantity.multiply(op.unitPrice)
            val opFees = MoneyMath.toDecimal(op.feesMinor, op.currency)
            when (op.type) {
                OperationType.COMPRA -> {
                    qty = qty.add(op.quantity)
                    cost = cost.add(gross).add(opFees)
                    capitalizedFees = capitalizedFees.add(opFees)
                }
                OperationType.VENTA -> {
                    if (op.quantity > qty) {
                        throw InvalidOperationException("No se puede vender más de lo disponible (${MoneyMath.formatQuantity(qty)})")
                    }
                    val removedCost = cost.multiply(op.quantity).divide(qty, MoneyMath.CONTEXT)
                    realizedCostBasis = realizedCostBasis.add(removedCost)
                    capitalizedFees = capitalizedFees.add(opFees)
                    realized = realized.add(gross.subtract(opFees).subtract(removedCost))
                    qty = qty.subtract(op.quantity)
                    cost = if (qty.signum() == 0) ZERO else cost.subtract(removedCost)
                }
                OperationType.DIVIDENDO -> dividends = dividends.add(gross.subtract(opFees))
                OperationType.COMISION -> fees = fees.add(gross)
                OperationType.TRASPASO_SALIDA -> {
                    if (op.quantity > qty) {
                        throw InvalidOperationException("No se puede traspasar más de lo disponible (${MoneyMath.formatQuantity(qty)})")
                    }
                    val removedCost = cost.multiply(op.quantity).divide(qty, MoneyMath.CONTEXT)
                    qty = qty.subtract(op.quantity)
                    cost = if (qty.signum() == 0) ZERO else cost.subtract(removedCost)
                }
                OperationType.TRASPASO_ENTRADA -> {
                    qty = qty.add(op.quantity)
                    cost = cost.add(gross)
                }
            }
        }
        return Position(qty, cost, realized, dividends, fees, realizedCostBasis, capitalizedFees)
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
