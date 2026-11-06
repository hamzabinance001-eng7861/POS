package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Periods
import com.inventry.pos.data.Settings
import com.inventry.pos.data.Store
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money

@Composable
private fun Stat(label: String, value: String, accent: Color, modifier: Modifier) {
    AppCard(modifier) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 2.dp)) {
            Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    }
}

/** [go] switches tab: 1 = Sell, 2 = Buy, 4 = Reports. */
@Composable
fun HomeScreen(go: (Int) -> Unit) {
    val today = remember(Store.rev) { Store.salesByDay(Periods.today()) }
    val sold = today.sumOf { it.total }
    val count = today.sumOf { it.count }
    val owed = remember(Store.rev) { Store.customersOweTotal() }
    val stockValue = remember(Store.rev) { Store.stockValue() }
    val low = remember(Store.rev) { Store.products().count { it.stock <= it.low } }
    val recent = remember(Store.rev) { Store.sales(5) }

    OnePane {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(heroBrush())
                .padding(22.dp)
        ) {
            Column {
                Text(Settings.shop, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Sales today", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Text(money(sold), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "$count invoice${if (count == 1) "" else "s"} today",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Customers owe", money(owed), Red, Modifier.weight(1f))
            Stat("Stock value", money(stockValue), Green, Modifier.weight(1f))
        }

        if (low > 0) {
            AppCard {
                Text("$low product${if (low == 1) " is" else "s are"} low or out of stock", fontWeight = FontWeight.SemiBold, color = Amber)
                Text("Open Reports → Low stock to see which.", color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("+ New sale", { go(1) }, Modifier.weight(1f))
            BigButton("+ New purchase", { go(2) }, Modifier.weight(1f), tonal = true)
        }

        AppCard {
            Heading("Recent sales")
            if (recent.isEmpty()) EmptyState("No sales yet. Add a product, then make your first sale.")
            recent.forEach { s -> ListRow("#${s.no} · ${s.customer}", fmtDate(s.date), money(s.total), salePill(s)) { go(1) } }
        }
    }
}
