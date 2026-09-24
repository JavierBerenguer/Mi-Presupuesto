package com.mipatrimonio.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEngineTest {
    private val generic = GenericSpanishParser()
    private val engine = NotificationEngine(listOf(generic))

    @Test
    fun `app no autorizada no llega a interpretar el texto`() {
        var parsed = false
        val observingParser = object : BankNotificationParser {
            override val parserId = "observer"
            override fun accepts(packageName: String) = true
            override fun parse(notification: BankNotification): ParsedNotification? {
                parsed = true
                return null
            }
        }
        val guardedEngine = NotificationEngine(listOf(observingParser, generic))

        listOf("Compra de 2,00 €", "Aviso sin importe").forEach { text ->
            val outcome = guardedEngine.process(notification(text), emptySet(), { null }, emptyList())
            assertEquals(NotificationOutcome.AppNoAutorizada, outcome)
        }
        assertTrue(!parsed)
    }

    @Test
    fun `texto sin importe devuelve resultado no interpretable`() {
        val outcome = engine.process(notification("Tu extracto está disponible"), setOf(PACKAGE), { null }, emptyList())
        assertEquals(
            NotificationOutcome.NoInterpretable(NoInterpretableReason.SIN_IMPORTE, false),
            outcome,
        )
    }

    @Test
    fun `distingue ausencia de texto contenido sensible y divisa desconocida`() {
        assertEquals(
            NotificationOutcome.NoInterpretable(NoInterpretableReason.SIN_TEXTO, false),
            engine.process(notification(""), setOf(PACKAGE), { null }, emptyList()),
        )
        assertEquals(
            NotificationOutcome.NoInterpretable(NoInterpretableReason.CONTENIDO_SENSIBLE, true),
            engine.process(notification("Código de verificación 123456 para 10 EUR"), setOf(PACKAGE), { null }, emptyList()),
        )
        assertEquals(
            NotificationOutcome.NoInterpretable(NoInterpretableReason.DIVISA_NO_RECONOCIDA, true),
            engine.process(notification("Compra de 10 CHF"), setOf(PACKAGE), { null }, emptyList()),
        )
    }

    @Test
    fun `importe sin palabras clave crea propuesta de gasto con confianza baja`() {
        val outcome = engine.process(notification("Saldo 2,00 €"), setOf(PACKAGE), { null }, emptyList())
        val proposal = (outcome as NotificationOutcome.Nueva).propuesta

        assertEquals(ProposalKind.GASTO, proposal.kind)
        assertEquals(Confidence.BAJA, proposal.confidence)
    }

    @Test
    fun `bizum nunca genera transferencia y mantiene confianza media`() {
        listOf(
            "Bizum de Ana +3 €" to ProposalKind.INGRESO,
            "Bizum de Ana 7 €" to ProposalKind.GASTO,
            "Bizum de Ana -7 €" to ProposalKind.GASTO,
        ).forEach { (text, expectedKind) ->
            val outcome = engine.process(notification(text), setOf(PACKAGE), { "a1" }, emptyList())
            val proposal = (outcome as NotificationOutcome.Nueva).propuesta

            assertEquals(text, expectedKind, proposal.kind)
            assertEquals(text, Confidence.MEDIA, proposal.confidence)
        }
    }

    @Test
    fun `plan de inversion permanece como transferencia`() {
        val outcome = engine.process(
            notification("Plan de inversión 50 €"),
            setOf(PACKAGE),
            { "a1" },
            emptyList(),
        )
        val proposal = (outcome as NotificationOutcome.Nueva).propuesta

        assertEquals(ProposalKind.TRANSFERENCIA, proposal.kind)
        assertEquals(Confidence.BAJA, proposal.confidence)
    }

    @Test
    fun `crea propuesta nueva con cuenta nullable`() {
        val outcome = engine.process(notification("Compra de 2,00 €"), setOf(PACKAGE), { null }, emptyList())
        val proposal = (outcome as NotificationOutcome.Nueva).propuesta
        assertEquals(200L, proposal.amountMinor)
        assertEquals(null, proposal.accountId)
    }

    @Test
    fun `deduplica dentro de cinco minutos y admite fuera de la ventana`() {
        val existing = draft(id = "p1", postedAt = NOW - 5 * 60 * 1_000L)
        val duplicate = engine.process(notification("Compra de 2,00 €"), setOf(PACKAGE), { "a1" }, listOf(existing))
        assertEquals(NotificationOutcome.Duplicada("p1"), duplicate)

        val old = existing.copy(postedAt = NOW - 5 * 60 * 1_000L - 1)
        val fresh = engine.process(notification("Compra de 2,00 €"), setOf(PACKAGE), { "a1" }, listOf(old))
        assertTrue(fresh is NotificationOutcome.Nueva)
    }

    @Test
    fun `kind diferente no se considera duplicado`() {
        val expense = draft(id = "p1", postedAt = NOW, kind = ProposalKind.GASTO)
        val outcome = engine.process(notification("Devolución de 2,00 €"), setOf(PACKAGE), { "a1" }, listOf(expense))
        assertTrue(outcome is NotificationOutcome.Nueva)
    }

    @Test
    fun `usa el primer parser que acepta el paquete`() {
        val first = fixedParser("first", ProposalKind.INGRESO)
        val second = fixedParser("second", ProposalKind.GASTO)
        val orderedEngine = NotificationEngine(listOf(first, second, generic))

        val proposal = (orderedEngine.process(notification("irrelevante"), setOf(PACKAGE), { null }, emptyList()) as NotificationOutcome.Nueva).propuesta

        assertEquals("first", proposal.parserId)
        assertEquals(ProposalKind.INGRESO, proposal.kind)
    }

    private fun fixedParser(id: String, kind: ProposalKind) = object : BankNotificationParser {
        override val parserId = id
        override fun accepts(packageName: String) = true
        override fun parse(notification: BankNotification) = ParsedNotification(kind, 200L, "EUR", null, Confidence.MEDIA, id)
    }

    private fun notification(text: String) = BankNotification(PACKAGE, "", text, NOW)

    private fun draft(id: String, postedAt: Long, kind: ProposalKind = ProposalKind.GASTO) = PendingProposalDraft(
        id, PACKAGE, "a1", kind, 200L, "EUR", null, Confidence.MEDIA, generic.parserId, postedAt,
    )

    private companion object {
        const val PACKAGE = "app.bank"
        const val NOW = 1_000_000L
    }
}
