package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.OperationType
import java.math.BigDecimal
import java.math.RoundingMode

enum class SaleAmountKind { BRUTO, NETO }

sealed interface TradeSizingError {
    data object UnsupportedOperation : TradeSizingError
    data object NonPositivePrice : TradeSizingError
    data object NonPositiveAmount : TradeSizingError
    data object NegativeFees : TradeSizingError
    data object AmountNotGreaterThanFees : TradeSizingError
    data object InvalidQuantityScale : TradeSizingError
}

data class TradeSizing(
    val quantity: BigDecimal,
    val tradedAmount: BigDecimal,
    val reproducedInputAmount: BigDecimal,
    val roundingDifference: BigDecimal,
)

sealed interface TradeSizingResult {
    data class Success(val sizing: TradeSizing) : TradeSizingResult
    data class Error(val reason: TradeSizingError) : TradeSizingResult
}

/** Calcula una cantidad decimal sin usar coma flotante. El redondeo de cantidad es HALF_EVEN. */
fun calculateTradeSizing(
    operationType: OperationType,
    inputAmount: BigDecimal,
    unitPrice: BigDecimal,
    fees: BigDecimal,
    saleAmountKind: SaleAmountKind = SaleAmountKind.NETO,
    quantityScale: Int = 8,
): TradeSizingResult {
    if (operationType != OperationType.COMPRA && operationType != OperationType.VENTA) {
        return TradeSizingResult.Error(TradeSizingError.UnsupportedOperation)
    }
    if (unitPrice.signum() <= 0) return TradeSizingResult.Error(TradeSizingError.NonPositivePrice)
    if (inputAmount.signum() <= 0) return TradeSizingResult.Error(TradeSizingError.NonPositiveAmount)
    if (fees.signum() < 0) return TradeSizingResult.Error(TradeSizingError.NegativeFees)
    if (inputAmount <= fees) return TradeSizingResult.Error(TradeSizingError.AmountNotGreaterThanFees)
    if (quantityScale < 0) return TradeSizingResult.Error(TradeSizingError.InvalidQuantityScale)

    val tradedAmount = when {
        operationType == OperationType.COMPRA -> inputAmount.subtract(fees)
        saleAmountKind == SaleAmountKind.NETO -> inputAmount.add(fees)
        else -> inputAmount
    }
    val quantity = tradedAmount.divide(unitPrice, quantityScale, RoundingMode.HALF_EVEN)
    val reproducedTradedAmount = quantity.multiply(unitPrice)
    val reproducedInputAmount = when {
        operationType == OperationType.COMPRA -> reproducedTradedAmount.add(fees)
        saleAmountKind == SaleAmountKind.NETO -> reproducedTradedAmount.subtract(fees)
        else -> reproducedTradedAmount
    }
    return TradeSizingResult.Success(
        TradeSizing(
            quantity = quantity,
            tradedAmount = tradedAmount,
            reproducedInputAmount = reproducedInputAmount,
            roundingDifference = inputAmount.subtract(reproducedInputAmount),
        ),
    )
}
