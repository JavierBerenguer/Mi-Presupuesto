package com.mipatrimonio.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.backup.BackupFileAccess
import com.mipatrimonio.app.data.backup.BackupRepository
import com.mipatrimonio.app.data.backup.BackupService
import com.mipatrimonio.app.data.db.AccountEntity
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.export.CsvExportFileAccess
import com.mipatrimonio.app.data.export.CsvExportRepository
import com.mipatrimonio.app.data.export.CsvExportService
import com.mipatrimonio.app.testutil.SettingsStoreRule
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupViewModelTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `valida minimo y confirmacion de contraseña`() {
        assertEquals(BackupPasswordError.TOO_SHORT, BackupViewModel.validatePassword("corta", "corta"))
        assertEquals(BackupPasswordError.DOES_NOT_MATCH, BackupViewModel.validatePassword("12345678", "87654321"))
        assertNull(BackupViewModel.validatePassword("12345678", "12345678"))
    }

    @Test
    fun `restauracion muestra resumen y no toca datos hasta confirmar`() = runTest {
        val original = AccountEntity("a", "Original", "CORRIENTE", "EUR", 100, false, 1, 1)
        db.accountDao().insertAllForRestore(listOf(original))
        val files = MemoryFiles()
        var postRestoreCalled = false
        val service = BackupService(
            BackupRepository(db, settingsRule.repository, "test", { 1 }),
            afterRestore = { postRestoreCalled = true },
        )
        val viewModel = BackupViewModel(
            service, files, CsvExportService(CsvExportRepository(db)), files,
        )
        val uri = Uri.parse("content://test/copia")

        viewModel.openCreatePassword()
        viewModel.prepareCreate("12345678", "12345678")
        viewModel.uiState.first { it.pendingCreate }
        viewModel.createPickerOpened()
        viewModel.writeAndVerify(uri, "12345678")
        viewModel.uiState.first { it.notice == BackupNotice.CREATED }

        db.accountDao().deleteAllForRestore()
        db.accountDao().insertAllForRestore(listOf(original.copy(name = "Actual")))
        viewModel.clearMessage()
        viewModel.chooseRestore(uri)
        viewModel.inspectRestore("12345678")
        val preview = viewModel.uiState.first { it.showRestoreConfirmation }
        assertEquals(1, preview.restorePreview?.counts?.get("account"))
        assertEquals("Actual", db.accountDao().getAllForBackup().single().name)
        assertFalse(postRestoreCalled)

        viewModel.confirmRestore()
        viewModel.uiState.first { it.notice == BackupNotice.RESTORED }
        assertEquals(original, db.accountDao().getAllForBackup().single())
        assertTrue(postRestoreCalled)
    }

    @Test
    fun `exportacion CSV avisa escribe zip y muestra filas`() = runTest {
        db.accountDao().insertAllForRestore(
            listOf(AccountEntity("a", "Cuenta", "CORRIENTE", "EUR", 100, false, 1, 1)),
        )
        val files = MemoryFiles()
        val viewModel = BackupViewModel(
            BackupService(BackupRepository(db, settingsRule.repository, "test", { 1 })),
            files,
            CsvExportService(CsvExportRepository(db)),
            files,
        )
        val uri = Uri.parse("content://test/exportacion")

        viewModel.openCsvWarning()
        assertTrue(viewModel.uiState.first { it.showCsvWarning }.showCsvWarning)
        viewModel.prepareCsvExport()
        viewModel.uiState.first { it.pendingCsvExport }
        viewModel.csvPickerOpened()
        viewModel.writeCsv(uri)
        val final = viewModel.uiState.first { it.notice == BackupNotice.CSV_EXPORTED }

        assertEquals(1, final.csvRowCounts["cuentas.csv"])
        assertEquals(0, final.csvRowCounts["movimientos.csv"])
        assertTrue(files.read(uri).copyOfRange(0, 2).contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte())))
    }

    private class MemoryFiles : BackupFileAccess, CsvExportFileAccess {
        private val values = mutableMapOf<Uri, ByteArray>()
        override suspend fun write(uri: Uri, bytes: ByteArray) { values[uri] = bytes.copyOf() }
        override suspend fun read(uri: Uri): ByteArray = requireNotNull(values[uri]).copyOf()
    }
}
