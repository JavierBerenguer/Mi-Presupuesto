package com.mipatrimonio.app.data.importer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

class TradeRepublicCsvAdapter : BankCsvAdapter {
    override val id: String = "trade_republic"
    override val displayName: String = "Trade Republic"

    override fun matches(headers: List<String>): Boolean = REQUIRED_HEADERS.all(headers::contains)

    override fun parse(table: CsvTable): ImportPreview {
        val movements = mutableListOf<ImportedMovement>()
        val issues = mutableListOf<ImportIssue>()
        val seenIds = mutableSetOf<String>()
        val duplicateIds = linkedSetOf<String>()

        table.rows.forEach { row ->
            val externalId = row["transaction_id"].trim()
            if (externalId.isEmpty()) {
                issues += ImportIssue(row.lineNumber, "Falta el identificador de la transacción.")
                return@forEach
            }
            if (!seenIds.add(externalId)) {
                duplicateIds += externalId
                return@forEach
            }

            val date = try {
                LocalDate.parse(row["date"].trim())
            } catch (_: DateTimeParseException) {
                issues += ImportIssue(row.lineNumber, "La fecha está ausente o no tiene formato ISO válido.")
                return@forEach
            }

            val amountText = row["amount"]
            val amountCents = if (amountText.isBlank()) {
                0L
            } else {
                parseCentsOrNull(amountText) ?: run {
                    issues += ImportIssue(row.lineNumber, "El importe no es un número válido.")
                    return@forEach
                }
            }

            val rawType = row["type"].trim()
            val normalizedType = rawType.uppercase(Locale.ROOT)
            val cancelled = normalizedType.endsWith(CANCELLED_SUFFIX)
            val baseType = normalizedType.removeSuffix(CANCELLED_SUFFIX)
            val kind = classify(baseType)
            val currency = row["currency"].trim().ifEmpty { null }
            val privateFundWithoutAmount =
                baseType == "BUY" &&
                    row["asset_class"].trim().equals("PRIVATE_FUND", ignoreCase = true) &&
                    amountText.isBlank()

            val reviewReasons = buildList {
                if (cancelled) add("La operación está cancelada y debe revisarse.")
                if (kind == ImportedKind.TRANSFERENCIA) {
                    add("La transferencia debe revisarse para identificar si es entre cuentas propias o con un tercero.")
                }
                if (kind == ImportedKind.DESCONOCIDO) add("El tipo de operación no está reconocido.")
                if (privateFundWithoutAmount) {
                    add("La compra de fondo privado no tiene importe; se conserva a cero para evitar duplicar la salida de caja.")
                }
                if (currency == null) add("La fila no tiene divisa.")
            }

            movements += ImportedMovement(
                externalId = externalId,
                date = date,
                kind = kind,
                amountCents = amountCents,
                currency = currency,
                description = firstNonBlank(
                    row["description"],
                    row["payment_reference"],
                    row["name"],
                ),
                counterparty = row["counterparty_name"].trim().ifEmpty { null },
                isin = row["symbol"].trim().ifEmpty { null },
                assetName = row["name"].trim().ifEmpty { null },
                shares = parseBigDecimalOrNull(row["shares"]),
                price = parseBigDecimalOrNull(row["price"]),
                feeCents = parseCentsOrNull(row["fee"]) ?: 0L,
                taxCents = parseCentsOrNull(row["tax"]) ?: 0L,
                originalAmountCents = parseCentsOrNull(row["original_amount"]),
                originalCurrency = row["original_currency"].trim().ifEmpty { null },
                fxRate = parseBigDecimalOrNull(row["fx_rate"]),
                mccCode = row["mcc_code"].trim().ifEmpty { null },
                needsReview = reviewReasons.isNotEmpty(),
                reviewReason = reviewReasons.takeIf { it.isNotEmpty() }?.joinToString(" "),
                rawType = rawType,
            )
        }

        return ImportPreview(
            movements = movements,
            duplicateIdsInFile = duplicateIds.toList(),
            issues = issues,
        )
    }

    private fun classify(type: String): ImportedKind = when (type) {
        "BUY", "PRIVATE_MARKET_BUY", "IPO_SUBSCRIPTION" -> ImportedKind.COMPRA
        "SELL" -> ImportedKind.VENTA
        "DIVIDEND", "LIQUIDATION_DIVIDEND", "INTERMEDIATE_SECURITIES_DISTRIBUTION" ->
            ImportedKind.DIVIDENDO
        "INTEREST_PAYMENT" -> ImportedKind.INTERES
        "FEE" -> ImportedKind.COMISION
        "CARD_TRANSACTION", "CARD_TRANSACTION_INTERNATIONAL" -> ImportedKind.GASTO
        "BENEFITS_SAVEBACK", "BONUS", "LIQUIDATION_PROCEEDS" -> ImportedKind.INGRESO
        "FREE_DELIVERY" -> ImportedKind.DESCONOCIDO
        else -> if (type.startsWith("TRANSFER_")) ImportedKind.TRANSFERENCIA else ImportedKind.DESCONOCIDO
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun parseBigDecimalOrNull(value: String): BigDecimal? {
        if (value.isBlank()) return null
        return try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseCentsOrNull(value: String): Long? {
        if (value.isBlank()) return null
        return try {
            BigDecimal(value.trim())
                .setScale(2, RoundingMode.HALF_EVEN)
                .movePointRight(2)
                .longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private companion object {
        const val CANCELLED_SUFFIX = "_CANCELLED"
        val REQUIRED_HEADERS = setOf("transaction_id", "date", "amount", "type")
    }
}
