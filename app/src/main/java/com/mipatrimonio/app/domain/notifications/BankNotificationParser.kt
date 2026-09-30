package com.mipatrimonio.app.domain.notifications

interface BankNotificationParser {
    val parserId: String
    fun accepts(packageName: String): Boolean
    fun parse(notification: BankNotification): ParsedNotification?

    fun parseWithReason(notification: BankNotification): NotificationParseResult =
        parse(notification)?.let(NotificationParseResult::Success)
            ?: NotificationParseResult.Failure(NoInterpretableReason.SIN_IMPORTE, amountFound = false)
}

sealed interface NotificationParseResult {
    data class Success(val parsed: ParsedNotification) : NotificationParseResult
    data class Failure(val reason: NoInterpretableReason, val amountFound: Boolean) : NotificationParseResult
}
