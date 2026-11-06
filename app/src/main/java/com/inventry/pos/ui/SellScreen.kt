package com.inventry.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

/** The lines typed so far on a sale or a purchase, each with a Remove link. */
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

@Composable
fun SellScreen() {
    val context = LocalContext.current
    val products = remember(Store.rev) { Store.products() }
    val history = remember(Store.rev) { Store.sales() }
    val customerNames = remember(Store.rev) { Store.parties("customers").map { it.name } }
    val vehicles = remember(Store.rev) { Store.vehicles(onlyActive = true) }

    var customer by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var qty by rememberSaveable { mutableStateOf("1") }
    var price by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("") }
    var tax by rememberSaveable { mutableStateOf(if (Settings.taxRate > 0) plain(Settings.taxRate) else "") }
    var chargeLabel by rememberSaveable { mutableStateOf("") }
    var charge by rememberSaveable { mutableStateOf("") }
    var vehicleId by rememberSaveable { mutableStateOf(0L) }
    var received by rememberSaveable { mutableStateOf("") }
    val cart = remember { mutableStateListOf<CartLine>() }
    var openSale by remember { mutableStateOf<Long?>(null) }

    val selected = products.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId) { selected?.let { price = plain(it.price) } }

    val sub = cart.sumOf { it.amount }
    val disc = (discount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, sub)
    val rate = tax.toDoubleOrNull() ?: 0.0
    val taxAmount = (sub - disc) * rate / 100.0
    val extra = charge.toDoubleOrNull() ?: 0.0
    val total = sub - disc + taxAmount + extra
    val paid = if (received.isBlank()) total else (received.toDoubleOrNull() ?: 0.0).coerceIn(0.0, total)
    val label = chargeLabel.trim().ifEmpty { Settings.chargeLabel }

    TwoPane(
        left = {
            AppCard {
                Heading("New sale")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NameField("Customer", customer, { customer = it }, customerNames, placeholder = "Walk-in customer")
                    ProductChooser(products, selected) { selectedId = it.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumField("Quantity", qty, { qty = it }, Modifier.weight(1f))
                        NumField("Price", price, { price = it }, Modifier.weight(1f))
                    }
                    BigButton("Add to sale", onClick = {
                        val p = selected
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val pr = price.toDoubleOrNull() ?: 0.0
                        val already = cart.filter { it.productId == p?.id }.sumOf { it.qty }
                        when {
                            p == null -> toast(context, "Choose a product first.")
                            q <= 0 -> toast(context, "Enter a quantity.")
                            already + q > p.stock -> toast(context, "Only ${qtyText(p.stock)} of ${p.name} in stock.")
                            else -> { cart.add(CartLine(p.id, p.name, q, pr, p.cost)); qty = "1" }
                        }
                    }, modifier = Modifier.fillMaxWidth(), tonal = true)
                }
            }
            AppCard {
                Heading("Items")
                CartRows(cart, "each") { cart.removeAt(it) }
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Column {
                        SumLine("Subtotal", money(sub))
                        if (disc > 0) SumLine("Discount", "− " + money(disc))
                        if (taxAmount > 0) SumLine("Tax (${qtyText(rate)}%)", money(taxAmount))
                        if (extra > 0) SumLine(label, money(extra))
                        SumLine("Total", money(total), big = true)
                        if (total - paid > 0.004) SumLine("Left to pay", money(total - paid), color = MaterialTheme.colorScheme.error)
                    }
                    BigButton("Save sale", onClick = {
                        val r = Store.createSale(
                            customer, cart.toList(), disc, rate, label, extra, vehicleId,
                            if (received.isBlank()) null else paid,
                        )
                        if (r.error != null) {
                            toast(context, r.error)
                        } else {
                            cart.clear(); customer = ""; discount = ""; received = ""; charge = ""; chargeLabel = ""
                            toast(context, "Sale saved.")
                            openSale = r.id
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        right = {
            AppCard {
                Heading("Sales history")
                if (history.isEmpty()) EmptyState("Nothing sold yet.")
                history.forEach { s ->
                    ListRow("#${s.no} · ${s.customer}", fmtDate(s.date), money(s.total), salePill(s)) { openSale = s.id }
                }
            }
        },
    )

    openSale?.let { SaleDialog(it) { openSale = null } }
}
