package com.mipatrimonio.app.domain.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationTemplateEngineTest {
    @Test fun `clave que contiene importe construye plantilla y reconoce comercios`() {
        val text = "Gastaste 3,75 € en CHANGLONET, S.L."
        val template = NotificationTemplateBuilder.build(
            text,
            text.indexOf("Gastaste")..(text.indexOf(" en ") + " en".lastIndex),
            text.indexOf("CHANGLONET,")..(text.indexOf("CHANGLONET,") + "CHANGLONET,".lastIndex),
        )

        assertEquals(
            listOf(
                TemplateSegment.Literal("Gastaste "),
                TemplateSegment.Amount,
                TemplateSegment.Literal(" en "),
                TemplateSegment.Variable,
            ),
            template.segments,
        )
        val mercadona = NotificationTemplateMatcher.match(template, "Gastaste 12,40 € en MERCADONA")
        assertEquals("MERCADONA", mercadona?.variableText)
        assertEquals(1_240L, mercadona?.amount?.amountMinor)
        assertEquals("EUR", mercadona?.amount?.currency)
        val original = NotificationTemplateMatcher.match(template, text)
        assertEquals("CHANGLONET, S.L.", original?.variableText)
        assertEquals(375L, original?.amount?.amountMinor)
        assertNull(NotificationTemplateMatcher.match(template, "Has recibido 5,00 € de Ana"))
    }

    @Test fun `construye pago con conector y captura variable final`() {
        val text = "Has pagado 1,60 € a LOOMISP*CAFETERIA PARA"
        val template = NotificationTemplateBuilder.build(text, 0..9, text.indexOf("LOOM")..text.lastIndex)
        assertEquals(listOf(TemplateSegment.Literal("Has pagado "), TemplateSegment.Amount,
            TemplateSegment.Literal(" a "), TemplateSegment.Variable), template.segments)
        assertEquals("Loomisp*Cafeteria Para", NotificationTemplateMatcher.match(template, "Prefijo HAS PAGADO  1,60 € a Loomisp*Cafeteria Para!!!")?.variableText)
    }

    @Test fun `tolera sufijo cambiante y variable antes del importe`() {
        val text = "Tu plan de inversión en MSCI World 2x de 150 € se va a ejecutar"
        val variable = text.indexOf("MSCI")..text.indexOf(" de 150") - 1
        val template = NotificationTemplateBuilder.build(text, 0..text.indexOf("MSCI") - 2, variable)
        val match = NotificationTemplateMatcher.match(template, "Aviso: TU PLAN DE INVERSION EN  MSCI World 2x de 150 € mañana")
        assertEquals("MSCI World 2x", match?.variableText)
        assertEquals(15_000L, match?.amount?.amountMinor)
    }

    @Test fun `normaliza comercio y elige estructura mas especifica y antigua en empate`() {
        assertEquals(normalizeVariable("  CAFÉ   París "), normalizeVariable("cafe paris"))
        assertEquals(normalizeVariable("CHANGLONET, S.L"), normalizeVariable("CHANGLONET, S.L."))
        val generic = structure("g", 1, NotificationTemplate(listOf(TemplateSegment.Literal("pagado "), TemplateSegment.Amount, TemplateSegment.Literal(" "), TemplateSegment.Variable)))
        val specific = structure("s", 2, NotificationTemplate(listOf(TemplateSegment.Literal("has pagado "), TemplateSegment.Amount, TemplateSegment.Literal(" "), TemplateSegment.Variable)))
        assertEquals("s", NotificationTemplateMatcher.select(listOf(generic, specific), "has pagado 1 € Tienda")?.first?.id)
        assertEquals("g", NotificationTemplateMatcher.select(listOf(generic, generic.copy(id = "new", createdAt = 2)), "pagado 1 € Tienda")?.first?.id)
    }

    @Test fun `sentidos y signos se reconocen sin coma flotante`() {
        val plus = NotificationAmountRecognizer.first("Abono +10 EUR")!!
        val minus = NotificationAmountRecognizer.first("Cargo -10 EUR")!!
        val unsigned = NotificationAmountRecognizer.first("Cargo 10 EUR")!!
        assertTrue(plus.hasExplicitPlus); assertTrue(minus.hasExplicitMinus); assertFalse(unsigned.hasExplicitPlus)
        assertEquals(1_000L, plus.amountMinor)
        assertEquals(ProposalKind.INGRESO, NotificationDirection.SEGUN_SIGNO.kindFor(plus))
        assertEquals(ProposalKind.GASTO, NotificationDirection.SEGUN_SIGNO.kindFor(minus))
        assertEquals(ProposalKind.GASTO, NotificationDirection.SEGUN_SIGNO.kindFor(unsigned))
        assertEquals(ProposalKind.INGRESO, NotificationDirection.INGRESO.kindFor(unsigned))
    }

    @Test fun `json conserva segmentos y rechaza plantilla sin clave o importe`() {
        val template = NotificationTemplate(listOf(TemplateSegment.Literal("Pago "), TemplateSegment.Amount, TemplateSegment.Variable))
        assertEquals(template, NotificationTemplateJson.decode(NotificationTemplateJson.encode(template)))
        assertFalse(NotificationTemplate(listOf(TemplateSegment.Amount, TemplateSegment.Variable)).isValid)
        assertFalse(NotificationTemplate(listOf(TemplateSegment.Literal("Pago"), TemplateSegment.Variable)).isValid)
    }

    @Test fun `mantiene clave antes del importe y admite importe al principio o final`() {
        val text = "Gastaste 3,75 € en Tienda"
        val variable = text.indexOf("Tienda")..text.lastIndex

        assertEquals(
            listOf(
                TemplateSegment.Literal("Gastaste "), TemplateSegment.Amount,
                TemplateSegment.Literal(" en "), TemplateSegment.Variable,
            ),
            NotificationTemplateBuilder.build(text, 0.."Gastaste".lastIndex, variable).segments,
        )
        assertEquals(
            listOf(
                TemplateSegment.Literal("Gastaste "), TemplateSegment.Amount,
                TemplateSegment.Literal(" en "), TemplateSegment.Variable,
            ),
            NotificationTemplateBuilder.build(text, 0..text.indexOf(" €") + 1, variable).segments,
        )

        val amountFirst = "3,75 € gastados en Tienda"
        assertEquals(
            listOf(
                TemplateSegment.Amount, TemplateSegment.Literal(" gastados en "), TemplateSegment.Variable,
            ),
            NotificationTemplateBuilder.build(
                amountFirst,
                0..(amountFirst.indexOf(" gastados") + " gastados".lastIndex),
                amountFirst.indexOf("Tienda")..amountFirst.lastIndex,
            ).segments,
        )
    }

    @Test fun `amplia clave que corta el importe completo`() {
        val text = "Gastaste 3,75 € en Tienda"
        val amount = NotificationAmountRecognizer.first(text)!!
        val template = NotificationTemplateBuilder.build(
            text,
            (amount.range.first + 2)..(text.indexOf(" en ") + " en".lastIndex),
            text.indexOf("Tienda")..text.lastIndex,
        )

        assertEquals(
            listOf(TemplateSegment.Amount, TemplateSegment.Literal(" en "), TemplateSegment.Variable),
            template.segments,
        )
    }

    @Test fun `informa por separado ausencia de importe variable con importe y solapamiento`() {
        assertBuildError(
            "No se ha detectado ningún importe en la notificación",
            "Gastaste en Tienda",
            0..7,
            12..17,
        )
        val text = "Gastaste 3,75 € en Tienda"
        val amount = NotificationAmountRecognizer.first(text)!!.range
        assertBuildError("El texto variable no puede incluir el importe", text, 0..7, amount)
        assertBuildError("El texto clave y el variable no pueden solaparse", text, 0..7, 5..7)
    }

    @Test fun `usa el primer importe aunque tenga signo mas y haya varias cantidades`() {
        val text = "Saldo +3,75 €; después 9,00 € en Tienda"
        val template = NotificationTemplateBuilder.build(
            text,
            0.."Saldo".lastIndex,
            text.indexOf("Tienda")..text.lastIndex,
        )

        assertEquals(
            listOf(
                TemplateSegment.Literal("Saldo "), TemplateSegment.Amount,
                TemplateSegment.Literal("; después 9,00 € en "), TemplateSegment.Variable,
            ),
            template.segments,
        )
        val match = NotificationTemplateMatcher.match(template, text)
        assertTrue(match?.amount?.hasExplicitPlus == true)
        assertEquals(375L, match?.amount?.amountMinor)
    }

    @Test fun `json anterior sigue decodificando y coincidiendo`() {
        val json = """[{"type":"LITERAL","text":"Gastaste "},{"type":"IMPORTE"},{"type":"LITERAL","text":" en "},{"type":"VARIABLE"}]"""
        val template = NotificationTemplateJson.decode(json)

        val match = NotificationTemplateMatcher.match(template, "Gastaste 12,40 € en MERCADONA")

        assertEquals("MERCADONA", match?.variableText)
        assertEquals(1_240L, match?.amount?.amountMinor)
    }

    private fun assertBuildError(expected: String, text: String, keyRange: IntRange, variableRange: IntRange) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NotificationTemplateBuilder.build(text, keyRange, variableRange)
        }
        assertEquals(expected, error.message)
    }

    private fun structure(id: String, createdAt: Long, template: NotificationTemplate) = NotificationStructure(
        id, "bank", id, template, createdAt = createdAt, updatedAt = createdAt,
    )
}
