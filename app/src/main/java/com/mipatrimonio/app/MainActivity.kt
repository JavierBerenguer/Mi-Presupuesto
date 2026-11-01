package com.mipatrimonio.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.data.repository.Settings
import com.mipatrimonio.app.ui.navigation.MiPatrimonioApp
import com.mipatrimonio.app.ui.theme.MiPatrimonioTheme

class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableStateOf<AutomationNavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest = takeAutomationNavigationRequest(intent)
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
                MiPatrimonioApp(
                    navigationRequest = navigationRequest,
                    onNavigationRequestConsumed = { navigationRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationRequest = takeAutomationNavigationRequest(intent)
    }

    private fun takeAutomationNavigationRequest(intent: Intent?): AutomationNavigationRequest? {
        val request = automationNavigationRequest(
            open = intent?.getStringExtra(EXTRA_OPEN),
            tab = intent?.getStringExtra(EXTRA_AUTOMATION_TAB),
        )
        intent?.removeExtra(EXTRA_OPEN)
        intent?.removeExtra(EXTRA_AUTOMATION_TAB)
        return request
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val EXTRA_AUTOMATION_TAB = "automation_tab"
        const val OPEN_AUTOMATION = "automation"
        const val TAB_PENDING = "pending"
        const val TAB_NEW_MERCHANTS = "new_merchants"
    }
}

data class AutomationNavigationRequest(val initialTab: String)

fun automationNavigationRequest(open: String?, tab: String?): AutomationNavigationRequest? =
    if (open == MainActivity.OPEN_AUTOMATION &&
        (tab == MainActivity.TAB_PENDING || tab == MainActivity.TAB_NEW_MERCHANTS)
    ) {
        AutomationNavigationRequest(requireNotNull(tab))
    } else {
        null
    }
