package com.mipatrimonio.app.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.ProposalStatus
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingProposalsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var notifications: NotificationRepository
    private var nextTransactionId = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 50_000L }
        notifications = NotificationRepository(
            db,
            NotificationEngine(listOf(GenericSpanishParser())),
            clock = { 40_000L },
            idFactory = { "proposal" },
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `confirma un gasto con cuenta y marca la propuesta`() = runTest {
        ledger.saveAccount(account("account-1"))
        val proposal = proposal("Compra de 12,50 EUR en Librería", "account-1")
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading && it.proposals.size == 1 }

        viewModel.requestConfirmation(proposal.id)
        advanceUntilIdle()

        val transaction = ledger.transactions.first().single()
        assertEquals(TransactionType.GASTO, transaction.type)
        assertEquals(1_250L, transaction.amountMinor)
        assertEquals("account-1", transaction.accountId)
        assertEquals("Librería", transaction.merchant)
        assertEquals(TransactionSource.NOTIFICACION, transaction.source)
        assertTrue(notifications.pendingProposals.first().isEmpty())
        assertEquals(ProposalStatus.CONFIRMADA.name, storedStatus(proposal.id).first)
        assertEquals(transaction.id, storedStatus(proposal.id).second)
    }

    @Test
    fun `sin cuenta bloquea la confirmacion hasta elegir una`() = runTest {
        ledger.saveAccount(account("account-1"))
        val proposal = proposal("Pago de 10 EUR", null)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading && it.proposals.size == 1 }

        viewModel.requestConfirmation(proposal.id)
        advanceUntilIdle()
        var state = viewModel.uiState.first { it.accountSelectionProposalId == proposal.id }
        assertTrue(ledger.transactions.first().isEmpty())

        viewModel.confirmWithAccount(null)
        advanceUntilIdle()
        state = viewModel.uiState.first { it.error == PendingProposalError.AccountRequired }
        assertNotNull(state.accountSelectionProposal)
        assertTrue(ledger.transactions.first().isEmpty())

        viewModel.confirmWithAccount("account-1")
        advanceUntilIdle()
        assertEquals("account-1", ledger.transactions.first().single().accountId)
        assertTrue(notifications.pendingProposals.first().isEmpty())
    }

    @Test
    fun `descartar cambia el estado sin crear movimiento`() = runTest {
        val proposal = proposal("Abono de 25 EUR", null)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading && it.proposals.size == 1 }

        viewModel.discard(proposal.id)
        advanceUntilIdle()

        assertTrue(notifications.pendingProposals.first().isEmpty())
        assertTrue(ledger.transactions.first().isEmpty())
        assertEquals(ProposalStatus.DESCARTADA.name, storedStatus(proposal.id).first)
        assertNull(storedStatus(proposal.id).second)
    }

    @Test
    fun `una transferencia no se puede confirmar`() = runTest {
        ledger.saveAccount(account("account-1"))
        val proposal = proposal("Transferencia de 30 EUR", "account-1")
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading && it.proposals.size == 1 }

        assertFalse(canConfirm(proposal))
        viewModel.requestConfirmation(proposal.id)
        viewModel.confirmWithAccount("account-1")
        advanceUntilIdle()

        assertTrue(ledger.transactions.first().isEmpty())
        assertNull(viewModel.uiState.value.accountSelectionProposalId)
        assertEquals(listOf(proposal.id), notifications.pendingProposals.first().map { it.id })
    }

    @Test
    fun `cuenta archivada muestra error y conserva la propuesta`() = runTest {
        ledger.saveAccount(account("account-1"))
        val proposal = proposal("Compra de 10 EUR", "account-1")
        ledger.setAccountArchived("account-1", true)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading && it.proposals.size == 1 }

        viewModel.requestConfirmation(proposal.id)
        advanceUntilIdle()

        val error = viewModel.uiState.first { it.error is PendingProposalError.Repository }.error
        assertEquals("La cuenta está archivada", (error as PendingProposalError.Repository).message)
        assertTrue(ledger.transactions.first().isEmpty())
        assertEquals(listOf(proposal.id), notifications.pendingProposals.first().map { it.id })
    }

    private fun viewModel() = PendingProposalsViewModel(
        notifications = notifications,
        ledger = ledger,
        clock = { 60_000L },
        idFactory = { "transaction-${++nextTransactionId}" },
        zoneId = ZoneId.of("Europe/Madrid"),
    )

    private suspend fun proposal(text: String, accountId: String?): PendingProposal {
        notifications.setAuthorized(PACKAGE, true, accountId)
        val outcome = notifications.ingest(BankNotification(PACKAGE, "Aviso", text, 1_000L))
        return (outcome as NotificationOutcome.Nueva).let {
            notifications.pendingProposals.first().single { proposal -> proposal.id == it.propuesta.id }
        }
    }

    private fun account(id: String) = Account(
        id = id,
        name = "Cuenta $id",
        type = AccountType.CORRIENTE,
        currency = "EUR",
        initialBalanceMinor = 0,
        archived = false,
        createdAt = 1L,
    )

    private fun storedStatus(id: String): Pair<String, String?> = db.openHelper.readableDatabase.query(
        "SELECT status, resultingTransactionId FROM pending_proposal WHERE id = ?",
        arrayOf(id),
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getString(0) to cursor.getString(1)
    }

    private companion object {
        const val PACKAGE = "app.bank"
    }
}
