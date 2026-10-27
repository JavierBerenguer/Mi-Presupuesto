package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate

object TestData {
    fun account(
        id: String = "a1",
        initial: Long = 0,
        currency: String = "EUR",
        type: AccountType = AccountType.CORRIENTE,
        archived: Boolean = false,
    ) = Account(id, "Cuenta $id", type, currency, initial, archived, 0)

    fun category(id: String, parentId: String? = null) =
        Category(id, id, parentId, 0xFF000000, false)

    fun tx(
        type: TransactionType,
        amount: Long,
        date: LocalDate = LocalDate.of(2026, 3, 10),
        account: String = "a1",
        category: String? = null,
        currency: String = "EUR",
        id: String = "t${counter++}",
    ) = Transaction(id, type, amount, currency, date, account, category, "", "", "", TransactionSource.MANUAL, 0, 0)

    fun transfer(
        from: String,
        to: String,
        fromAmount: Long,
        toAmount: Long = fromAmount,
        date: LocalDate = LocalDate.of(2026, 3, 11),
    ) = Transfer("tr${counter++}", from, to, fromAmount, toAmount, date, "", 0)

    private var counter = 0
}
