package com.mipatrimonio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Esmeralda = Color(0xFF3DDC97)
private val EsmeraldaOscuro = Color(0xFF0B7A55)

private val EsquemaOscuro = darkColorScheme(
    primary = Esmeralda,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF14503D),
    onPrimaryContainer = Color(0xFFB8F5DA),
    secondary = Color(0xFF8FB8FF),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE0E9E4),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFE0E9E4),
    surfaceVariant = Color(0xFF1B2620),
    onSurfaceVariant = Color(0xFFBFCAC3),
    error = Color(0xFFFF8A80),
)

private val EsquemaClaro = lightColorScheme(
    primary = EsmeraldaOscuro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5DA),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF2E5AA8),
    background = Color(0xFFF6FBF8),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF8),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404943),
    error = Color(0xFFBA1A1A),
)

/** Modo oscuro por defecto; el claro es opcional. */
@Composable
fun MiPatrimonioTheme(
    modoOscuro: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (modoOscuro) EsquemaOscuro else EsquemaClaro,
        content = content,
    )
}
