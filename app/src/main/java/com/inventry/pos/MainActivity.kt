package com.inventry.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inventry.pos.data.Store
import com.inventry.pos.ui.AppRoot
import com.inventry.pos.ui.PosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; their icons follow light or dark mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent { PosTheme { AppRoot() } }
    }
}
