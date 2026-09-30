package com.mipatrimonio.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {
    @Test
    fun `conserva comas y comillas escapadas dentro de campos entrecomillados`() {
        val table = CsvParser.parse("nombre,detalle\nEjemplo,\"Texto, con \"\"comillas\"\"\"")

        assertEquals("Texto, con \"comillas\"", table.rows.single()["detalle"])
    }

    @Test
    fun `conserva un salto de linea dentro de un campo y calcula la linea inicial de cada fila`() {
        val table = CsvParser.parse("id,detalle\n1,\"primera\nsegunda\"\n2,final")

        assertEquals("primera\nsegunda", table.rows[0]["detalle"])
        assertEquals(2, table.rows[0].lineNumber)
        assertEquals(4, table.rows[1].lineNumber)
    }

    @Test
    fun `ignora BOM inicial y acepta terminaciones CRLF`() {
        val table = CsvParser.parse("\uFEFFid,nombre\r\n1,Ejemplo\r\n")

        assertEquals(listOf("id", "nombre"), table.headers)
        assertEquals("Ejemplo", table.rows.single()["nombre"])
        assertEquals(2, table.rows.single().lineNumber)
    }

    @Test
    fun `fichero vacio produce tabla vacia`() {
        val table = CsvParser.parse("")

        assertTrue(table.headers.isEmpty())
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun `fichero con solo cabecera no produce filas`() {
        val table = CsvParser.parse("id,nombre")

        assertEquals(listOf("id", "nombre"), table.headers)
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun `rellena campos ausentes y descarta campos sobrantes`() {
        val table = CsvParser.parse("a,b,c\n1,2\n3,4,5,6")

        assertEquals("", table.rows[0]["c"])
        assertEquals(mapOf("a" to "3", "b" to "4", "c" to "5"), table.rows[1].values)
    }

    @Test
    fun `recorta campos sin comillas y conserva espacios entre comillas`() {
        val table = CsvParser.parse("a,b\n  valor  ,\"  valor  \"")

        assertEquals("valor", table.rows.single()["a"])
        assertEquals("  valor  ", table.rows.single()["b"])
        assertEquals("", table.rows.single()["inexistente"])
    }

    @Test
    fun `ignora lineas completamente vacias`() {
        val table = CsvParser.parse("\n\nid\n\n1\n\n")

        assertEquals(listOf("id"), table.headers)
        assertEquals(5, table.rows.single().lineNumber)
    }
}
