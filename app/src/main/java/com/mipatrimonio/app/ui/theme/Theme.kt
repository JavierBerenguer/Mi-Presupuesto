package com.mipatrimonio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MiPatrimonioExtraColors(
    val expense: Color,
    val chipBackground: Color,
    val track: Color,
    val chartAccent: Color,
    val chartSecondary: Color,
    val surfaceVariant: Color,
)

private val DarkExtras = MiPatrimonioExtraColors(
    expense = ExpenseDark,
    chipBackground = DarkChipBackground,
    track = DarkOutline,
    chartAccent = AccentGold,
    chartSecondary = ChartSage,
    surfaceVariant = DarkSurfaceVariant,
)

private val LightExtras = MiPatrimonioExtraColors(
    expense = ExpenseLight,
    chipBackground = LightChipBackground,
    track = LightOutline,
    chartAccent = Color(0xFF8A6208),
    chartSecondary = Color(0xFF39715F),
    surfaceVariant = LightSurfaceVariant,
)

val LocalMiPatrimonioExtraColors = staticCompositionLocalOf { DarkExtras }

val MaterialTheme.extras: MiPatrimonioExtraColors
    @Composable get() = LocalMiPatrimonioExtraColors.current

private val EsquemaOscuro = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF073426),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFB5E8D5),
    secondary = ChartSage,
    onSecondary = Color(0xFF12372C),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ExpenseDark,
)

private val EsquemaClaro = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF0C382A),
    secondary = Color(0xFF39715F),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ExpenseLight,
)

/** Modo oscuro por defecto; el claro se conserva como opción de ajustes. */
@Composable
fun MiPatrimonioTheme(
    modoOscuro: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMiPatrimonioExtraColors provides if (modoOscuro) DarkExtras else LightExtras,
    ) {
        MaterialTheme(
            colorScheme = if (modoOscuro) EsquemaOscuro else EsquemaClaro,
            typography = MiPatrimonioTypography,
            shapes = MiPatrimonioShapes,
            content = content,
        )
    }
}
