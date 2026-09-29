package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.CategoryUsage
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
    val expandedCategoryIds: Set<String> = emptySet(),
    val formError: CategoryFormError? = null,
    val deletion: CategoryDeletionState? = null,
)

data class CategoryDeletionState(
    val category: Category,
    val usage: CategoryUsage,
    val targets: List<CategoryDeleteTarget>,
    val targetSelected: Boolean = false,
    val targetId: String? = null,
    val isDeleting: Boolean = false,
    val error: String? = null,
)

class CategoriesViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val formError = MutableStateFlow<CategoryFormError?>(null)
    private val deletion = MutableStateFlow<CategoryDeletionState?>(null)
    private val expandedCategoryIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.categories,
        formError,
        deletion,
        expandedCategoryIds,
    ) { categories, error, deletionState, expandedIds ->
        CategoriesUiState(
            isLoading = false,
            categories = categories,
            expandedCategoryIds = expandedIds,
            formError = error,
            deletion = deletionState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState(),
    )

    fun clearFormError() {
        formError.value = null
    }

    fun toggleCategoryExpanded(categoryId: String) {
        expandedCategoryIds.value = if (categoryId in expandedCategoryIds.value) {
            expandedCategoryIds.value - categoryId
        } else {
            expandedCategoryIds.value + categoryId
        }
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
                    parentId?.let { parent ->
                        expandedCategoryIds.value = expandedCategoryIds.value + parent
                    }
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

    fun requestDelete(category: Category) {
        viewModelScope.launch {
            runCatching { repository.categoryUsage(category.id) }
                .onSuccess { usage ->
                    deletion.value = CategoryDeletionState(
                        category = category,
                        usage = usage,
                        targets = categoryDeleteTargets(
                            categoryId = category.id,
                            categories = uiState.value.categories,
                            expenseOnly = usage.budgets > 0,
                        ),
                    )
                }
                .onFailure { error ->
                    formError.value = CategoryFormError.Repository(error.message.orEmpty())
                }
        }
    }

    fun selectDeleteTarget(targetId: String?) {
        deletion.value = deletion.value?.copy(targetSelected = true, targetId = targetId, error = null)
    }

    fun dismissDelete() {
        deletion.value = null
    }

    fun confirmDelete() {
        val request = deletion.value ?: return
        if (request.usage.isUsed && !request.targetSelected) return
        deletion.value = request.copy(isDeleting = true, error = null)
        viewModelScope.launch {
            runCatching { repository.deleteCategory(request.category.id, request.targetId) }
                .onSuccess { deletion.value = null }
                .onFailure { error ->
                    deletion.value = deletion.value?.copy(
                        isDeleting = false,
                        error = error.message.orEmpty(),
                    )
                }
        }
    }
}
