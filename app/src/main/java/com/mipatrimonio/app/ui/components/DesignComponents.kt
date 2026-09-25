package com.mipatrimonio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.navigation.Destino
import com.mipatrimonio.app.ui.theme.LargeAmountStyle
import com.mipatrimonio.app.ui.theme.MiPatrimonioTheme
import com.mipatrimonio.app.ui.theme.extras

enum class AmountKind { INCOME, EXPENSE, NEUTRAL }

@Composable
fun SecondaryTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            Modifier.statusBarsPadding().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.common_back),
                )
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
    }
}

@Composable
fun AmountText(
    amountMinor: Long,
    currency: String,
    kind: AmountKind,
    modifier: Modifier = Modifier,
    style: TextStyle = LargeAmountStyle,
    incomeColor: Color = MaterialTheme.colorScheme.primary,
    expenseColor: Color = MaterialTheme.extras.expense,
    neutralColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val sign = when (kind) {
        AmountKind.INCOME -> "+"
        AmountKind.EXPENSE -> "−"
        AmountKind.NEUTRAL -> if (amountMinor < 0) "−" else if (amountMinor > 0) "+" else ""
    }
    val magnitudeText = MoneyMath.format(amountMinor, currency).removePrefix("-")
    val color = when (kind) {
        AmountKind.INCOME -> incomeColor
        AmountKind.EXPENSE -> expenseColor
        AmountKind.NEUTRAL -> neutralColor
    }
    Text(
        text = sign + magnitudeText,
        modifier = modifier,
        color = color,
        style = style.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

@Composable
fun TransactionRow(
    icon: ImageVector,
    title: String,
    metadata: String,
    amountMinor: Long,
    currency: String,
    kind: AmountKind,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.extras.chipBackground,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    metadataColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                metadata,
                color = metadataColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AmountText(amountMinor, currency, kind, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor, contentColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.extras.track,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(safeProgress).fillMaxHeight().background(progressColor))
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    unselectedContainerColor: Color = MaterialTheme.extras.surfaceVariant,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(unselectedContainerColor)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) selectedContainerColor else unselectedContainerColor)
                    .selectable(selected, role = Role.Tab, onClick = { onSelected(index) })
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    color = if (selected) selectedContentColor else unselectedContentColor,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PillTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContainerColor: Color = MaterialTheme.extras.chipBackground,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(if (selected) selectedContainerColor else unselectedContainerColor)
                    .selectable(selected, role = Role.Tab, onClick = { onSelected(index) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    color = if (selected) selectedContentColor else unselectedContentColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(
    selected: Destino,
    onSelected: (Destino) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(modifier.fillMaxWidth().height(72.dp), color = containerColor) {
        Row(Modifier.fillMaxSize().selectableGroup()) {
            Destino.principales.forEach { destination ->
                val isSelected = destination == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(isSelected, role = Role.Tab, onClick = { onSelected(destination) })
                        .padding(horizontal = 2.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        destination.icono,
                        contentDescription = null,
                        tint = if (isSelected) activeColor else inactiveColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        androidx.compose.ui.res.stringResource(destination.titulo),
                        color = if (isSelected) activeColor else inactiveColor,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.10f),
) {
    Canvas(modifier.semantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        fun point(index: Int) = Offset(
            x = index * stepX,
            y = size.height - ((values[index] - min) / range * size.height),
        )
        val line = Path().apply {
            val first = point(0)
            moveTo(first.x, first.y)
            for (i in 1 until values.size) point(i).let { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            val first = point(0)
            moveTo(first.x, size.height)
            lineTo(first.x, first.y)
            for (i in 1 until values.size) point(i).let { lineTo(it.x, it.y) }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(fill, fillColor)
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun AddFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 88.dp).size(56.dp),
            containerColor = containerColor,
            contentColor = contentColor,
        ) {
            Icon(Icons.Default.Add, contentDescription)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun ComponentsDarkPreview() {
    MiPatrimonioTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransactionRow(Icons.Default.ShoppingBag, "Supermercado", "Hoy · Alimentación", 4250, "EUR", AmountKind.EXPENSE)
            SectionCard(title = "Presupuesto mensual") { ProgressBar(0.64f) }
            SegmentedControl(listOf("Mes", "Tres meses", "Año"), 0, {})
            PillTabs(listOf("Todos", "Gastos", "Ingresos"), 1, {})
            Sparkline(listOf(2f, 4f, 3f, 7f, 6f), "Evolución de ejemplo", Modifier.fillMaxWidth().height(72.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComponentsLightPreview() {
    MiPatrimonioTheme(modoOscuro = false) {
        SectionCard(Modifier.padding(16.dp), title = "Saldo") {
            AmountText(125050, "EUR", AmountKind.INCOME)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun AmountTextDarkPreview() = MiPatrimonioTheme {
    AmountText(4250, "EUR", AmountKind.EXPENSE, Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun TransactionRowDarkPreview() = MiPatrimonioTheme {
    TransactionRow(Icons.Default.ShoppingBag, "Librería", "Ayer · Compras", 2499, "EUR", AmountKind.EXPENSE)
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun SectionCardDarkPreview() = MiPatrimonioTheme {
    SectionCard(Modifier.padding(16.dp), "Resumen") { Text("Contenido de ejemplo") }
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun ProgressBarDarkPreview() = MiPatrimonioTheme {
    ProgressBar(0.75f, Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun SegmentedControlDarkPreview() = MiPatrimonioTheme {
    SegmentedControl(listOf("Mes", "Año"), 0, {}, Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun PillTabsDarkPreview() = MiPatrimonioTheme {
    PillTabs(listOf("Todos", "Gastos"), 0, {}, Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun BottomNavBarDarkPreview() = MiPatrimonioTheme {
    BottomNavBar(Destino.Inicio, {})
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C, widthDp = 320, fontScale = 2f)
@Composable
private fun BottomNavBarNarrowLargeTextPreview() = MiPatrimonioTheme {
    BottomNavBar(Destino.Presupuesto, {})
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun SparklineDarkPreview() = MiPatrimonioTheme {
    Sparkline(listOf(2f, 4f, 3f, 7f), "Serie de ejemplo", Modifier.padding(16.dp).fillMaxWidth().height(80.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C, heightDp = 200)
@Composable
private fun AddFabDarkPreview() = MiPatrimonioTheme {
    AddFab("Añadir", {})
}
