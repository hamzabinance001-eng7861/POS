package com.inventry.pos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inventry.pos.data.Store
import com.inventry.pos.ui.AppRoot
import com.inventry.pos.ui.PosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons, because the top of every screen is the dark gradient.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent { PosTheme { AppRoot() } }
    }
}
