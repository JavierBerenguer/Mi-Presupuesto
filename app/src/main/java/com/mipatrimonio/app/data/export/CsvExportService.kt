package com.mipatrimonio.app.data.export

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.PriceQuality
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.QuoteProvider
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.movementStatus
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvExportService(
    private val repository: CsvExportRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun create(): CsvExportArchive = create(repository.load(), today(), zoneId)

    companion object {
        private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
        internal fun create(data: CsvExportData, today: LocalDate, zoneId: ZoneId): CsvExportArchive {
            val accounts = data.accounts.associateBy { it.id }
            val categories = data.categories.associateBy { it.id }
            val portfolios = data.portfolios.associateBy { it.id }
            val assets = data.assets.associateBy { it.id }
            val rows = linkedMapOf(
                "cuentas.csv" to accountsRows(data, today, zoneId),
                "categorias.csv" to categoryRows(data.categories, categories),
                "movimientos.csv" to transactionRows(data, accounts, categories, today),
                "transferencias.csv" to transferRows(data, accounts, categories, today),
                "presupuestos.csv" to budgetRows(data, categories),
                "carteras.csv" to portfolioRows(data, accounts, zoneId),
                "activos.csv" to assetRows(data),
                "operaciones.csv" to operationRows(data, portfolios, assets, accounts),
                "dividendos.csv" to dividendRows(data, assets, accounts),
                "precios.csv" to priceRows(data, assets, zoneId),
            )
            val output = ByteArrayOutputStream()
            ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
                rows.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    CsvWriter.write(zip, content.asSequence())
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("LEEME.txt"))
                zip.write(readme().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            return CsvExportArchive(output.toByteArray(), rows.mapValues { it.value.size - 1 })
        }

        private fun accountsRows(data: CsvExportData, today: LocalDate, zoneId: ZoneId): List<List<String>> =
            rows(listOf("id", "nombre", "tipo", "divisa", "saldo_inicial", "saldo_actual", "archivada", "fecha_creacion")) {
                data.accounts.map { account ->
                    listOf(
                        account.id, account.name, account.type.name.toSpanishWords(), account.currency,
                        money(account.initialBalanceMinor, account.currency),
                        money(BalanceCalculator.balance(account, data.transactions, data.transfers, data.operations, today), account.currency),
                        yesNo(account.archived), Instant.ofEpochMilli(account.createdAt).atZone(zoneId).toLocalDate().toString(),
                    )
                }
            }

        private fun categoryRows(values: List<Category>, categories: Map<String, Category>): List<List<String>> =
            rows(listOf("id", "nombre", "tipo", "categoria_padre", "archivada")) {
                values.map {
                    listOf(
                        it.id, it.name, categoryKind(it.kind),
                        it.parentId?.let(categories::get)?.name.orEmpty(), yesNo(it.archived),
                    )
                }
            }

        private fun transactionRows(
            data: CsvExportData,
            accounts: Map<String, com.mipatrimonio.app.domain.model.Account>,
            categories: Map<String, Category>,
            today: LocalDate,
        ): List<List<String>> = rows(
            listOf("id", "fecha", "tipo", "importe", "divisa", "cuenta", "categoria", "subcategoria", "descripcion", "comercio", "notas", "origen", "estado"),
        ) {
            data.transactions.map { transaction ->
                val selected = transaction.categoryId?.let(categories::get)
                val parent = selected?.parentId?.let(categories::get)
                listOf(
                    transaction.id, transaction.date.toString(), transactionType(transaction.type),
                    money(transaction.amountMinor, transaction.currency), transaction.currency,
                    accounts[transaction.accountId]?.name.orEmpty(), parent?.name ?: selected?.name.orEmpty(),
                    if (parent == null) "" else selected?.name.orEmpty(), transaction.description, transaction.merchant,
                    transaction.notes, source(transaction.source), status(movementStatus(transaction.date, today)),
                )
            }
        }

        private fun transferRows(
            data: CsvExportData,
            accounts: Map<String, com.mipatrimonio.app.domain.model.Account>,
            categories: Map<String, Category>,
            today: LocalDate,
        ): List<List<String>> = rows(
            listOf("id", "fecha", "cuenta_origen", "importe_origen", "divisa_origen", "cuenta_destino", "importe_destino", "divisa_destino", "categoria", "descripcion", "estado"),
        ) {
            data.transfers.map { transfer ->
                val from = accounts[transfer.fromAccountId]
                val to = accounts[transfer.toAccountId]
                listOf(
                    transfer.id, transfer.date.toString(), from?.name.orEmpty(),
                    money(transfer.fromAmountMinor, from?.currency.orEmpty()), from?.currency.orEmpty(),
                    to?.name.orEmpty(), money(transfer.toAmountMinor, to?.currency.orEmpty()), to?.currency.orEmpty(),
                    transfer.categoryId?.let(categories::get)?.name.orEmpty(), transfer.description,
                    status(movementStatus(transfer.date, today)),
                )
            }
        }

        private fun budgetRows(data: CsvExportData, categories: Map<String, Category>): List<List<String>> = rows(
            listOf("id", "nombre", "periodo", "importe_limite", "divisa", "fecha_inicio", "fecha_fin", "umbral_aviso", "categorias", "incluye_subcategorias", "archivado"),
        ) {
            data.budgets.map { budget ->
                listOf(
                    budget.id, budget.name, budgetPeriod(budget.period), money(budget.limitMinor, budget.currency), budget.currency,
                    budget.startDate.toString(), budget.endDate?.toString().orEmpty(), budget.alertThresholdPct.toString(),
                    budget.categoryRules.joinToString("|") { categories[it.categoryId]?.name ?: it.categoryId },
                    budget.categoryRules.joinToString("|") { yesNo(it.includeSubcategories) }, yesNo(budget.archived),
                )
            }
        }

        private fun portfolioRows(
            data: CsvExportData,
            accounts: Map<String, com.mipatrimonio.app.domain.model.Account>,
            zoneId: ZoneId,
        ): List<List<String>> = rows(listOf("id", "nombre", "cuenta_predeterminada", "fecha_creacion", "archivada")) {
            data.portfolios.map {
                listOf(
                    it.id, it.name, it.defaultAccountId?.let(accounts::get)?.name.orEmpty(),
                    Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate().toString(),
                    yesNo(it.archived),
                )
            }
        }

        private fun assetRows(data: CsvExportData): List<List<String>> = rows(
            listOf("id", "nombre", "ticker", "isin", "tipo", "mercado", "divisa", "proveedor_cotizacion", "simbolo_cotizacion", "archivado"),
        ) {
            data.assets.map {
                listOf(it.id, it.name, it.ticker, it.isin, assetType(it.type), it.market, it.currency, provider(it.quoteProvider), it.quoteSymbol.orEmpty(), yesNo(it.archived))
            }
        }

        private fun operationRows(
            data: CsvExportData,
            portfolios: Map<String, com.mipatrimonio.app.domain.model.Portfolio>,
            assets: Map<String, com.mipatrimonio.app.domain.model.Asset>,
            accounts: Map<String, com.mipatrimonio.app.domain.model.Account>,
        ): List<List<String>> = rows(
            listOf("id", "fecha", "hora", "cartera", "activo", "tipo", "cantidad", "precio_unitario", "comisiones", "divisa", "cuenta"),
        ) {
            data.operations.map {
                listOf(
                    it.id, it.date.toString(), timeFormat.format(it.time), portfolios[it.portfolioId]?.name.orEmpty(),
                    assets[it.assetId]?.name.orEmpty(), operationType(it.type), decimal(it.quantity), decimal(it.unitPrice),
                    money(it.feesMinor, it.currency), it.currency, it.accountId?.let(accounts::get)?.name.orEmpty(),
                )
            }
        }

        private fun dividendRows(
            data: CsvExportData,
            assets: Map<String, com.mipatrimonio.app.domain.model.Asset>,
            accounts: Map<String, com.mipatrimonio.app.domain.model.Account>,
        ): List<List<String>> = rows(
            listOf("fecha", "activo", "importe_bruto", "retencion", "importe_neto", "divisa", "cuenta"),
        ) {
            data.operations.filter { it.type == OperationType.DIVIDENDO }.map {
                val gross = MoneyMath.toMinor(it.quantity.multiply(it.unitPrice, MoneyMath.CONTEXT), it.currency)
                listOf(
                    it.date.toString(), assets[it.assetId]?.name.orEmpty(), money(gross, it.currency),
                    money(it.feesMinor, it.currency), money(Math.subtractExact(gross, it.feesMinor), it.currency),
                    it.currency, it.accountId?.let(accounts::get)?.name.orEmpty(),
                )
            }
        }

        private fun priceRows(data: CsvExportData, assets: Map<String, com.mipatrimonio.app.domain.model.Asset>, zoneId: ZoneId): List<List<String>> = rows(
            listOf("activo", "fecha_hora", "precio", "divisa", "origen", "calidad"),
        ) {
            data.prices.map {
                listOf(
                    assets[it.assetId]?.name.orEmpty(), dateTimeFormat.format(Instant.ofEpochMilli(it.asOfEpochMillis).atZone(zoneId)),
                    decimal(it.price), it.currency, priceSource(it.source), priceQuality(it.quality),
                )
            }
        }

        private fun rows(header: List<String>, body: () -> List<List<String>>) = listOf(header) + body()
        private fun money(minor: Long, currency: String): String = MoneyMath.toDecimal(minor, currency).toPlainString().replace('.', ',')
        private fun decimal(value: BigDecimal): String = value.toPlainString().replace('.', ',')
        private fun yesNo(value: Boolean) = if (value) "Sí" else "No"
        private fun status(value: MovementStatus) = if (value == MovementStatus.EJECUTADO) "Ejecutado" else "Previsto"
        private fun transactionType(value: TransactionType) = if (value == TransactionType.INGRESO) "Ingreso" else "Gasto"
        private fun categoryKind(value: CategoryKind) = if (value == CategoryKind.INGRESO) "Ingreso" else "Gasto"
        private fun source(value: TransactionSource) = when (value) {
            TransactionSource.MANUAL -> "Manual"
            TransactionSource.IMPORTACION -> "Importación"
            TransactionSource.NOTIFICACION -> "Notificación"
            TransactionSource.RECURRENTE -> "Recurrente"
        }
        private fun budgetPeriod(value: BudgetPeriod) = when (value) {
            BudgetPeriod.SEMANAL -> "Semanal"
            BudgetPeriod.MENSUAL -> "Mensual"
            BudgetPeriod.TRIMESTRAL -> "Trimestral"
            BudgetPeriod.SEMESTRAL -> "Semestral"
            BudgetPeriod.ANUAL -> "Anual"
            BudgetPeriod.UNICO -> "Único"
        }
        private fun operationType(value: OperationType) = when (value) {
            OperationType.COMPRA -> "Compra"
            OperationType.VENTA -> "Venta"
            OperationType.DIVIDENDO -> "Dividendo"
            OperationType.COMISION -> "Comisión"
        }
        private fun assetType(value: AssetType) = value.name.toSpanishWords()
        private fun provider(value: QuoteProvider?) = when (value) {
            QuoteProvider.TWELVE_DATA -> "Twelve Data"
            QuoteProvider.COINGECKO -> "CoinGecko"
            null -> ""
        }
        private fun priceSource(value: PriceSource) = if (value == PriceSource.MANUAL) "Manual" else "Proveedor"
        private fun priceQuality(value: PriceQuality?) = when (value) {
            PriceQuality.RETRASADO -> "Retrasado"
            PriceQuality.CIERRE -> "Cierre"
            null -> ""
        }
        private fun String.toSpanishWords() = lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

        private fun readme(): String = """
            EXPORTACIÓN CSV DE MI PATRIMONIO

            Este ZIP no está cifrado. Contiene datos financieros y debe guardarse en un lugar seguro.

            Formato: UTF-8 con BOM, separador punto y coma, fin de línea CRLF, fechas AAAA-MM-DD,
            horas HH:MM:SS e importes con coma decimal y sin separador de miles.

            cuentas.csv: id; nombre; tipo; divisa; saldo_inicial; saldo_actual (solo movimientos ejecutados a la fecha de exportación); archivada; fecha_creacion.
            categorias.csv: id; nombre; tipo; categoria_padre (nombre); archivada.
            movimientos.csv: id; fecha; tipo; importe positivo; divisa; cuenta; categoria raíz; subcategoria; descripcion; comercio; notas; origen; estado.
            transferencias.csv: id; fecha; cuenta_origen; importe_origen; divisa_origen; cuenta_destino; importe_destino; divisa_destino; categoria; descripcion; estado. Las divisas proceden de las cuentas relacionadas porque la transferencia solo guarda sus identificadores e importes.
            presupuestos.csv: id; nombre; periodo; importe_limite; divisa; fecha_inicio; fecha_fin; umbral_aviso (porcentaje entero); categorias (nombres separados por |); incluye_subcategorias (Sí/No alineados con categorias); archivado.
            carteras.csv: id; nombre; cuenta_predeterminada; fecha_creacion.
            activos.csv: id; nombre; ticker; isin; tipo; mercado; divisa; proveedor_cotizacion; simbolo_cotizacion; archivado. El modelo también conserva MIC de cotización, que no forma parte del formato solicitado.
            operaciones.csv: id; fecha; hora; cartera; activo; tipo; cantidad; precio_unitario; comisiones (o retención en dividendos); divisa; cuenta. Una cuenta vacía indica que la operación no tiene cuenta vinculada.
            dividendos.csv: subconjunto de operaciones de tipo dividendo; fecha; activo; importe_bruto (cantidad × precio_unitario); retencion (campo de comisiones del modelo); importe_neto; divisa; cuenta.
            precios.csv: activo; fecha_hora; precio; divisa; origen; calidad.

            No se incluyen ajustes, secretos, claves, autorizaciones, diagnósticos ni propuestas de notificaciones.
        """.trimIndent() + "\r\n"
    }
}
