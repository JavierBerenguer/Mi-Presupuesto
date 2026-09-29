package com.mipatrimonio.app.domain.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationTemplateEngineTest {
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

    private fun structure(id: String, createdAt: Long, template: NotificationTemplate) = NotificationStructure(
        id, "bank", id, template, createdAt = createdAt, updatedAt = createdAt,
    )
}
