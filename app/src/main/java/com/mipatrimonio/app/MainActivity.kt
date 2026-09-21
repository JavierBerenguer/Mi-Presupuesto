package com.mipatrimonio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mipatrimonio.app.ui.navigation.MiPatrimonioApp
import com.mipatrimonio.app.ui.theme.MiPatrimonioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiPatrimonioTheme {
                MiPatrimonioApp()
            }
        }
    }
}
