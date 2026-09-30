package com.mipatrimonio.app.data.export

import java.io.OutputStream
import java.nio.charset.StandardCharsets

object CsvWriter {
    private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun write(output: OutputStream, rows: Sequence<List<String>>) {
        output.write(bom)
        rows.forEach { row ->
            output.write(row.joinToString(";") { escape(it) }.toByteArray(StandardCharsets.UTF_8))
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
        }
    }

    internal fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
