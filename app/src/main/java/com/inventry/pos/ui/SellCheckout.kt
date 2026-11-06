package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.LineKind
import com.inventry.pos.data.PAYMENT_METHODS
import com.inventry.pos.data.Settings
import com.inventry.pos.data.Store
import com.inventry.pos.data.money
import com.inventry.pos.data.parties
import com.inventry.pos.data.plain
import com.inventry.pos.data.qtyText
import com.inventry.pos.data.vehicles

/** A small rounded square with an emoji, the way the design marks each block of the order page. */
@Composable
private fun IconChip(emoji: String) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 18.sp) }
}

@Composable
private fun BlockTitle(emoji: String, title: String, note: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        IconChip(emoji)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (note.isNotBlank()) Text(note, color = muted(), fontSize = 12.sp)
        }
    }
}

/** The order page: customer, the lines with their IMEI, charges, how it is paid, and the totals. */
@Composable
internal fun CheckoutPage(
    cart: List<SaleItem>,
    onBack: () -> Unit,
    onChange: (Long, (SaleItem) -> SaleItem) -> Unit,
    onPlus: (Long) -> Unit,
    onMinus: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onHold: (String, Double) -> Unit,
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
    var method by rememberSaveable { mutableStateOf(PAYMENT_METHODS.first()) }

    val sub = cart.sumOf { it.amount }
    val disc = (discount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, sub)
    val rate = tax.toDoubleOrNull() ?: 0.0
    val taxAmount = (sub - disc) * rate / 100.0
    val extra = charge.toDoubleOrNull() ?: 0.0
    val total = sub - disc + taxAmount + extra
    val paid = if (received.isBlank()) total else (received.toDoubleOrNull() ?: 0.0).coerceIn(0.0, total)
    val label = chargeLabel.trim().ifEmpty { Settings.chargeLabel }

    OnePane {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleButton("←", onBack, filled = false, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text("Checkout", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("${cart.size} line${if (cart.size == 1) "" else "s"} in this sale", color = muted(), fontSize = 12.sp)
            }
        }

        AppCard {
            BlockTitle("👤", "Customer", "Leave empty for a walk-in")
            NameField("Customer name", customer, { customer = it }, customerNames, placeholder = "Walk-in customer")
        }

        AppCard {
            BlockTitle("🧾", "Items")
            cart.forEachIndexed { i, it ->
                val product = remember(it.productId, Store.rev) { if (it.kind == LineKind.PRODUCT) Store.product(it.productId) else null }
                Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (product != null) {
                        ProductThumb(product, 48.dp)
                    } else {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (it.kind == LineKind.SERVICE) "🛠️" else "✏️", fontSize = 22.sp) }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(it.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(money(it.amount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (it.kind != LineKind.PRODUCT) Pill(LineKind.label(it.kind), Violet)
                        }
                    }
                    QtyStepper(it.qty, { onMinus(it.key) }, { onPlus(it.key) })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumField("Price", it.priceText, { v -> onChange(it.key) { x -> x.copy(priceText = v) } }, Modifier.weight(1f))
                    if (it.kind == LineKind.PRODUCT) {
                        Field("IMEI / serial", it.serial, { v -> onChange(it.key) { x -> x.copy(serial = v) } }, Modifier.weight(1.4f), placeholder = "optional")
                    }
                    TextButton(onClick = { onRemove(it.key) }) { Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 16.sp) }
                }
                if (i < cart.size - 1) HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline)
            }
        }

        AppCard {
            BlockTitle("🏷️", "Charges")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
        }

        AppCard {
            BlockTitle("💳", "Payment", "How the customer pays")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAYMENT_METHODS.forEach { m -> Chip(m, method == m) { method = m } }
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Received now", received, { received = it }, Modifier.fillMaxWidth(), placeholder = "Full amount")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Full amount", received.isBlank()) { received = "" }
                    Chip("Pay later", received == "0") { received = "0" }
                }
            }
        }

        AppCard {
            SumLine("Subtotal", money(sub))
            if (disc > 0) SumLine("Discount", "− " + money(disc))
            if (taxAmount > 0) SumLine("Tax (${qtyText(rate)}%)", money(taxAmount))
            if (extra > 0) SumLine(label, money(extra))
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(money(total), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (total - paid > 0.004) SumLine("Left to pay", money(total - paid), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Hold", onClick = { onHold(customer, total) }, modifier = Modifier.weight(1f), tonal = true)
                BigButton("Complete sale", onClick = {
                    if (sub <= 0.0) {
                        toast(context, "Set a price for the items first.")
                    } else {
                        val r = Store.createSale(
                            customer, cart.map { it.toLine() }, disc, rate, label, extra, vehicleId,
                            if (received.isBlank()) null else paid, method,
                        )
                        if (r.error != null) toast(context, r.error) else onSaved(r.id)
                    }
                }, modifier = Modifier.weight(2f))
            }
        }
    }
}

/** The "done" moment after a sale is saved, with the way on to the invoice. */
@Composable
internal fun SaleDoneDialog(saleId: Long, onInvoice: () -> Unit, onClose: () -> Unit) {
    val sale = remember(saleId, Store.rev) { Store.sale(saleId) }
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).scale(pop.value).clip(CircleShape).background(Green.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("✓", color = Green, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Text("Sale completed", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.padding(top = 14.dp))
                if (sale != null) {
                    Text("Invoice #${sale.no} · ${sale.customer}", color = muted(), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    Text(money(sale.total), fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                    if (sale.due > 0.004) Text("Left to pay ${money(sale.due)}", color = Color(0xFFEF5A6F), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onInvoice) { Text("View invoice") } },
        dismissButton = { TextButton(onClick = onClose) { Text("New sale") } },
    )
}
