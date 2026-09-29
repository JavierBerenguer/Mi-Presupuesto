package com.mipatrimonio.app.ui.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationDirection
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationRecordStatus
import com.mipatrimonio.app.domain.notifications.NotificationRuleValues
import com.mipatrimonio.app.testutil.awaitValue
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class AutomationViewModelsTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var notifications: NotificationRepository
    private var id = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 20_000L }
        notifications = NotificationRepository(
            db, NotificationEngine(listOf(GenericSpanishParser())), clock = { 10_000L + id },
            idFactory = { "id-${++id}" }, zoneId = ZoneId.of("UTC"),
        )
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `pendientes ordena cuenta descarta y actualiza contador`() = runTest {
        authorize()
        ingest("Formato uno 1 EUR", 1_000)
        ingest("Formato dos 2 EUR", 2_000)
        notifications.setAuthorized("app.unlinked", true, null)
        val newest = (notifications.ingest(BankNotification("app.unlinked", "", "Formato tres 3 EUR", 3_000)) as
            com.mipatrimonio.app.domain.notifications.NotificationOutcome.Registrada).recordId
        val viewModel = PendingAutomationViewModel(notifications)

        val initial = viewModel.uiState.first { !it.isLoading && it.count == 3 }
        assertEquals(newest, initial.records.first().id)
        assertTrue(initial.records.any { it.status == NotificationRecordStatus.PENDIENTE_CUENTA })
        viewModel.discard(newest)

        val final = viewModel.uiState.first { !it.isLoading && it.count == 2 }
        assertEquals(NotificationRecordStatus.DESCARTADA, notifications.records(NotificationRecordStatus.DESCARTADA).first().single().status)
        assertEquals(2, final.count)
    }

    @Test fun `ensenanza selecciona palabras valida previsualiza guarda y reprocesa`() = runTest {
        authorize()
        val seed = ingest("Pago 10 EUR en Uno", 1_000)
        ingest("Pago 20 EUR en Uno", 400_000)
        val viewModel = TeachStructureViewModel(seed, notifications, ledger)
        viewModel.uiState.first { !it.isLoading }

        viewModel.selectWord(0)
        assertEquals(TeachValidationError.MISSING_VARIABLE, viewModel.uiState.first { it.keyRange != null }.validationError)
        viewModel.selectMode(SelectionMode.VARIABLE)
        viewModel.selectWord(4)
        viewModel.setName("Pago con tarjeta")

        val preview = viewModel.uiState.first { it.preview != null && it.validationError == null }
        assertEquals("Uno", preview.preview?.variableText)
        assertFalse(preview.tokens[1].range == preview.keyRange)
        viewModel.save()

        val saved = viewModel.uiState.first { it.savedAdditionalCount != null }
        assertEquals(1, saved.savedAdditionalCount)
        assertEquals(2, notifications.records(NotificationRecordStatus.AUTOMATIZADA).first().size)
    }

    @Test fun `ensenanza impide solapamiento y no permite seleccionar importe`() = runTest {
        authorize()
        val seed = ingest("Pago 10 EUR en Uno", 1_000)
        val viewModel = TeachStructureViewModel(seed, notifications, ledger)
        viewModel.uiState.first { !it.isLoading }

        viewModel.selectWord(1)
        assertNull(viewModel.uiState.first { !it.isLoading }.keyRange)
        viewModel.selectWord(0)
        viewModel.selectMode(SelectionMode.VARIABLE)
        viewModel.selectWord(0)
        viewModel.setName("Pago")
        val invalid = viewModel.uiState.first { it.variableRange != null }
        assertEquals(TeachValidationError.OVERLAP, invalid.validationError)
        assertFalse(invalid.canSave)
    }

    @Test fun `configurar comercio muestra estructura crea regla y automatiza`() = runTest {
        authorize()
        val seed = ingest("Pago 10 EUR en Uno", 1_000)
        notifications.createStructureFromRecord(seed, "Pago", 0..3, 15..17, NotificationDirection.GASTO)
        val pending = ingest("Pago 20 EUR en Dos", 100_000)
        val viewModel = ConfigureRuleViewModel(pending, notifications, ledger)

        val initial = viewModel.uiState.first { !it.isLoading && it.preview != null }
        assertEquals("Pago", initial.structure?.name)
        assertEquals("Dos", initial.record?.variableText)
        viewModel.setValues(NotificationRuleValues(title = "Cafetería Dos", detail = "Desayuno"))
        viewModel.save()

        viewModel.uiState.first { it.savedAdditionalCount != null }
        val transaction = ledger.transactions.awaitValue { rows -> rows.any { it.description == "Cafetería Dos" } }
            .first { it.description == "Cafetería Dos" }
        assertEquals("Desayuno", transaction.notes)
    }

    @Test fun `gestiona edicion desactivacion y eliminacion de estructuras y reglas`() = runTest {
        authorize()
        val seed = ingest("Pago 10 EUR en Uno", 1_000)
        val structure = notifications.createStructureFromRecord(seed, "Pago", 0..3, 15..17, NotificationDirection.GASTO)
        val viewModel = StructuresViewModel(notifications, ledger)
        val initial = viewModel.uiState.first { !it.isLoading && it.groups.isNotEmpty() }
        val rule = initial.groups.single().structures.single().rules.single()

        viewModel.saveStructure(structure.copy(name = "Tarjeta"))
        assertEquals("Tarjeta", notifications.structures.awaitValue { it.singleOrNull()?.name == "Tarjeta" }.single().name)
        viewModel.setStructureEnabled(structure.id, false)
        assertFalse(notifications.structures.awaitValue { it.singleOrNull()?.enabled == false }.single().enabled)
        viewModel.saveRule(rule.copy(variableDisplay = "Uno editado", title = "Comercio"))
        val editedRule = notifications.rules(structure.id).awaitValue { it.singleOrNull()?.title == "Comercio" }.single()
        assertEquals("Comercio", editedRule.title)
        assertEquals("Uno editado", editedRule.variableDisplay)
        viewModel.setRuleEnabled(rule.id, false)
        assertFalse(notifications.rules(structure.id).awaitValue { it.singleOrNull()?.enabled == false }.single().enabled)
        viewModel.deleteRule(rule.id)
        assertTrue(notifications.rules(structure.id).awaitValue { it.isEmpty() }.isEmpty())
        viewModel.deleteStructure(structure.id)
        assertTrue(notifications.structures.awaitValue { it.isEmpty() }.isEmpty())
    }

    private suspend fun authorize() {
        ledger.saveAccount(Account("account", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        notifications.setAuthorized(PACKAGE, true, "account")
    }

    private suspend fun ingest(text: String, postedAt: Long): String =
        (notifications.ingest(BankNotification(PACKAGE, "", text, postedAt)) as com.mipatrimonio.app.domain.notifications.NotificationOutcome.Registrada).recordId

    private companion object { const val PACKAGE = "app.bank" }
}
