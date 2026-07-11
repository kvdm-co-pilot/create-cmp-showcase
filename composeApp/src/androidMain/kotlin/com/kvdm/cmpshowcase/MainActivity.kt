package com.kvdm.cmpshowcase

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: the status and navigation bars are transparent and content draws
        // beneath them. Screens own their insets in shared code via BaseScreen's Scaffold
        // (statusBarsPadding / navigationBarsPadding), which also maps to iOS safe areas
        // under Compose Multiplatform. Never set the deprecated window.statusBarColor on API 35.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { App() }
    }
}
