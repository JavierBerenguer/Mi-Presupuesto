package com.mipatrimonio.app.ui.importer

import com.mipatrimonio.app.data.importer.ImportedMovement
import com.mipatrimonio.app.data.importer.ImportRowDecision
import com.mipatrimonio.app.data.importer.ImportRowStatus
import com.mipatrimonio.app.data.importer.PlannedImportRow

enum class ImportReviewAction { IMPORT_OPERATION, IMPORT_MOVEMENT, NONE }

enum class ImportReviewDecisionState { PENDING, IGNORED, IMPORT_OPERATION, IMPORT_MOVEMENT }

data class ImportReviewItem(
    val row: PlannedImportRow,
    val action: ImportReviewAction,
    val decisionState: ImportReviewDecisionState,
)

internal fun canConvertToMovement(row: ImportedMovement): Boolean =
    runCatching { Math.addExact(Math.addExact(row.amountCents, row.feeCents), row.taxCents) != 0L }.getOrDefault(false)

internal fun importReviewItem(row: PlannedImportRow, decision: ImportRowDecision?): ImportReviewItem {
    val action = when {
        row.source.category.equals("TRADING", ignoreCase = true) -> ImportReviewAction.IMPORT_OPERATION
        canConvertToMovement(row.source) -> ImportReviewAction.IMPORT_MOVEMENT
        else -> ImportReviewAction.NONE
    }
    val state = when {
        row.status == ImportRowStatus.REVIEW -> ImportReviewDecisionState.PENDING
        decision == ImportRowDecision.Ignore -> ImportReviewDecisionState.IGNORED
        decision == ImportRowDecision.AcceptDefault && action == ImportReviewAction.IMPORT_OPERATION ->
            ImportReviewDecisionState.IMPORT_OPERATION
        decision == ImportRowDecision.AcceptDefault -> ImportReviewDecisionState.IMPORT_MOVEMENT
        decision is ImportRowDecision.AsTransaction -> ImportReviewDecisionState.IMPORT_MOVEMENT
        else -> ImportReviewDecisionState.PENDING
    }
    return ImportReviewItem(row, action, state)
}
