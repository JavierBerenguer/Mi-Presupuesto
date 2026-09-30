package com.mipatrimonio.app.data.importer

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class NeverlessRow(
    val lineNumber: Int,
    val id: String,
    val type: String,
    val date: LocalDate,
    val time: LocalTime,
    val amountReceived: BigDecimal?,
    val assetReceived: String?,
    val amountSent: BigDecimal?,
    val assetSent: String?,
    val fee: BigDecimal?,
    val feeAsset: String?,
    val description: String,
    val usdPriceReceived: BigDecimal?,
    val usdPriceSent: BigDecimal?,
    val usdPriceFeeAsset: BigDecimal?,
    val network: String?,
    val blockchainAddress: String?,
    val blockchainHash: String?,
)

data class NeverlessPreview(
    val rows: List<NeverlessRow>,
    val duplicateKeysInFile: List<String>,
    val issues: List<ImportIssue>,
)

class NeverlessCsvAdapter : BankCsvAdapter {
    override val id = "neverless"
    override val displayName = "Neverless"

    override fun matches(headers: List<String>): Boolean = REQUIRED_HEADERS.all(headers::contains)

    /** Compatibilidad con el detector común; el planificador de Neverless usa [parseNeverless]. */
    override fun parse(table: CsvTable): ImportPreview {
        val preview = parseNeverless(table)
        return ImportPreview(emptyList(), preview.duplicateKeysInFile, preview.issues)
    }

    fun parseNeverless(table: CsvTable): NeverlessPreview {
        val rows = mutableListOf<NeverlessRow>()
        val issues = mutableListOf<ImportIssue>()
        val seen = mutableSetOf<String>()
        val duplicates = linkedSetOf<String>()
        table.rows.forEach { source ->
            val id = source["ID"].trim()
            val type = source["Type"].trim()
            if (id.isBlank() || type.isBlank()) {
                issues += ImportIssue(source.lineNumber, "Falta el identificador o el tipo de la fila.")
                return@forEach
            }
            val key = "$id:$type"
            if (!seen.add(key)) {
                duplicates += key
                return@forEach
            }
            val instant = runCatching { Instant.parse(source["Date"].trim()) }.getOrNull()
            if (instant == null) {
                issues += ImportIssue(source.lineNumber, "La fecha no tiene formato ISO UTC válido.")
                return@forEach
            }
            val decimalColumns = DECIMAL_COLUMNS.filter { source[it].isNotBlank() }
            val parsed = decimalColumns.associateWith { source[it].toBigDecimalOrNull() }
            val invalid = parsed.entries.firstOrNull { it.value == null }?.key
            if (invalid != null) {
                issues += ImportIssue(source.lineNumber, "El campo $invalid no es un número decimal válido.")
                return@forEach
            }
            val local = instant.atZone(ZoneId.systemDefault())
            rows += NeverlessRow(
                source.lineNumber, id, type, local.toLocalDate(), local.toLocalTime(),
                parsed["Amount received"], source["Asset received"].nullable(),
                parsed["Amount sent"], source["Asset sent"].nullable(),
                parsed["Fee"], source["Asset of the fee"].nullable(), source["Description"].trim(),
                parsed["USD price of asset received"], parsed["USD price of asset sent"],
                parsed["USD price of fee asset"], source["Network"].nullable(),
                source["Blockchain address"].nullable(), source["Blockchain transaction hash"].nullable(),
            )
        }
        return NeverlessPreview(rows, duplicates.toList(), issues)
    }

    private fun String.nullable() = trim().ifBlank { null }

    companion object {
        val REQUIRED_HEADERS = setOf(
            "Type", "Date", "Amount received", "Asset received", "Amount sent", "Asset sent",
            "Fee", "Asset of the fee", "Description", "USD price of asset received",
            "USD price of asset sent", "USD price of fee asset", "Network", "Blockchain address",
            "Blockchain transaction hash", "ID",
        )
        private val DECIMAL_COLUMNS = setOf(
            "Amount received", "Amount sent", "Fee", "USD price of asset received",
            "USD price of asset sent", "USD price of fee asset",
        )
    }
}
