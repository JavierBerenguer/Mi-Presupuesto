package com.mipatrimonio.app.data.importer

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

enum class ImportedKind {
    GASTO,
    INGRESO,
    COMPRA,
    VENTA,
    DIVIDENDO,
    INTERES,
    COMISION,
    IMPUESTO,
    TRANSFERENCIA,
    DESCONOCIDO,
}

data class ImportedMovement(
    val externalId: String,
    val date: LocalDate,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val kind: ImportedKind,
    val amountCents: Long,
    val currency: String?,
    val description: String,
    val counterparty: String?,
    val isin: String?,
    val assetName: String?,
    val shares: BigDecimal?,
    val price: BigDecimal?,
    val feeCents: Long,
    val taxCents: Long,
    val originalAmountCents: Long?,
    val originalCurrency: String?,
    val fxRate: BigDecimal?,
    val mccCode: String?,
    val needsReview: Boolean,
    val reviewReason: String?,
    val rawType: String,
    val assetClass: String = "",
)

data class ImportIssue(
    val lineNumber: Int,
    val message: String,
)

data class ImportPreview(
    val movements: List<ImportedMovement>,
    val duplicateIdsInFile: List<String>,
    val issues: List<ImportIssue>,
)
