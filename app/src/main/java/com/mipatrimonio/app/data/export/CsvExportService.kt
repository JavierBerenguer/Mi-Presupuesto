package com.mipatrimonio.app.data.export

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.MovementStatus
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
        internal fun create(data: CsvExportData, today: LocalDate, zoneId: ZoneId): CsvExportArchive {
            val accounts = data.accounts.associateBy { it.id }
            val categories = data.categories.associateBy { it.id }
            val rows = linkedMapOf(
                "cuentas.csv" to accountsRows(data, today, zoneId),
                "saldos_cuenta.csv" to accountBalanceRows(data),
                "categorias.csv" to categoryRows(data.categories, categories),
                "movimientos.csv" to transactionRows(data, accounts, categories, today),
                "transferencias.csv" to transferRows(data, accounts, categories, today),
                "presupuestos.csv" to budgetRows(data, categories),
                "estructuras_notificacion.csv" to notificationStructureRows(data),
                "reglas_notificacion.csv" to notificationRuleRows(data),
                "registros_notificacion.csv" to notificationRecordRows(data, zoneId),
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
            rows(listOf("id", "nombre", "nombre_corto", "tipo", "entidad", "divisa", "color", "liquidez", "uso_en_app", "saldo_inicial", "saldo_actual", "archivada", "fecha_creacion")) {
                data.accounts.map { account ->
                    listOf(
                        account.id, account.name, account.shortName.orEmpty(), account.type.name.toSpanishWords(),
                        account.institution.orEmpty(), account.currency, account.color?.toString().orEmpty(),
                        yesNo(account.liquidity), yesNo(account.useInApp),
                        money(account.initialBalanceMinor, account.currency),
                        money(BalanceCalculator.balance(account, data.transactions, data.transfers, today, balanceRecords = data.accountBalances), account.currency),
                        yesNo(account.archived), Instant.ofEpochMilli(account.createdAt).atZone(zoneId).toLocalDate().toString(),
                    )
                }
            }

        private fun accountBalanceRows(data: CsvExportData): List<List<String>> =
            rows(listOf("id", "cuenta_id", "fecha", "saldo", "actualizado", "eliminado")) {
                val currencies = data.accounts.associate { it.id to it.currency }
                data.accountBalances.map { value ->
                    listOf(
                        value.id, value.accountId, value.date.toString(),
                        money(value.balanceMinor, currencies[value.accountId] ?: "EUR"),
                        value.updatedAt.toString(), value.deletedAt?.toString().orEmpty(),
                    )
                }
            }

        private fun categoryRows(values: List<Category>, categories: Map<String, Category>): List<List<String>> =
            rows(listOf("id", "nombre", "categoria_padre", "icono", "archivada")) {
                values.map {
                    listOf(
                        it.id, it.name,
                        it.parentId?.let(categories::get)?.name.orEmpty(), it.icon.orEmpty(), yesNo(it.archived),
                    )
                }
            }

        private fun transactionRows(
            data: CsvExportData,
            accounts: Map<String, com.mipatrimonio.app.domain.model.Account>,
            categories: Map<String, Category>,
            today: LocalDate,
        ): List<List<String>> = rows(
            listOf("id", "fecha", "tipo", "importe", "divisa", "cuenta", "categoria", "subcategoria", "descripcion", "comercio", "notas", "origen", "estado", "grupo"),
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
                    transaction.groupParentId.orEmpty(),
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

        private fun notificationStructureRows(data: CsvExportData): List<List<String>> = rows(
            listOf("id", "aplicacion", "nombre", "plantilla", "sentido", "titulo", "detalle", "categoria_id", "activa", "destino", "producto_por_defecto_id"),
        ) {
            data.notificationStructures.map { value ->
                listOf(value.id, value.packageName, value.name, value.template, value.direction, value.defaultTitle.orEmpty(),
                    value.defaultDetail.orEmpty(), value.defaultCategoryId.orEmpty(), yesNo(value.enabled), value.target, value.defaultProductId.orEmpty())
            }
        }

        private fun notificationRuleRows(data: CsvExportData): List<List<String>> = rows(
            listOf("id", "estructura_id", "clave_variable", "texto_variable", "titulo", "detalle", "categoria_id", "activa", "producto_id"),
        ) {
            data.notificationRules.map { value ->
                listOf(value.id, value.structureId, value.variableKey, value.variableDisplay, value.title.orEmpty(),
                    value.detail.orEmpty(), value.categoryId.orEmpty(), yesNo(value.enabled), value.productId.orEmpty())
            }
        }

        private fun notificationRecordRows(data: CsvExportData, zoneId: ZoneId): List<List<String>> = rows(
            listOf("id", "aplicacion", "fecha", "texto", "importe_minor", "divisa", "estructura_id", "regla_id", "texto_variable", "estado", "apunte_id", "movimiento_sync_id"),
        ) {
            data.notificationRecords.map { value ->
                listOf(value.id, value.packageName, Instant.ofEpochMilli(value.postedAt).atZone(zoneId).toString(), value.text,
                    value.amountMinor?.toString().orEmpty(), value.currency.orEmpty(), value.structureId.orEmpty(), value.ruleId.orEmpty(),
                    value.variableText.orEmpty(), value.status, value.transactionId.orEmpty(), value.syncMovementId.orEmpty())
            }
        }

        private fun rows(header: List<String>, body: () -> List<List<String>>) = listOf(header) + body()
        private fun money(minor: Long, currency: String): String = MoneyMath.toDecimal(minor, currency).toPlainString().replace('.', ',')
        private fun decimal(value: BigDecimal): String = value.toPlainString().replace('.', ',')
        private fun yesNo(value: Boolean) = if (value) "Sí" else "No"
        private fun status(value: MovementStatus) = if (value == MovementStatus.EJECUTADO) "Ejecutado" else "Previsto"
        private fun transactionType(value: TransactionType) = if (value == TransactionType.INGRESO) "Ingreso" else "Gasto"
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
        private fun String.toSpanishWords() = lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

        private fun readme(): String = """
            EXPORTACIÓN CSV DE MI PATRIMONIO

            Este ZIP no está cifrado. Contiene datos financieros y debe guardarse en un lugar seguro.

            Formato: UTF-8 con BOM, separador punto y coma, fin de línea CRLF, fechas AAAA-MM-DD,
            horas HH:MM:SS e importes con coma decimal y sin separador de miles.
            En movimientos.csv, grupo contiene el id del apunte principal o queda vacio si no esta agrupado.

            cuentas.csv: id; nombre; tipo; divisa; saldo_inicial; saldo_actual (solo movimientos ejecutados a la fecha de exportación); archivada; fecha_creacion.
            categorias.csv: id; nombre; categoria_padre (nombre); icono (clave estable); archivada.
            movimientos.csv: id; fecha; tipo; importe positivo; divisa; cuenta; categoria raíz; subcategoria; descripcion; comercio; notas; origen; estado.
            transferencias.csv: id; fecha; cuenta_origen; importe_origen; divisa_origen; cuenta_destino; importe_destino; divisa_destino; categoria; descripcion; estado. Las divisas proceden de las cuentas relacionadas porque la transferencia solo guarda sus identificadores e importes.
            presupuestos.csv: id; nombre; periodo; importe_limite; divisa; fecha_inicio; fecha_fin; umbral_aviso (porcentaje entero); categorias (nombres separados por |); incluye_subcategorias (Sí/No alineados con categorias); archivado.
            estructuras_notificacion.csv, reglas_notificacion.csv y registros_notificacion.csv: configuración y trazabilidad de automatizaciones, incluidos el destino, los productos de inversión y el movimiento sincronizado.

            No se incluyen ajustes, secretos, claves, autorizaciones, diagnósticos ni propuestas pendientes del motor antiguo de notificaciones.
        """.trimIndent() + "\r\n"
    }
}
