package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun HomeScreen(nav: Nav) {
    val today = remember(Store.rev) { Store.salesByDay(Periods.today()) }
    val sold = today.sumOf { it.total }
    val shownSold by androidx.compose.animation.core.animateFloatAsState(sold.toFloat(), androidx.compose.animation.core.tween(900), label = "sold")
    val count = today.sumOf { it.count }
    val week = remember(Store.rev) { Store.lastDaysTotals(7) }
    val owed = remember(Store.rev) { Store.customersOweTotal() }
    val weOwe = remember(Store.rev) { Store.suppliersOwed().sumOf { it.amount } }
    val stockValue = remember(Store.rev) { Store.stockValue() }
    val low = remember(Store.rev) { Store.products().count { it.stock <= it.low } }
    val recent = remember(Store.rev) { Store.sales(5) }

    OnePane {
        // ---- the summary card
        Column(
            Modifier
                .appear(0)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(heroBrush())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = Settings.logo
                if (logo != null) {
                    BytesImage(logo, 46.dp, circle = true)
                } else {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text("🛍️", fontSize = 22.sp)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(greeting(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text(Settings.shop, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Sales today", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
            Text(money(shownSold.toDouble()), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "$count invoice${if (count == 1) "" else "s"} today",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp),
            )
            BarChart(week, bar = Color.White, label = Color.White.copy(alpha = 0.8f))
        }

        // ---- the two big buttons from the design
        Row(Modifier.appear(90).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("🛒  New Sale", { nav.tab(1) }, Modifier.weight(1f))
            BigButton("📦  Purchase", { nav.open("buy") }, Modifier.weight(1f), coral = true)
        }

        // ---- shortcuts
        Row(Modifier.appear(150).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmojiTile("🛒", "New sale", Indigo, { nav.tab(1) }, Modifier.weight(1f))
            EmojiTile("📦", "Purchase", Violet, { nav.open("buy") }, Modifier.weight(1f))
            EmojiTile("👥", "Customers", Color(0xFF0EA5E9), { nav.open("customers") }, Modifier.weight(1f))
            EmojiTile("💸", "Expenses", Amber, { nav.open("expenses") }, Modifier.weight(1f))
        }
        Row(Modifier.appear(210).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmojiTile("↩️", "Returns", Red, { nav.open("returns") }, Modifier.weight(1f))
            EmojiTile("🚚", "Vehicles", Color(0xFF14B8A6), { nav.open("vehicles") }, Modifier.weight(1f))
            EmojiTile("📒", "Cash book", Green, { nav.open("reports:5") }, Modifier.weight(1f))
            EmojiTile("📊", "Reports", Color(0xFFEC4899), { nav.open("reports:0") }, Modifier.weight(1f))
        }

        // ---- the numbers
        Row(Modifier.appear(270).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Customers owe", money(owed), Red, Modifier.weight(1f))
            StatTile("You owe suppliers", money(weOwe), Amber, Modifier.weight(1f))
        }
        Row(Modifier.appear(330).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Stock value", money(stockValue), Green, Modifier.weight(1f))
            StatTile("Low stock", "$low item${if (low == 1) "" else "s"}", if (low > 0) Amber else Green, Modifier.weight(1f))
        }

        AppCard(Modifier.appear(420)) {
            Heading("Recent sales")
            if (recent.isEmpty()) EmptyState("No sales yet. Add a product, then make your first sale.")
            recent.forEach { s -> ListRow("#${s.no} · ${s.customer}", fmtDate(s.date), money(s.total), salePill(s)) { nav.tab(1) } }
        }
    }
}
