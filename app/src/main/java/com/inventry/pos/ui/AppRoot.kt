package com.inventry.pos.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.R
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
    AppBackground {
        if (signedIn) {
            MainShell()
        } else {
            LoginScreen(onSuccess = {
                prefs.edit().putBoolean("signed_in", true).apply()
                signedIn = true
            })
        }
    }
}

/* ------------------------------------------------------------------ login */

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(Indigo),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(104.dp))
            }
            Text("Khan Mobile", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Indigo, modifier = Modifier.padding(top = 14.dp))
            Text("POINT OF SALE", fontSize = 11.sp, letterSpacing = 3.sp, color = muted(), modifier = Modifier.padding(bottom = 22.dp))

            AppCard(Modifier.widthIn(max = 420.dp)) {
                Text("Welcome Back!", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    "Sign in once. This phone remembers you after that.",
                    color = muted(), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                )
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Username", user, { user = it; error = "" }, placeholder = "Enter your username")
                    Field("Password", pass, { pass = it; error = "" }, placeholder = "Enter your password", password = !show)
                    Text(
                        if (show) "Hide password" else "Show password",
                        color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { show = !show },
                    )
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
    Tab("Parties", Icons.Filled.Person),
    Tab("Items", Icons.AutoMirrored.Filled.List),
    Tab("More", Icons.Filled.Menu),
)

private fun subTitle(sub: String): String = when (sub.substringBefore(':')) {
    "customers" -> "Customers"
    "suppliers" -> "Suppliers"
    "buy" -> "Purchase"
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
            "customers" -> PartiesTab("customers")
            "suppliers" -> PartiesTab("suppliers")
            "buy" -> BuyScreen()
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
        2 -> PartiesTab("customers")
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

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            title = sub?.let { subTitle(it) } ?: TABS[tab].label,
            canGoBack = sub != null,
            onLeft = { if (sub != null) sub = null else nav.tab(4) },
            onSettings = { sub = "settings" },
        )
        if (wide) {
            Row(Modifier.weight(1f)) {
                NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = Color.Transparent) {
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
            BottomBar(tab) { nav.tab(it) }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun TopBar(title: String, canGoBack: Boolean, onLeft: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(if (canGoBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu, if (canGoBack) "Back" else "Menu", onLeft)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(Settings.shop, fontSize = 11.sp, color = muted())
        }
        RoundIcon(Icons.Filled.Settings, "Settings", onSettings)
    }
}

/** A floating white bar. The chosen tab grows into a teal pill that shows its name. */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEachIndexed { i, t ->
                val on = i == selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = if (on) 14.dp else 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        t.icon, contentDescription = t.label, modifier = Modifier.size(22.dp),
                        tint = if (on) MaterialTheme.colorScheme.onPrimary else muted(),
                    )
                    if (on) Text(t.label, color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
