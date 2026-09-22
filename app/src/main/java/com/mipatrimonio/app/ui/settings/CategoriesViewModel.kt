package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryFormError {
    data object BlankName : CategoryFormError
    data object InvalidParent : CategoryFormError
    data class Repository(val message: String) : CategoryFormError
}

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val formError: CategoryFormError? = null,
)

class CategoriesViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val formError = MutableStateFlow<CategoryFormError?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(repository.categories, formError) { categories, error ->
        CategoriesUiState(
            isLoading = false,
            categories = categories,
            formError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState(),
    )

    fun clearFormError() {
        formError.value = null
    }

    fun saveCategory(
        existing: Category?,
        kind: CategoryKind,
        name: String,
        parentId: String?,
        colorArgb: Long,
        onSaved: () -> Unit,
    ) {
        if (name.isBlank()) {
            formError.value = CategoryFormError.BlankName
            return
        }
        val categories = uiState.value.categories
        if (!canSetParent(existing?.id, parentId, categories)) {
            formError.value = CategoryFormError.InvalidParent
            return
        }
        val category = Category(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            kind = existing?.kind ?: kind,
            parentId = parentId,
            colorArgb = colorArgb,
            archived = existing?.archived ?: false,
        )
        viewModelScope.launch {
            runCatching { repository.saveCategory(category) }
                .onSuccess {
                    formError.value = null
                    onSaved()
                }
                .onFailure { error ->
                    formError.value = CategoryFormError.Repository(error.message.orEmpty())
                }
        }
    }

    fun setArchived(category: Category, archived: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.saveCategory(category.copy(archived = archived))
                if (archived && category.parentId == null) {
                    uiState.value.categories
                        .filter { it.parentId == category.id && !it.archived }
                        .forEach { child -> repository.saveCategory(child.copy(archived = true)) }
                }
            }.onSuccess {
                formError.value = null
                onSaved()
            }.onFailure { error ->
                formError.value = CategoryFormError.Repository(error.message.orEmpty())
            }
        }
    }
}
