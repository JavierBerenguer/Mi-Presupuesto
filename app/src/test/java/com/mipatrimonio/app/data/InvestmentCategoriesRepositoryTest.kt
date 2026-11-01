package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvestmentCategoriesRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun `prefiere singular con tildes frente a plural`() = runTest {
        ledger.saveCategory(category("root", "Inversiónes"))
        ledger.saveCategory(category("plural", "Comisiones", "root"))
        ledger.saveCategory(category("singular", "Comisión", "root"))

        ledger.ensureInvestmentCategories()

        val marked = ledger.categories.first().associateBy { it.investmentKind }
        assertEquals("singular", marked[InvestmentKind.COMISION]?.id)
        assertEquals("plural", ledger.categories.first().single { it.id == "plural" }.id)
    }

    @Test fun `usa plural si es la unica candidata`() = runTest {
        ledger.saveCategory(category("mp-inversiones", "Mi cartera"))
        ledger.saveCategory(category("fees", "Comisiones", "mp-inversiones"))

        ledger.ensureInvestmentCategories()

        assertEquals(InvestmentKind.COMISION, ledger.categories.first().single { it.id == "fees" }.investmentKind)
    }

    @Test fun `ignora archivada y crea id estable con raiz renombrada`() = runTest {
        ledger.saveCategory(category("mp-inversiones", "A largo plazo"))
        ledger.saveCategory(category("old", "Compra", "mp-inversiones", archived = true))

        ledger.ensureInvestmentCategories()

        val categories = ledger.categories.first()
        assertEquals(InvestmentKind.COMPRA, categories.single { it.id == "mp-inversiones-inv-compra" }.investmentKind)
        assertEquals(null, categories.single { it.id == "old" }.investmentKind)
    }

    @Test fun `crea raiz y cuatro marcas de forma idempotente`() = runTest {
        ledger.ensureInvestmentCategories()
        val first = ledger.categories.first().sortedBy(Category::id)

        ledger.ensureInvestmentCategories()

        val second = ledger.categories.first().sortedBy(Category::id)
        assertEquals(first, second)
        assertEquals("Inversiones", second.single { it.id == "mp-inversiones" }.name)
        assertEquals(InvestmentKind.entries.toSet(), second.mapNotNull { it.investmentKind }.toSet())
    }

    @Test fun `guardar conserva marca y rechaza mover o archivar`() = runTest {
        ledger.ensureInvestmentCategories()
        val purchase = ledger.categories.first().single { it.investmentKind == InvestmentKind.COMPRA }

        ledger.saveCategory(purchase.copy(name = "Adquisiciones", investmentKind = null))
        val saved = ledger.categories.first().single { it.id == purchase.id }
        assertEquals(InvestmentKind.COMPRA, saved.investmentKind)
        assertEquals("Adquisiciones", saved.name)
        assertFails { ledger.saveCategory(saved.copy(parentId = null)) }
        assertFails { ledger.saveCategory(saved.copy(archived = true)) }
    }

    @Test fun `rechaza borrar marcada y raiz que contiene marcas`() = runTest {
        ledger.ensureInvestmentCategories()
        val purchase = ledger.categories.first().single { it.investmentKind == InvestmentKind.COMPRA }

        val categoryError = runCatching { ledger.deleteCategory(purchase.id, null) }.exceptionOrNull()
        val rootError = runCatching { ledger.deleteCategory("mp-inversiones", null) }.exceptionOrNull()

        assertTrue(categoryError is IllegalArgumentException)
        assertTrue(rootError is IllegalArgumentException)
        assertTrue(categoryError?.message.orEmpty().contains("movimientos de inversión"))
        assertFalse(ledger.categories.first().isEmpty())
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun category(id: String, name: String, parentId: String? = null, archived: Boolean = false) =
        Category(id, name, parentId, 0xFF4527A0, archived, "investment")
}
