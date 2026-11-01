package com.mipatrimonio.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R

/** Selector de color con área de saturación/brillo, barra de tono y campo hexadecimal. */
@Composable
fun HexColorPicker(colorArgb: Long, onColorChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val currentOnColorChange by rememberUpdatedState(onColorChange)
    var hsv by remember { mutableStateOf(argbToHsv(colorArgb)) }
    var hexText by remember { mutableStateOf(argbToHex(colorArgb)) }
    LaunchedEffect(colorArgb) {
        if (hsvToArgb(hsv) != colorArgb) hsv = argbToHsv(colorArgb)
        if (hexToArgb(hexText) != colorArgb) hexText = argbToHex(colorArgb)
    }
    fun emit(next: Hsv) {
        hsv = next
        val argb = hsvToArgb(next)
        hexText = argbToHex(argb)
        currentOnColorChange(argb)
    }
    val svDescription = stringResource(R.string.aj_color_saturation)
    val hueDescription = stringResource(R.string.aj_color_hue)
    val thumbRing = Color.White

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = svDescription }
                .pointerInput(Unit) {
                    trackPointer(size) { x, y -> emit(hsv.copy(s = x, v = 1f - y)) }
                },
        ) {
            drawRect(Color.hsv(hsv.h, 1f, 1f))
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val center = Offset(hsv.s * size.width, (1f - hsv.v) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = center, style = Stroke(5.dp.toPx()))
            drawCircle(thumbRing, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        }
        Canvas(
            Modifier.fillMaxWidth().height(28.dp)
                .semantics { contentDescription = hueDescription }
                .pointerInput(Unit) {
                    trackPointer(size) { x, _ -> emit(hsv.copy(h = x * 359.9f)) }
                },
        ) {
            val barHeight = 12.dp.toPx()
            val top = (size.height - barHeight) / 2
            drawRoundRect(
                Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
            )
            val center = Offset(hsv.h / 360f * size.width, size.height / 2)
            drawCircle(Color.hsv(hsv.h, 1f, 1f), radius = 10.dp.toPx(), center = center)
            drawCircle(thumbRing, radius = 10.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = 11.5.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        }
        OutlinedTextField(
            value = hexText,
            onValueChange = { input ->
                val clean = input.removePrefix("#").filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                hexText = clean
                hexToArgb(clean)?.let { argb ->
                    hsv = argbToHsv(argb)
                    currentOnColorChange(argb)
                }
            },
            label = { Text(stringResource(R.string.aj_color_hex)) },
            prefix = { Text("#") },
            leadingIcon = {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(colorArgb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            },
            isError = hexToArgb(hexText) == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Sigue el dedo desde que toca y devuelve la posición normalizada (0..1) dentro de [size]. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackPointer(
    size: IntSize,
    onPosition: (Float, Float) -> Unit,
) {
    fun report(change: PointerInputChange) {
        change.consume()
        onPosition(
            (change.position.x / size.width).coerceIn(0f, 1f),
            (change.position.y / size.height).coerceIn(0f, 1f),
        )
    }
    awaitEachGesture {
        report(awaitFirstDown())
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            report(change)
        }
    }
}

internal data class Hsv(val h: Float, val s: Float, val v: Float)

internal fun argbToHsv(argb: Long): Hsv {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), out)
    return Hsv(out[0], out[1], out[2])
}

internal fun hsvToArgb(hsv: Hsv): Long =
    android.graphics.Color.HSVToColor(floatArrayOf(hsv.h, hsv.s, hsv.v)).toLong() and 0xFFFFFFFFL

internal fun argbToHex(argb: Long): String = "%06X".format(argb and 0xFFFFFFL)

internal fun hexToArgb(hex: String): Long? =
    if (hex.length == 6) hex.toLongOrNull(16)?.let { 0xFF000000L or it } else null
