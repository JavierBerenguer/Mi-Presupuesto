package com.mipatrimonio.app.ui.settings

import com.mipatrimonio.app.domain.notifications.Confidence
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.domain.notifications.ProposalStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingProposalRulesTest {
    @Test
    fun `gastos e ingresos se pueden confirmar pero transferencias no`() {
        assertTrue(canConfirm(proposal(ProposalKind.GASTO)))
        assertTrue(canConfirm(proposal(ProposalKind.INGRESO)))
        assertFalse(canConfirm(proposal(ProposalKind.TRANSFERENCIA)))
    }

    private fun proposal(kind: ProposalKind) = PendingProposal(
        id = "proposal",
        packageName = "app.bank",
        accountId = "account",
        kind = kind,
        amountMinor = 1_000L,
        currency = "EUR",
        merchant = null,
        confidence = Confidence.MEDIA,
        parserId = "test",
        postedAt = 1L,
        status = ProposalStatus.PENDIENTE,
        resultingTransactionId = null,
        createdAt = 1L,
    )
}
