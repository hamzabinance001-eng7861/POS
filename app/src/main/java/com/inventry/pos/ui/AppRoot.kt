package com.inventry.pos.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
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
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 60f }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
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
    "serial" -> "IMEI / Warranty"
    "count" -> "Stock count"
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
            "serial" -> SerialLookupScreen()
            "count" -> StockCountScreen()
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

/** Slides and fades between tabs and sub-screens; the direction follows where you are going. */
@Composable
private fun AnimatedPage(tab: Int, sub: String?, nav: Nav) {
    AnimatedContent(
        targetState = tab to sub,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val opening = targetState.second != null && initialState.second == null
            val closing = targetState.second == null && initialState.second != null
            val dir = if (opening) 1 else if (closing) -1 else if (targetState.first >= initialState.first) 1 else -1
            (fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it / 9 }) togetherWith
                (fadeOut(tween(140)) + slideOutHorizontally(tween(260)) { -dir * it / 12 })
        },
        label = "page",
    ) { (t, s) -> Content(t, s, nav) }
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
                Box(Modifier.weight(1f)) { AnimatedPage(tab, sub, nav) }
            }
        } else {
            Box(Modifier.weight(1f)) { AnimatedPage(tab, sub, nav) }
            BottomBar(tab) { nav.tab(it) }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun TopBar(title: String, canGoBack: Boolean, onLeft: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(if (canGoBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu, if (canGoBack) "Back" else "Menu", onLeft)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        RoundIcon(Icons.Filled.Settings, "Settings", onSettings)
    }
}

/** A white bar along the bottom. The chosen tab sits in a soft violet rounded square. */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { i, t ->
            val on = i == selected
            val tabBg by animateColorAsState(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(220), label = "tabBg")
            val tabTint by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else muted(), tween(220), label = "tabTint")
            val tabScale by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tabScale")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tabBg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    t.icon, contentDescription = t.label, modifier = Modifier.size(22.dp).scale(tabScale),
                    tint = tabTint,
                )
                Text(
                    t.label, fontSize = 10.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = tabTint, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
