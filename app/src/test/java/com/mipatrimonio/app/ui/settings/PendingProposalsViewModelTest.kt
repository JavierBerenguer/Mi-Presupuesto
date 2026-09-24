package com.mipatrimonio.app.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.domain.notifications.ProposalStatus
import java.time.LocalDate
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
    private var nextResultId = 0

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
    fun `confirma un gasto con categoria comercio y descripcion`() = runTest {
        ledger.saveAccount(account("account-1"))
        ledger.saveCategory(category("shopping", CategoryKind.GASTO))
        ledger.saveCategory(category("books", CategoryKind.GASTO, parentId = "shopping"))
        val proposal = proposal("Compra de 12,50 EUR en Librería", "account-1")
        val viewModel = readyViewModel()

        viewModel.requestConfirmation(proposal.id)
        viewModel.confirm(
            ProposalConfirmation(
                kind = ProposalKind.GASTO,
                accountId = "account-1",
                categoryId = "books",
                merchant = "Librería Centro",
                description = "Libro técnico",
            ),
        )
        advanceUntilIdle()

        val transaction = ledger.transactions.first().single()
        assertEquals(TransactionType.GASTO, transaction.type)
        assertEquals(1_250L, transaction.amountMinor)
        assertEquals("account-1", transaction.accountId)
        assertEquals("books", transaction.categoryId)
        assertEquals("Librería Centro", transaction.merchant)
        assertEquals("Libro técnico", transaction.description)
        assertEquals(TransactionSource.NOTIFICACION, transaction.source)
        assertEquals(ProposalStatus.CONFIRMADA.name, storedStatus(proposal.id).first)
        assertEquals(transaction.id, storedStatus(proposal.id).second)
    }

    @Test
    fun `permite cambiar gasto a ingreso e ingreso a gasto`() = runTest {
        ledger.saveAccount(account("account-1"))
        var proposal = proposal("Compra de 10 EUR", "account-1")
        var viewModel = readyViewModel()

        viewModel.requestConfirmation(proposal.id)
        viewModel.confirm(ProposalConfirmation(ProposalKind.INGRESO, "account-1"))
        advanceUntilIdle()
        assertEquals(TransactionType.INGRESO, ledger.transactions.first().single().type)

        proposal = proposal("Abono de 25 EUR", "account-1")
        viewModel = readyViewModel()
        viewModel.requestConfirmation(proposal.id)
        viewModel.confirm(ProposalConfirmation(ProposalKind.GASTO, "account-1"))
        advanceUntilIdle()
        assertEquals(
            TransactionType.GASTO,
            ledger.transactions.first().first { it.id == "result-2" }.type,
        )
    }

    @Test
    fun `transferencia crea Transfer y no Transaction ni gasto o ingreso`() = runTest {
        ledger.saveAccount(account("from", initialBalanceMinor = 50_000L))
        ledger.saveAccount(account("to"))
        val proposal = proposal("Transferencia de 30 EUR", "from")
        val viewModel = readyViewModel()

        viewModel.requestConfirmation(proposal.id)
        viewModel.confirm(
            ProposalConfirmation(
                kind = ProposalKind.TRANSFERENCIA,
                accountId = "from",
                destinationAccountId = "to",
                description = "Ahorro",
            ),
        )
        advanceUntilIdle()

        val transfer = ledger.transfers.first().single()
        val transactions = ledger.transactions.first()
        assertEquals("from", transfer.fromAccountId)
        assertEquals("to", transfer.toAccountId)
        assertEquals(3_000L, transfer.fromAmountMinor)
        assertEquals(3_000L, transfer.toAmountMinor)
        assertEquals("Ahorro", transfer.description)
        assertTrue(transactions.isEmpty())
        assertEquals(transfer.id, storedStatus(proposal.id).second)

        val range = LocalDate.of(1970, 1, 1)..LocalDate.of(1970, 1, 31)
        val totals = StatsCalculator.totals(transactions, "EUR", range)
        val budget = Budget("budget", null, BudgetPeriod.MENSUAL, 10_000L, "EUR", false)
        assertEquals(0L, totals.incomeMinor)
        assertEquals(0L, totals.expenseMinor)
        assertEquals(0L, BudgetCalculator.status(budget, transactions, emptyList(), range.start).spentMinor)
    }

    @Test
    fun `rechaza cuentas iguales divisa distinta y cuenta archivada`() = runTest {
        ledger.saveAccount(account("eur"))
        ledger.saveAccount(account("usd", currency = "USD"))
        ledger.saveAccount(account("archived", archived = true))
        val proposal = proposal("Transferencia de 30 EUR", "eur")
        val viewModel = readyViewModel()
        viewModel.requestConfirmation(proposal.id)
        assertEquals(setOf("eur", "usd"), viewModel.uiState.value.activeAccounts.map { it.id }.toSet())

        viewModel.confirm(ProposalConfirmation(ProposalKind.TRANSFERENCIA, "eur"))
        assertError(viewModel, PendingProposalError.DestinationAccountRequired)

        viewModel.confirm(ProposalConfirmation(ProposalKind.TRANSFERENCIA, "eur", "eur"))
        assertError(viewModel, PendingProposalError.AccountsMustDiffer)

        viewModel.confirm(ProposalConfirmation(ProposalKind.TRANSFERENCIA, "eur", "usd"))
        assertError(viewModel, PendingProposalError.CurrencyMismatch)

        viewModel.confirm(ProposalConfirmation(ProposalKind.GASTO, "usd"))
        assertError(viewModel, PendingProposalError.CurrencyMismatch)

        viewModel.confirm(ProposalConfirmation(ProposalKind.GASTO, "archived"))
        assertError(viewModel, PendingProposalError.AccountRequired)
        assertTrue(ledger.transactions.first().isEmpty())
        assertTrue(ledger.transfers.first().isEmpty())
    }

    @Test
    fun `rechaza categoria archivada o de otro tipo`() = runTest {
        ledger.saveAccount(account("account"))
        ledger.saveCategory(category("archived", CategoryKind.GASTO, archived = true))
        ledger.saveCategory(category("income", CategoryKind.INGRESO))
        val proposal = proposal("Compra de 10 EUR", "account")
        val viewModel = readyViewModel()
        viewModel.requestConfirmation(proposal.id)

        assertEquals(listOf("income"), viewModel.uiState.value.activeCategories.map { it.id })
        assertEquals(listOf("account"), viewModel.uiState.value.activeAccounts.map { it.id })

        viewModel.confirm(ProposalConfirmation(ProposalKind.GASTO, "account", categoryId = "archived"))
        assertError(viewModel, PendingProposalError.CategoryUnavailable)

        viewModel.confirm(ProposalConfirmation(ProposalKind.GASTO, "account", categoryId = "income"))
        assertError(viewModel, PendingProposalError.CategoryUnavailable)
        assertTrue(ledger.transactions.first().isEmpty())
    }

    @Test
    fun `fallo al marcar confirmada revierte Transaction y Transfer`() = runTest {
        ledger.saveAccount(account("from"))
        ledger.saveAccount(account("to"))
        val expense = proposal("Compra de 10 EUR", "from")
        installConfirmationFailureTrigger()
        var viewModel = readyViewModel()

        viewModel.requestConfirmation(expense.id)
        viewModel.confirm(ProposalConfirmation(ProposalKind.GASTO, "from"))
        advanceUntilIdle()
        assertTrue(ledger.transactions.first().isEmpty())
        assertNotNull(viewModel.uiState.first { it.error is PendingProposalError.Repository }.error)
        assertEquals(ProposalStatus.PENDIENTE.name, storedStatus(expense.id).first)

        removeConfirmationFailureTrigger()
        val transfer = proposal("Transferencia de 20 EUR", "from")
        installConfirmationFailureTrigger()
        viewModel = readyViewModel()
        viewModel.requestConfirmation(transfer.id)
        viewModel.confirm(ProposalConfirmation(ProposalKind.TRANSFERENCIA, "from", "to"))
        advanceUntilIdle()
        assertTrue(ledger.transfers.first().isEmpty())
        assertNotNull(viewModel.uiState.first { it.error is PendingProposalError.Repository }.error)
        assertEquals(ProposalStatus.PENDIENTE.name, storedStatus(transfer.id).first)
    }

    @Test
    fun `doble confirmacion crea un solo registro`() = runTest {
        ledger.saveAccount(account("account"))
        val proposal = proposal("Compra de 10 EUR", "account")
        val viewModel = readyViewModel()
        viewModel.requestConfirmation(proposal.id)
        val confirmation = ProposalConfirmation(ProposalKind.GASTO, "account")

        viewModel.confirm(confirmation)
        viewModel.confirm(confirmation)
        advanceUntilIdle()

        assertEquals(1, ledger.transactions.first().size)
        assertEquals(1, nextResultId)
    }

    @Test
    fun `confirmar siempre abre revision y prellenado se conserva en la propuesta`() = runTest {
        ledger.saveAccount(account("account"))
        val proposal = proposal("Compra de 10 EUR en Mercado", "account")
        val viewModel = readyViewModel()

        viewModel.requestConfirmation(proposal.id)

        val state = viewModel.uiState.value
        assertEquals(proposal.id, state.reviewProposalId)
        assertEquals("account", state.reviewProposal?.accountId)
        assertEquals("Mercado", state.reviewProposal?.merchant)
        assertTrue(ledger.transactions.first().isEmpty())
    }

    @Test
    fun `descartar cambia el estado sin crear movimiento`() = runTest {
        val proposal = proposal("Abono de 25 EUR", null)
        val viewModel = readyViewModel()

        viewModel.discard(proposal.id)
        advanceUntilIdle()

        assertTrue(ledger.transactions.first().isEmpty())
        assertEquals(ProposalStatus.DESCARTADA.name, storedStatus(proposal.id).first)
        assertNull(storedStatus(proposal.id).second)
    }

    private suspend fun readyViewModel(): PendingProposalsViewModel {
        val viewModel = PendingProposalsViewModel(
            notifications = notifications,
            ledger = ledger,
            clock = { 60_000L },
            idFactory = { "result-${++nextResultId}" },
            zoneId = ZoneId.of("Europe/Madrid"),
        )
        viewModel.uiState.first { !it.isLoading && it.proposals.isNotEmpty() }
        return viewModel
    }

    private suspend fun proposal(text: String, accountId: String?): PendingProposal {
        notifications.setAuthorized(PACKAGE, true, accountId)
        val outcome = notifications.ingest(BankNotification(PACKAGE, "Aviso", text, 1_000L))
        return (outcome as NotificationOutcome.Nueva).let {
            notifications.pendingProposals.first().single { proposal -> proposal.id == it.propuesta.id }
        }
    }

    private fun account(
        id: String,
        currency: String = "EUR",
        archived: Boolean = false,
        initialBalanceMinor: Long = 0,
    ) = Account(
        id = id,
        name = "Cuenta $id",
        type = AccountType.CORRIENTE,
        currency = currency,
        initialBalanceMinor = initialBalanceMinor,
        archived = archived,
        createdAt = 1L,
    )

    private fun category(
        id: String,
        kind: CategoryKind,
        archived: Boolean = false,
        parentId: String? = null,
    ) = Category(
        id = id,
        name = "Categoría $id",
        kind = kind,
        parentId = parentId,
        colorArgb = 0,
        archived = archived,
    )

    private fun installConfirmationFailureTrigger() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_confirmation BEFORE UPDATE ON pending_proposal " +
                "WHEN NEW.status = 'CONFIRMADA' BEGIN SELECT RAISE(ABORT, 'fallo confirmado'); END",
        )
    }

    private fun removeConfirmationFailureTrigger() {
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_confirmation")
    }

    private suspend fun assertError(
        viewModel: PendingProposalsViewModel,
        expected: PendingProposalError,
    ) {
        assertEquals(expected, viewModel.uiState.first { it.error == expected }.error)
    }

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
