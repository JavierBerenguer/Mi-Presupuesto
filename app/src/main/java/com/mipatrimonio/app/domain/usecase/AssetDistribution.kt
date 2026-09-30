package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.model.AccountType

/** Porción de la distribución de activos: efectivo de un tipo de cuenta o inversiones (`type` = null). */
data class AssetShare(val type: AccountType?, val valueMinor: Long)

/** Distribución por tipo de activo en divisa base: efectivo por tipo de cuenta + inversiones a mercado. */
fun assetDistribution(netWorth: NetWorth): List<AssetShare> =
    buildList {
        netWorth.cashByType.forEach { (type, value) -> add(AssetShare(type, value)) }
        add(AssetShare(null, netWorth.investmentsMinor))
    }.filter { it.valueMinor > 0 }
