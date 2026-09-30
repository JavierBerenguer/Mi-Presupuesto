package com.mipatrimonio.app.data.importer

interface BankCsvAdapter {
    val id: String
    val displayName: String

    fun matches(headers: List<String>): Boolean

    fun parse(table: CsvTable): ImportPreview

    companion object {
        fun detect(headers: List<String>): BankCsvAdapter? =
            listOf(TradeRepublicCsvAdapter(), NeverlessCsvAdapter()).firstOrNull { it.matches(headers) }
    }
}
