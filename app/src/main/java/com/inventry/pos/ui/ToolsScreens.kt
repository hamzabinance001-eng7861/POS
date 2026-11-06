package com.inventry.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Store
import com.inventry.pos.data.findSerial
import com.inventry.pos.data.fmtDay
import com.inventry.pos.data.plain
import com.inventry.pos.data.qtyText

/** Type an IMEI or serial number (or part of one) to see who bought the phone and whether it is still under warranty. */
@Composable
fun SerialLookupScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, Store.rev) { Store.findSerial(query) }
    val now = System.currentTimeMillis()

    OnePane {
        SearchField(query, { query = it }, "Type or paste an IMEI / serial number…")
        AppCard {
            Heading("Warranty & IMEI check")
            Text(
                "Searches the IMEI or serial numbers saved on your sales and purchases. Enter at least 3 characters.",
                color = muted(), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            if (query.trim().length < 3) {
                EmptyState("Start typing an IMEI or serial number.")
            } else if (hits.isEmpty()) {
                EmptyState("Nothing found for “${query.trim()}”.")
            }
            hits.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.product, fontWeight = FontWeight.Bold)
                        Text("IMEI/Serial: ${h.serial}", color = muted(), fontSize = 12.sp)
                        Text(
                            (if (h.bought) "Bought from " else "Sold to ") + h.party + " · " + (if (h.bought) "Purchase" else "Invoice") + " #${h.invoiceNo} · " + fmtDay(h.date),
                            color = muted(), fontSize = 12.sp,
                        )
                    }
                    val until = h.warrantyUntil
                    when {
                        h.bought -> Pill("Purchased", Violet)
                        until == null -> Pill("No warranty", Color.Gray)
                        until > now -> Pill("${((until - now) / 86_400_000L) + 1} days left", Green)
                        else -> Pill("Warranty ended", Red)
                    }
                }
                if (i < hits.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Count what is really on the shelf; the app works out the differences and corrects the stock. */
@Composable
fun StockCountScreen() {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    val products = remember(Store.rev, search) { Store.products(search.trim()) }
    val counted = remember { mutableStateMapOf<Long, String>() }
    var confirm by remember { mutableStateOf(false) }

    val changes = Store.products().mapNotNull { p ->
        val c = counted[p.id]?.toDoubleOrNull() ?: return@mapNotNull null
        if (kotlin.math.abs(c - p.stock) < 1e-9) null else p to c
    }

    OnePane {
        SearchField(search, { search = it }, "Search items to count…")
        AppCard {
            Heading("Stock count")
            Text(
                "Type how many you actually have. Leave a box empty to skip that item. Then apply: the difference is recorded as a stock adjustment.",
                color = muted(), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            if (products.isEmpty()) EmptyState("No products.")
            products.forEachIndexed { i, p ->
                val c = counted[p.id]?.toDoubleOrNull()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductThumb(p, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "In the app: ${qtyText(p.stock)}" + if (c != null && c != p.stock) "  ·  difference ${(if (c > p.stock) "+" else "")}${qtyText(c - p.stock)}" else "",
                            color = if (c != null && c != p.stock) Amber else muted(), fontSize = 12.sp,
                        )
                    }
                    NumField("Counted", counted[p.id] ?: "", { counted[p.id] = it }, Modifier.weight(0.7f))
                }
                if (i < products.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
        BigButton(
            if (changes.isEmpty()) "Nothing to apply" else "Apply ${changes.size} correction${if (changes.size == 1) "" else "s"}",
            onClick = { if (changes.isNotEmpty()) confirm = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirm) {
        ConfirmDialog(
            "Apply the count?", "${changes.size} product${if (changes.size == 1) "" else "s"} will have their stock changed to what you counted.",
            "Apply", { confirm = false },
        ) {
            changes.forEach { (p, c) -> Store.adjustStock(p.id, c - p.stock, "Stock count") }
            counted.clear()
            confirm = false
            toast(context, "Stock corrected.")
        }
    }
}
