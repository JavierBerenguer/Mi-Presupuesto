package com.mipatrimonio.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.backup.BackupData
import com.mipatrimonio.app.data.backup.BackupException
import com.mipatrimonio.app.data.backup.BackupFileAccess
import com.mipatrimonio.app.data.backup.BackupService
import com.mipatrimonio.app.data.backup.BackupSummary
import com.mipatrimonio.app.data.export.CsvExportArchive
import com.mipatrimonio.app.data.export.CsvExportFileAccess
import com.mipatrimonio.app.data.export.CsvExportService
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackupPasswordError { TOO_SHORT, DOES_NOT_MATCH }
enum class BackupNotice { CREATED, RESTORED, CSV_EXPORTED }

data class BackupUiState(
    val busy: Boolean = false,
    val showCreatePassword: Boolean = false,
    val restoreUri: Uri? = null,
    val pendingCreate: Boolean = false,
    val restorePreview: BackupSummary? = null,
    val showRestoreConfirmation: Boolean = false,
    val passwordError: BackupPasswordError? = null,
    val notice: BackupNotice? = null,
    val createdRecordCount: Int = 0,
    val error: String? = null,
    val showCsvWarning: Boolean = false,
    val pendingCsvExport: Boolean = false,
    val csvRowCounts: Map<String, Int> = emptyMap(),
)

class BackupViewModel(
    private val service: BackupService,
    private val files: BackupFileAccess,
    private val csvService: CsvExportService,
    private val csvFiles: CsvExportFileAccess,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = mutableState.asStateFlow()
    private var createdBytes: ByteArray? = null
    private var createdSummary: BackupSummary? = null
    private var restoreData: BackupData? = null
    private var csvArchive: CsvExportArchive? = null

    fun openCreatePassword() {
        mutableState.value = BackupUiState(showCreatePassword = true)
    }

    fun dismissDialog() {
        mutableState.value = mutableState.value.copy(
            showCreatePassword = false,
            restoreUri = null,
            restorePreview = null,
            showRestoreConfirmation = false,
            passwordError = null,
            error = null,
        )
        restoreData = null
    }

    fun prepareCreate(password: String, confirmation: String) {
        val validation = validatePassword(password, confirmation)
        if (validation != null) {
            mutableState.value = mutableState.value.copy(passwordError = validation)
            return
        }
        viewModelScope.launch {
            mutableState.value = BackupUiState(busy = true)
            runCatching { service.create(password.toCharArray()) }
                .onSuccess { (bytes, summary) ->
                    createdBytes = bytes
                    createdSummary = summary
                    mutableState.value = BackupUiState(pendingCreate = true)
                }
                .onFailure(::showError)
        }
    }

    fun createPickerOpened() {
        mutableState.value = mutableState.value.copy(pendingCreate = false)
    }

    fun cancelPendingCreate() {
        createdBytes?.fill(0)
        createdBytes = null
        createdSummary = null
        mutableState.value = BackupUiState()
    }

    fun writeAndVerify(uri: Uri, password: String) {
        val bytes = createdBytes ?: return
        val expected = createdSummary ?: return
        viewModelScope.launch {
            mutableState.value = BackupUiState(busy = true)
            runCatching {
                files.write(uri, bytes)
                val written = files.read(uri)
                service.verify(written, password.toCharArray(), expected)
            }.onSuccess { summary ->
                createdBytes?.fill(0)
                createdBytes = null
                createdSummary = null
                mutableState.value = BackupUiState(
                    notice = BackupNotice.CREATED,
                    createdRecordCount = summary.counts.values.sum(),
                )
            }.onFailure { error ->
                createdBytes?.fill(0)
                createdBytes = null
                createdSummary = null
                showError(error)
            }
        }
    }

    fun chooseRestore(uri: Uri) {
        mutableState.value = BackupUiState(restoreUri = uri)
    }

    fun inspectRestore(password: String) {
        val uri = mutableState.value.restoreUri ?: return
        if (password.length < MIN_PASSWORD_LENGTH) {
            mutableState.value = mutableState.value.copy(passwordError = BackupPasswordError.TOO_SHORT)
            return
        }
        viewModelScope.launch {
            mutableState.value = BackupUiState(busy = true)
            runCatching {
                service.inspect(files.read(uri), password.toCharArray())
            }.onSuccess { data ->
                restoreData = data
                mutableState.value = BackupUiState(
                    restorePreview = BackupSummary(data.createdAt, data.counts()),
                    showRestoreConfirmation = true,
                )
            }.onFailure(::showError)
        }
    }

    fun confirmRestore() {
        val data = restoreData ?: return
        viewModelScope.launch {
            mutableState.value = BackupUiState(busy = true)
            runCatching { service.restore(data) }
                .onSuccess {
                    restoreData = null
                    mutableState.value = BackupUiState(notice = BackupNotice.RESTORED)
                }
                .onFailure(::showError)
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(notice = null, error = null)
    }

    fun openCsvWarning() {
        mutableState.value = mutableState.value.copy(showCsvWarning = true, error = null)
    }

    fun dismissCsvWarning() {
        mutableState.value = mutableState.value.copy(showCsvWarning = false)
    }

    fun prepareCsvExport() {
        viewModelScope.launch {
            mutableState.value = BackupUiState(busy = true)
            runCatching { csvService.create() }
                .onSuccess { archive ->
                    csvArchive = archive
                    mutableState.value = BackupUiState(pendingCsvExport = true)
                }
                .onFailure(::showError)
        }
    }

    fun csvPickerOpened() {
        mutableState.value = mutableState.value.copy(pendingCsvExport = false)
    }

    fun cancelCsvExport() {
        csvArchive?.bytes?.fill(0)
        csvArchive = null
        mutableState.value = BackupUiState()
    }

    fun writeCsv(uri: Uri) {
        val archive = csvArchive ?: return
        viewModelScope.launch {
            mutableState.value = BackupUiState(busy = true)
            runCatching { csvFiles.write(uri, archive.bytes) }
                .onSuccess {
                    archive.bytes.fill(0)
                    csvArchive = null
                    mutableState.value = BackupUiState(
                        notice = BackupNotice.CSV_EXPORTED,
                        csvRowCounts = archive.rowCounts,
                    )
                }
                .onFailure { error ->
                    archive.bytes.fill(0)
                    csvArchive = null
                    showError(error)
                }
        }
    }

    private fun showError(error: Throwable) {
        mutableState.value = BackupUiState(error = (error as? BackupException)?.message ?: error.message.orEmpty())
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        fun suggestedFileName(today: LocalDate = LocalDate.now()) = "mi-patrimonio-$today.mipatrimonio"
        fun suggestedCsvFileName(today: LocalDate = LocalDate.now()) = "mi-patrimonio-csv-$today.zip"
        fun validatePassword(password: String, confirmation: String): BackupPasswordError? = when {
            password.length < MIN_PASSWORD_LENGTH -> BackupPasswordError.TOO_SHORT
            password != confirmation -> BackupPasswordError.DOES_NOT_MATCH
            else -> null
        }
    }
}
