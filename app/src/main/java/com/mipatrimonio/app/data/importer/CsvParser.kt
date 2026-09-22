package com.mipatrimonio.app.data.importer

data class CsvTable(
    val headers: List<String>,
    val rows: List<CsvRow>,
)

data class CsvRow(
    val lineNumber: Int,
    val values: Map<String, String>,
) {
    operator fun get(column: String): String = values[column].orEmpty()
}

object CsvParser {
    fun parse(text: String, separator: Char = ','): CsvTable {
        val records = parseRecords(text.removePrefix("\uFEFF"), separator)
        if (records.isEmpty()) return CsvTable(emptyList(), emptyList())

        val headers = records.first().fields
        val rows = records.drop(1).map { record ->
            val values = LinkedHashMap<String, String>(headers.size)
            headers.forEachIndexed { index, header ->
                values[header] = record.fields.getOrElse(index) { "" }
            }
            CsvRow(lineNumber = record.lineNumber, values = values)
        }
        return CsvTable(headers = headers, rows = rows)
    }

    private fun parseRecords(text: String, separator: Char): List<ParsedRecord> {
        val records = mutableListOf<ParsedRecord>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var fieldWasQuoted = false
        var inQuotes = false
        var afterClosingQuote = false
        var recordHasCharacters = false
        var physicalLine = 1
        var recordStartLine = 1
        var index = 0

        fun finishField() {
            fields += if (fieldWasQuoted) field.toString() else field.toString().trim()
            field.clear()
            fieldWasQuoted = false
            afterClosingQuote = false
        }

        fun finishRecord() {
            finishField()
            if (recordHasCharacters) {
                records += ParsedRecord(fields.toList(), recordStartLine)
            }
            fields.clear()
            recordHasCharacters = false
        }

        while (index < text.length) {
            val character = text[index]
            if (inQuotes) {
                when {
                    character == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index += 2
                    }
                    character == '"' -> {
                        inQuotes = false
                        afterClosingQuote = true
                        index++
                    }
                    character == '\r' -> {
                        if (index + 1 < text.length && text[index + 1] == '\n') {
                            field.append("\r\n")
                            index += 2
                        } else {
                            field.append('\r')
                            index++
                        }
                        physicalLine++
                    }
                    character == '\n' -> {
                        field.append('\n')
                        physicalLine++
                        index++
                    }
                    else -> {
                        field.append(character)
                        index++
                    }
                }
                continue
            }

            when {
                character == separator -> {
                    recordHasCharacters = true
                    finishField()
                    index++
                }
                character == '\r' || character == '\n' -> {
                    finishRecord()
                    if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index += 2
                    } else {
                        index++
                    }
                    physicalLine++
                    recordStartLine = physicalLine
                }
                character == '"' && field.isEmpty() && !afterClosingQuote -> {
                    recordHasCharacters = true
                    fieldWasQuoted = true
                    inQuotes = true
                    index++
                }
                afterClosingQuote && character.isWhitespace() -> index++
                else -> {
                    recordHasCharacters = true
                    field.append(character)
                    index++
                }
            }
        }

        if (recordHasCharacters || fields.isNotEmpty() || field.isNotEmpty()) {
            finishRecord()
        }
        return records
    }

    private data class ParsedRecord(
        val fields: List<String>,
        val lineNumber: Int,
    )
}
