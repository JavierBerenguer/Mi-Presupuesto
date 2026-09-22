package com.mipatrimonio.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.data.repository.Settings
import com.mipatrimonio.app.ui.navigation.MiPatrimonioApp
import com.mipatrimonio.app.ui.theme.MiPatrimonioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MiPatrimonioApplication).container
        setContent {
            // Valor inicial = ajustes por defecto (oscuro), para no parpadear en claro al arrancar.
            val settings by container.settings.settings.collectAsStateWithLifecycle(Settings("EUR", true))
            val dark = settings.darkMode
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MiPatrimonioTheme(modoOscuro = dark) {
                MiPatrimonioApp()
            }
        }
    }
}
