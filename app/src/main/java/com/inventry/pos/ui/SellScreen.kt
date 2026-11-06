package com.inventry.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.CartLine
import com.inventry.pos.data.Sale
import com.inventry.pos.data.Settings
import com.inventry.pos.data.Store
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.parties
import com.inventry.pos.data.plain
import com.inventry.pos.data.qtyText
import com.inventry.pos.data.vehicles

/** The lines typed so far on a purchase, each with a Remove link. */
@Composable
fun CartRows(lines: List<CartLine>, priceWord: String, onRemove: (Int) -> Unit) {
    if (lines.isEmpty()) {
        EmptyState("Nothing added yet.")
        return
    }
    lines.forEachIndexed { i, l ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, fontWeight = FontWeight.SemiBold)
                Text("${qtyText(l.qty)} × ${money(l.price)} $priceWord", color = muted(), fontSize = 12.sp)
            }
            Text(money(l.amount), fontWeight = FontWeight.Bold)
            TextButton(onClick = { onRemove(i) }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}

fun salePill(s: Sale): Pair<String, Color> = when {
    s.voided -> "Voided" to Color.Gray
    s.due <= 0.004 -> "Paid" to Green
    s.paid > 0 -> "Part paid" to Amber
    else -> "Unpaid" to Red
}

/**
 * The Sell tab. "New sale" is a grid of products to tap; the cart bar at the bottom leads to the
 * checkout page. "History" lists every invoice.
 */
@Composable
fun SellScreen() {
    val products = remember(Store.rev) { Store.products() }
    val categories = remember(Store.rev) { Store.categories() }
    val history = remember(Store.rev) { Store.sales(60) }

    var page by rememberSaveable { mutableStateOf(0) } // 0 = new sale, 1 = history
    var checkout by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    val cart = remember { mutableStateMapOf<Long, Double>() }
    val prices = remember { mutableStateMapOf<Long, String>() }
    var openSale by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    fun add(id: Long) {
        val p = products.firstOrNull { it.id == id } ?: return
        val now = cart[id] ?: 0.0
        if (now + 1 > p.stock) toast(context, "Only ${qtyText(p.stock)} of ${p.name} in stock.") else cart[id] = now + 1
    }

    fun remove(id: Long) {
        val now = cart[id] ?: return
        if (now - 1 <= 0.0) { cart.remove(id); prices.remove(id) } else cart[id] = now - 1
    }

    val lines = cart.entries.mapNotNull { (pid, q) ->
        products.firstOrNull { it.id == pid }?.let { p ->
            CartLine(pid, p.name, q, prices[pid]?.toDoubleOrNull() ?: p.price, p.cost)
        }
    }

    // Taking the last item out of the cart leaves the checkout page.
    LaunchedEffect(lines.isEmpty()) { if (lines.isEmpty()) checkout = false }

    if (checkout && lines.isNotEmpty()) {
        BackHandler { checkout = false }
        CheckoutPage(
            lines = lines,
            prices = prices,
            onBack = { checkout = false },
            onPlus = { add(it) },
            onMinus = { remove(it) },
            onDelete = { cart.remove(it); prices.remove(it) },
            onPrice = { id, text -> prices[id] = text },
            onSaved = { cart.clear(); prices.clear(); checkout = false; openSale = it },
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("New sale", page == 0) { page = 0 }
                Chip("History", page == 1) { page = 1 }
            }
            if (page == 0) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchField(search, { search = it }, "Search name, barcode or category…")
                    if (categories.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("All", category.isEmpty()) { category = "" }
                            categories.forEach { c -> Chip(c, category == c) { category = c } }
                        }
                    }
                }
                val shown = products.filter {
                    (category.isEmpty() || it.category == category) &&
                        (search.isBlank() || it.name.contains(search.trim(), true) || it.barcode.contains(search.trim(), true) || it.category.contains(search.trim(), true))
                }
                if (products.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState("No products yet.\nGo to Items and add your first product.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(shown, key = { it.id }) { p ->
                            ProductCard(p, cart[p.id] ?: 0.0, onMinus = { remove(p.id) }, onPlus = { add(p.id) })
                        }
                    }
                }
                if (lines.isNotEmpty()) {
                    val count = lines.sumOf { it.qty }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${qtyText(count)} item${if (count == 1.0) "" else "s"}", color = muted(), fontSize = 12.sp)
                            Text(money(lines.sumOf { it.amount }), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        BigButton("Next", onClick = { checkout = true }, modifier = Modifier.padding(end = 0.dp))
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    HistoryList(history) { openSale = it }
                }
            }
        }
    }

    openSale?.let { SaleDialog(it) { openSale = null } }
}

