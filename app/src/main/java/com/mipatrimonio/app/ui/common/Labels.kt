package com.mipatrimonio.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.OperationType
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
fun CategoryKind.label(): String = stringResource(
    if (this == CategoryKind.GASTO) R.string.common_kind_gasto else R.string.common_kind_ingreso,
)

@Composable
fun TransactionType.label(): String = stringResource(
    if (this == TransactionType.GASTO) R.string.common_kind_gasto else R.string.common_kind_ingreso,
)

@Composable
fun OperationType.label(): String = stringResource(
    when (this) {
        OperationType.COMPRA -> R.string.common_op_compra
        OperationType.VENTA -> R.string.common_op_venta
        OperationType.DIVIDENDO -> R.string.common_op_dividendo
        OperationType.COMISION -> R.string.common_op_comision
    },
)

@Composable
fun AssetType.label(): String = stringResource(
    when (this) {
        AssetType.ACCION -> R.string.common_asset_accion
        AssetType.ETF -> R.string.common_asset_etf
        AssetType.FONDO_INDEXADO -> R.string.common_asset_fondo_indexado
        AssetType.FONDO_INVERSION -> R.string.common_asset_fondo_inversion
        AssetType.CRIPTO -> R.string.common_asset_cripto
    },
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
