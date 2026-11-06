package com.inventry.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Auth
import com.inventry.pos.data.Settings

@Composable
fun AppRoot() {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    if (signedIn) MainShell(onSignOut = { signedIn = false }) else LoginScreen(onSuccess = { signedIn = true })
}

/* ------------------------------------------------------------------ login */

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(heroBrush()).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
            ) { Text("POS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
            Text("Inventry POS", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text("Sell, buy and see how the shop is doing.", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))

            AppCard(Modifier.widthIn(max = 420.dp)) {
                Heading("Sign in")
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Username", user, { user = it; error = "" })
                    Field("Password", pass, { pass = it; error = "" }, password = true)
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    BigButton("Sign in", onClick = {
                        if (user.trim() == Auth.USERNAME && pass == Auth.PASSWORD) {
                            pass = ""
                            onSuccess()
                        } else {
                            error = "Wrong username or password."
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/* ------------------------------------------------------------------- shell */

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Sell", Icons.Filled.ShoppingCart),
    Tab("Buy", Icons.Filled.Add),
    Tab("Products", Icons.AutoMirrored.Filled.List),
    Tab("Reports", Icons.Filled.DateRange),
)

@Composable
private fun Screen(tab: Int, go: (Int) -> Unit) {
    when (tab) {
        0 -> HomeScreen(go)
        1 -> SellScreen()
        2 -> BuyScreen()
        3 -> ProductsScreen()
        else -> ReportsScreen()
    }
}

@Composable
fun MainShell(onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val wide = isWide()

    BackHandler(enabled = tab != 0) { tab = 0 }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        TopBar(TABS[tab].label, onSettings = { settingsOpen = true }, onSignOut = onSignOut)
        if (wide) {
            Row(Modifier.weight(1f)) {
                NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { Screen(tab) { tab = it } }
            }
        } else {
            Box(Modifier.weight(1f)) { Screen(tab) { tab = it } }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 11.sp) },
                    )
                }
            }
        }
    }

    if (settingsOpen) SettingsDialog { settingsOpen = false }
}

@Composable
private fun TopBar(title: String, onSettings: () -> Unit, onSignOut: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(heroBrush()).statusBarsPadding().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(Settings.shop, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) }
        IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out", tint = Color.White) }
    }
}

@Composable
private fun SettingsDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Shown at the top of the app and on every amount.", color = muted(), fontSize = 13.sp, textAlign = TextAlign.Start)
                Field("Shop name", Settings.shop, { Settings.shop = it })
                Field("Currency", Settings.currency, { Settings.currency = it.take(6) }, placeholder = "Rs, $, AED …")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
