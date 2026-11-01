package com.mipatrimonio.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.usecase.Period

@Composable
fun AccountType.label(): String = stringResource(
    when (this) {
        AccountType.CORRIENTE -> R.string.common_account_corriente
        AccountType.AHORRO -> R.string.common_account_ahorro
        AccountType.EFECTIVO -> R.string.common_account_efectivo
        AccountType.INVERSION -> R.string.common_account_inversion
        AccountType.CRIPTO -> R.string.common_account_cripto
        AccountType.OTRA -> R.string.common_account_otra
    },
)

@Composable
fun TransactionType.label(): String = stringResource(
    if (this == TransactionType.GASTO) R.string.common_kind_gasto else R.string.common_kind_ingreso,
)

@Composable
fun BudgetPeriod.label(): String = stringResource(
    if (this == BudgetPeriod.MENSUAL) R.string.common_period_mensual else R.string.common_period_anual,
)

@Composable
fun Period.label(): String = stringResource(
    when (this) {
        Period.MES -> R.string.common_range_mes
        Period.TRES_MESES -> R.string.common_range_3m
        Period.SEIS_MESES -> R.string.common_range_6m
        Period.ANIO -> R.string.common_range_anio
        Period.TODO -> R.string.common_range_todo
    },
)