@Composable
private fun HistoryList(history: List<Sale>, onOpen: (Long) -> Unit) {
    OnePane {
        AppCard {
            Heading("Sales history")
            if (history.isEmpty()) EmptyState("Nothing sold yet.")
            history.forEach { s -> ListRow("#${s.no} · ${s.customer}", fmtDate(s.date), money(s.total), salePill(s)) { onOpen(s.id) } }
        }
    }
}

/** The second step: who is buying, discount, tax, extra charge, and how much was received. */
@Composable
private fun CheckoutPage(
    lines: List<CartLine>,
    prices: Map<Long, String>,
    onBack: () -> Unit,
    onPlus: (Long) -> Unit,
    onMinus: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onPrice: (Long, String) -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val customerNames = remember(Store.rev) { Store.parties("customers").map { it.name } }
    val vehicles = remember(Store.rev) { Store.vehicles(onlyActive = true) }

    var customer by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("") }
    var tax by rememberSaveable { mutableStateOf(if (Settings.taxRate > 0) plain(Settings.taxRate) else "") }
    var chargeLabel by rememberSaveable { mutableStateOf("") }
    var charge by rememberSaveable { mutableStateOf("") }
    var vehicleId by rememberSaveable { mutableStateOf(0L) }
    var received by rememberSaveable { mutableStateOf("") }

    val sub = lines.sumOf { it.amount }
    val disc = (discount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, sub)
    val rate = tax.toDoubleOrNull() ?: 0.0
    val taxAmount = (sub - disc) * rate / 100.0
    val extra = charge.toDoubleOrNull() ?: 0.0
    val total = sub - disc + taxAmount + extra
    val paid = if (received.isBlank()) total else (received.toDoubleOrNull() ?: 0.0).coerceIn(0.0, total)
    val label = chargeLabel.trim().ifEmpty { Settings.chargeLabel }

    OnePane {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircleButton("←", onBack, filled = false, size = 38.dp)
            Text("Checkout", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        AppCard {
            Heading("Items")
            lines.forEachIndexed { i, l ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.SemiBold)
                        Text(money(l.amount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    NumField("Price", prices[l.productId] ?: plain(l.price), { onPrice(l.productId, it) }, Modifier.weight(0.8f))
                    Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        QtyStepper(l.qty, { onMinus(l.productId) }, { onPlus(l.productId) })
                        TextButton(onClick = { onDelete(l.productId) }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    }
                }
                if (i < lines.size - 1) HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }

        AppCard {
            Heading("Customer & charges")
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NameField("Customer", customer, { customer = it }, customerNames, placeholder = "Walk-in customer")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Discount", discount, { discount = it }, Modifier.weight(1f))
                    NumField("Tax %", tax, { tax = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Extra charge is called", chargeLabel, { chargeLabel = it.take(40) }, Modifier.weight(1.3f), placeholder = Settings.chargeLabel)
                    NumField("Extra charge", charge, { charge = it }, Modifier.weight(1f))
                }
                if (vehicles.isNotEmpty()) {
                    ChoiceField("Delivered on", listOf(0L to "No vehicle") + vehicles.map { it.id to it.name }, vehicleId) { vehicleId = it }
                }
                NumField("Received", received, { received = it }, Modifier.fillMaxWidth(), placeholder = "Full amount")
            }
        }

        AppCard {
            SumLine("Subtotal", money(sub))
            if (disc > 0) SumLine("Discount", "− " + money(disc))
            if (taxAmount > 0) SumLine("Tax (${qtyText(rate)}%)", money(taxAmount))
            if (extra > 0) SumLine(label, money(extra))
            SumLine("Total", money(total), big = true)
            if (total - paid > 0.004) SumLine("Left to pay", money(total - paid), color = MaterialTheme.colorScheme.error)
            BigButton("Save sale", onClick = {
                val r = Store.createSale(customer, lines, disc, rate, label, extra, vehicleId, if (received.isBlank()) null else paid)
                if (r.error != null) {
                    toast(context, r.error)
                } else {
                    toast(context, "Sale saved.")
                    onSaved(r.id)
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}
