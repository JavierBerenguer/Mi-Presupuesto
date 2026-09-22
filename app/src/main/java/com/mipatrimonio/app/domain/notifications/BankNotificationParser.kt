package com.mipatrimonio.app.domain.notifications

interface BankNotificationParser {
    val parserId: String
    fun accepts(packageName: String): Boolean
    fun parse(notification: BankNotification): ParsedNotification?
}
