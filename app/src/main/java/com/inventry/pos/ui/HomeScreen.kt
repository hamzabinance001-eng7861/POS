package com.inventry.pos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.inventry.pos.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Periods
import com.inventry.pos.data.Settings
import com.inventry.pos.data.Store
import com.inventry.pos.data.customersOweTotal
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.lastDaysTotals
import com.inventry.pos.data.money
import com.inventry.pos.data.salesByDay
import com.inventry.pos.data.stockValue
import com.inventry.pos.data.suppliersOwed
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/** Seven slim bars, one per day, with the day's first letter under each. Today is the bright one. */
@Composable
private fun MiniBars(data: List<Pair<String, Double>>, modifier: Modifier) {
    val max = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        data.forEachIndexed { i, (name, v) ->
            val today = i == data.size - 1
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((0.08f + 0.70f * (v / max).toFloat()).coerceIn(0.08f, 0.78f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = if (today) 1f else if (v > 0) 0.6f else 0.25f)),
                )
                Text(name.take(1), color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
fun HomeScreen(nav: Nav) {
    val today = remember(Store.rev) { Store.salesByDay(Periods.today()) }
    val sold = today.sumOf { it.total }
    val shownSold by animateFloatAsState(sold.toFloat(), tween(900), label = "sold")
    val count = today.sumOf { it.count }
    val week = remember(Store.rev) { Store.lastDaysTotals(7) }
    val owed = remember(Store.rev) { Store.customersOweTotal() }
    val weOwe = remember(Store.rev) { Store.suppliersOwed().sumOf { it.amount } }
    val stockValue = remember(Store.rev) { Store.stockValue() }
    val low = remember(Store.rev) { Store.products().count { it.stock <= it.low } }
    val recent = remember(Store.rev) { Store.sales(5) }

    OnePane {
        // ---- today at a glance
        Row(
            Modifier
                .appear(0)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(heroBrush())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val logo = Settings.logo
                    if (logo != null) {
                        BytesImage(logo, 32.dp, circle = true)
                    } else {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Text(Settings.shop.trim().take(1).uppercase().ifEmpty { "K" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(greeting(), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        Text(Settings.shop, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Sales today", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                Text(money(shownSold.toDouble()), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("$count invoice${if (count == 1) "" else "s"} today", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            MiniBars(week, Modifier.width(118.dp).height(72.dp))
        }

        // ---- the two things done most
        Row(Modifier.appear(80).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("New sale", { nav.tab(1) }, Modifier.weight(1f), icon = rememberVectorPainter(Icons.Filled.ShoppingCart))
            BigButton("Purchase", { nav.open("buy") }, Modifier.weight(1f), coral = true, icon = painterResource(R.drawable.ic_receipt))
        }

        // ---- shortcuts
        Row(Modifier.appear(140).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(rememberVectorPainter(Icons.Filled.Person), "Customers", Indigo, { nav.open("customers") }, Modifier.weight(1f))
            IconTile(rememberVectorPainter(Icons.Filled.Create), "Expenses", Amber, { nav.open("expenses") }, Modifier.weight(1f))
            IconTile(rememberVectorPainter(Icons.Filled.Refresh), "Returns", Red, { nav.open("returns") }, Modifier.weight(1f))
            IconTile(rememberVectorPainter(Icons.Filled.DateRange), "Reports", Green, { nav.open("reports:0") }, Modifier.weight(1f))
        }

        // ---- the numbers
        Row(Modifier.appear(200).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Customers owe", money(owed), Red, Modifier.weight(1f))
            StatTile("You owe suppliers", money(weOwe), Amber, Modifier.weight(1f))
        }
        Row(Modifier.appear(260).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Stock value", money(stockValue), Green, Modifier.weight(1f))
            StatTile("Low stock", "$low item${if (low == 1) "" else "s"}", if (low > 0) Amber else Green, Modifier.weight(1f))
        }

        AppCard(Modifier.appear(320)) {
            Heading("Recent sales")
            if (recent.isEmpty()) EmptyState("No sales yet. Make your first sale from the Sell tab.")
            recent.forEach { s -> ListRow("#${s.no} · ${s.customer}", fmtDate(s.date), money(s.total), salePill(s)) { nav.tab(1) } }
        }
    }
}
