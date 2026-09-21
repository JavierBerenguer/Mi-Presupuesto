package com.mipatrimonio.app.domain.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Importe en unidades menores enteras (céntimos) de una divisa. Nunca se usa coma flotante.
 * Regla de redondeo global: HALF_EVEN al convertir a unidades menores.
 */
data class Money(val minor: Long, val currency: String) {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    operator fun unaryMinus() = Money(Math.negateExact(minor), currency)

    val isNegative: Boolean get() = minor < 0

    private fun requireSameCurrency(other: Money) =
        require(currency == other.currency) { "Divisas distintas: $currency y ${other.currency}" }

    companion object {
        fun zero(currency: String) = Money(0, currency)
    }
}

object Currencies {
    const val EUR = "EUR"

    /** Divisas ofrecidas por defecto en la interfaz. */
    val comunes = listOf("EUR", "USD", "GBP", "CHF", "JPY", "BTC", "ETH")

    /** Decimales de la unidad menor. ISO 4217 si existe; 8 para cripto conocida; 2 en otro caso. */
    fun decimals(code: String): Int = when (code) {
        "BTC", "ETH" -> 8
        else -> runCatching { Currency.getInstance(code).defaultFractionDigits }
            .getOrDefault(2)
            .coerceAtLeast(0)
    }

    fun symbol(code: String): String = when (code) {
        "BTC", "ETH" -> code
        else -> runCatching { Currency.getInstance(code).getSymbol(LOCALE_ES) }.getOrDefault(code)
    }
}

private val LOCALE_ES = Locale.forLanguageTag("es-ES")

object MoneyMath {
    val CONTEXT: MathContext = MathContext.DECIMAL128

    fun toMinor(amount: BigDecimal, currency: String): Long {
        val scaled = amount.movePointRight(Currencies.decimals(currency)).setScale(0, RoundingMode.HALF_EVEN)
        return scaled.longValueExact()
    }

    fun toDecimal(minor: Long, currency: String): BigDecimal =
        BigDecimal.valueOf(minor, Currencies.decimals(currency))

    fun toDecimal(money: Money): BigDecimal = toDecimal(money.minor, money.currency)

    /** Convierte con un tipo de cambio explícito (1 [from] = [rate] destino). El origen no se modifica. */
    fun convert(minor: Long, fromCurrency: String, toCurrency: String, rate: BigDecimal): Long {
        require(rate.signum() > 0) { "El tipo de cambio debe ser positivo" }
        return toMinor(toDecimal(minor, fromCurrency).multiply(rate, CONTEXT), toCurrency)
    }

    /**
     * Interpreta un importe escrito por el usuario. Acepta "1234,56", "1.234,56", "1,234.56" y "1234.56".
     * Devuelve null si no es un número válido.
     */
    fun parse(text: String): BigDecimal? {
        val t = text.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        val lastComma = t.lastIndexOf(',')
        val lastDot = t.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 -> if (t.count { it == ',' } > 1) t.replace(",", "") else t.replace(',', '.')
            lastDot >= 0 && t.count { it == '.' } > 1 -> t.replace(".", "")
            else -> t
        }
        return normalized.toBigDecimalOrNull()
    }

    fun format(minor: Long, currency: String): String {
        val digits = Currencies.decimals(currency)
        val nf = NumberFormat.getNumberInstance(LOCALE_ES).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        val number = nf.format(toDecimal(minor, currency))
        return "$number ${Currencies.symbol(currency)}"
    }

    fun format(money: Money): String = format(money.minor, money.currency)

    /** Formato de cantidades de activos (hasta 8 decimales, sin ceros sobrantes). */
    fun formatQuantity(quantity: BigDecimal): String {
        val nf = NumberFormat.getNumberInstance(LOCALE_ES).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 8
        }
        return nf.format(quantity)
    }
}
