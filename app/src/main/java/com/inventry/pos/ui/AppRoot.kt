package com.inventry.pos.ui

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Auth
import com.inventry.pos.data.Settings

/** Moves around the app: [tab] switches the bottom tab, [open] opens a screen such as "customers" or "reports:5". */
class Nav(val tab: (Int) -> Unit, val open: (String) -> Unit)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pos", Context.MODE_PRIVATE) }
    // Asked once, ever: after the first good sign-in the phone remembers it.
    var signedIn by rememberSaveable { mutableStateOf(prefs.getBoolean("signed_in", false)) }
    if (signedIn) {
        MainShell()
    } else {
        LoginScreen(onSuccess = {
            prefs.edit().putBoolean("signed_in", true).apply()
            signedIn = true
        })
    }
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
                Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
            ) { Text("🛍️", fontSize = 34.sp) }
            Text("Inventry POS", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(
                "Sell, buy and see how the shop is doing.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
            )

            AppCard(Modifier.widthIn(max = 420.dp)) {
                Heading("Sign in once")
                Text("You only need to do this the first time.", color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
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
    Tab("More", Icons.Filled.Menu),
)

private fun subTitle(sub: String): String = when (sub.substringBefore(':')) {
    "customers" -> "Customers"
    "suppliers" -> "Suppliers"
    "expenses" -> "Expenses"
    "returns" -> "Sale returns"
    "vehicles" -> "Vehicles"
    "reports" -> "Reports"
    "settings" -> "Settings"
    else -> "More"
}

@Composable
private fun Content(tab: Int, sub: String?, nav: Nav) {
    if (sub != null) {
        val parts = sub.split(":")
        when (parts[0]) {
            "customers" -> PartiesScreen("customers")
            "suppliers" -> PartiesScreen("suppliers")
            "expenses" -> ExpensesScreen()
            "returns" -> ReturnsScreen()
            "vehicles" -> VehiclesScreen()
            "reports" -> ReportsScreen(parts.getOrNull(1)?.toIntOrNull() ?: 0)
            "settings" -> SettingsScreen()
            else -> MoreScreen(nav)
        }
        return
    }
    when (tab) {
        0 -> HomeScreen(nav)
        1 -> SellScreen()
        2 -> BuyScreen()
        3 -> ProductsScreen()
        else -> MoreScreen(nav)
    }
}

@Composable
fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    val wide = isWide()
    val nav = Nav(tab = { tab = it; sub = null }, open = { sub = it })

    BackHandler(enabled = sub != null || tab != 0) { if (sub != null) sub = null else tab = 0 }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        TopBar(
            title = sub?.let { subTitle(it) } ?: TABS[tab].label,
            canGoBack = sub != null,
            onBack = { sub = null },
            onSettings = { sub = "settings" },
        )
        if (wide) {
            Row(Modifier.weight(1f)) {
                NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { nav.tab(i) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { Content(tab, sub, nav) }
            }
        } else {
            Box(Modifier.weight(1f)) { Content(tab, sub, nav) }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { nav.tab(i) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 11.sp) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, canGoBack: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(heroBrush())
            .statusBarsPadding()
            .padding(start = if (canGoBack) 4.dp else 18.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(Settings.shop, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) }
    }
}
