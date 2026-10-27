package com.mipatrimonio.app.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TradeRepublicImportRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TradeRepublicImportRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        LedgerRepository(db).saveAccount(Account("a", "TR", AccountType.CORRIENTE, "EUR", 0, false, 1))
        repository = TradeRepublicImportRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun `reimportacion crea cero filas`() = runBlocking {
        val movement = ImportedMovement(
            "same", LocalDate.of(2026, 1, 1), kind = ImportedKind.INGRESO, amountCents = 100,
            currency = "EUR", description = "Bonus", counterparty = null, isin = null, assetName = null,
            shares = null, price = null, feeCents = 0, taxCents = 0, originalAmountCents = null,
            originalCurrency = null, fxRate = null, mccCode = null, needsReview = false, reviewReason = null,
            rawType = "BONUS", category = "CASH",
        )
        val preview = ImportPreview(listOf(movement), emptyList(), emptyList())
        assertEquals(1, repository.execute(repository.plan(preview, "a")).transactions)
        assertEquals(0, repository.execute(repository.plan(preview, "a")).transactions)
    }
}
