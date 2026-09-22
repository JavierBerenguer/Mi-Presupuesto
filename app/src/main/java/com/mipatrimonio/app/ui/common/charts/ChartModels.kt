package com.mipatrimonio.app.ui.common.charts

import androidx.compose.ui.graphics.Color

data class ChartPoint(val label: String, val value: Long)

data class BarGroup(val label: String, val incomeMinor: Long, val expenseMinor: Long)

data class DonutSlice(val label: String, val valueMinor: Long, val color: Color)
