package com.mipatrimonio.app.ui.investments

internal enum class InvestmentMenuAction {
    REFRESH_PRICES,
    BUY,
    SELL,
    DIVIDEND,
    FEE,
    TRANSFER,
}

internal val investmentMenuActions: List<InvestmentMenuAction> = InvestmentMenuAction.entries
