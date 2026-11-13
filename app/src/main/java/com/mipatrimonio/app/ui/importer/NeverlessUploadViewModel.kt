package com.mipatrimonio.app.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.sync.NeverlessCsvValidation
import com.mipatrimonio.app.data.sync.NeverlessDocumentReader
import com.mipatrimonio.app.data.sync.NeverlessPendingState
import com.mipatrimonio.app.data.sync.NeverlessUploadRepository
import com.mipatrimonio.app.data.sync.SyncConnectionException
import com.mipatrimonio.app.data.sync.SyncSessionExpiredException
import com.mipatrimonio.app.data.sync.validateNeverlessCsv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NeverlessAvailability { LOADING, NO_SESSION, NO_PROFILE, OFFLINE, NOT_PENDING, PENDING }

enum class NeverlessMessage {
    TOO_LARGE, NOT_UTF8, NOT_NEVERLESS, NO_DATA, READ_ERROR, SESSION_EXPIRED, STORAGE_ERROR, SENT,
}

data class NeverlessUploadUiState(
    val availability: NeverlessAvailability = NeverlessAvailability.LOADING,
    val uploadedAt: String? = null,
    val busy: Boolean = false,
    val confirmReplacement: Boolean = false,
    val message: NeverlessMessage? = null,
) {
    val canChooseFile: Boolean
        get() = !busy && (availability == NeverlessAvailability.NOT_PENDING || availability == NeverlessAvailability.PENDING)
}

class NeverlessUploadViewModel(
    private val repository: NeverlessUploadRepository,
    private val reader: NeverlessDocumentReader,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NeverlessUploadUiState())
    val uiState: StateFlow<NeverlessUploadUiState> = mutableState.asStateFlow()
    private var pendingCsvText: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            runCatching { repository.status() }
                .onSuccess { status ->
                    mutableState.value = mutableState.value.copy(
                        availability = when (status) {
                            is NeverlessPendingState.Pending -> NeverlessAvailability.PENDING
                            NeverlessPendingState.NotPending -> NeverlessAvailability.NOT_PENDING
                            NeverlessPendingState.NoSession -> NeverlessAvailability.NO_SESSION
                            NeverlessPendingState.NoProfile -> NeverlessAvailability.NO_PROFILE
                        },
                        uploadedAt = (status as? NeverlessPendingState.Pending)?.uploadedAt,
                        busy = false,
                    )
                }
                .onFailure(::showRemoteError)
        }
    }

    fun chooseFile(uri: Uri) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            runCatching { reader.read(uri) }
                .onSuccess(::validateAndContinue)
                .onFailure {
                    mutableState.value = mutableState.value.copy(busy = false, message = NeverlessMessage.READ_ERROR)
                }
        }
    }

    fun confirmReplacement() {
        mutableState.value = mutableState.value.copy(confirmReplacement = false)
        pendingCsvText?.let(::upload)
    }

    fun dismissReplacement() {
        pendingCsvText = null
        mutableState.value = mutableState.value.copy(confirmReplacement = false)
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private fun validateAndContinue(bytes: ByteArray) {
        when (val validation = validateNeverlessCsv(bytes)) {
            is NeverlessCsvValidation.Valid -> {
                pendingCsvText = validation.text
                if (mutableState.value.availability == NeverlessAvailability.PENDING) {
                    mutableState.value = mutableState.value.copy(busy = false, confirmReplacement = true)
                } else {
                    upload(validation.text)
                }
            }
            is NeverlessCsvValidation.Invalid -> {
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    message = when (validation.reason) {
                        NeverlessCsvValidation.Reason.TOO_LARGE -> NeverlessMessage.TOO_LARGE
                        NeverlessCsvValidation.Reason.NOT_UTF8 -> NeverlessMessage.NOT_UTF8
                        NeverlessCsvValidation.Reason.NOT_NEVERLESS -> NeverlessMessage.NOT_NEVERLESS
                        NeverlessCsvValidation.Reason.NO_DATA -> NeverlessMessage.NO_DATA
                    },
                )
            }
        }
    }

    private fun upload(text: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            runCatching { repository.upload(text) }
                .onSuccess {
                    pendingCsvText = null
                    val status = runCatching { repository.status() }.getOrNull()
                    mutableState.value = mutableState.value.copy(
                        availability = NeverlessAvailability.PENDING,
                        uploadedAt = (status as? NeverlessPendingState.Pending)?.uploadedAt,
                        busy = false,
                        message = NeverlessMessage.SENT,
                    )
                }
                .onFailure(::showRemoteError)
        }
    }

    private fun showRemoteError(error: Throwable) {
        pendingCsvText = null
        mutableState.value = mutableState.value.copy(
            availability = when (error) {
                is SyncConnectionException -> NeverlessAvailability.OFFLINE
                is SyncSessionExpiredException -> NeverlessAvailability.NO_SESSION
                else -> mutableState.value.availability
            },
            busy = false,
            message = when (error) {
                is SyncSessionExpiredException -> NeverlessMessage.SESSION_EXPIRED
                else -> NeverlessMessage.STORAGE_ERROR
            },
        )
    }
}
