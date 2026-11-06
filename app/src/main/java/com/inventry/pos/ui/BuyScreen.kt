package com.inventry.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inventry.pos.data.CartLine
import com.inventry.pos.data.Store
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.parties
import com.inventry.pos.data.plain

@Composable
fun BuyScreen() {
    val context = LocalContext.current
    val products = remember(Store.rev) { Store.products() }
    val history = remember(Store.rev) { Store.purchases() }
    val supplierNames = remember(Store.rev) { Store.parties("suppliers").map { it.name } }

    var supplier by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var qty by rememberSaveable { mutableStateOf("1") }
    var cost by rememberSaveable { mutableStateOf("") }
    var paidText by rememberSaveable { mutableStateOf("") }
    val cart = remember { mutableStateListOf<CartLine>() }
    var openPurchase by remember { mutableStateOf<Long?>(null) }

    val selected = products.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId) { selected?.let { cost = plain(it.cost) } }

    val total = cart.sumOf { it.amount }
    val paid = if (paidText.isBlank()) total else (paidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, total)

    TwoPane(
        left = {
            AppCard {
                Heading("New purchase")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NameField("Supplier", supplier, { supplier = it }, supplierNames, placeholder = "Supplier name")
                    ProductChooser(products, selected) { selectedId = it.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumField("Quantity", qty, { qty = it }, Modifier.weight(1f))
                        NumField("Cost each", cost, { cost = it }, Modifier.weight(1f))
                    }
                    BigButton("Add to purchase", onClick = {
                        val p = selected
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val c = cost.toDoubleOrNull() ?: 0.0
                        when {
                            p == null -> toast(context, "Choose a product first.")
                            q <= 0 -> toast(context, "Enter a quantity.")
                            else -> { cart.add(CartLine(p.id, p.name, q, c, c)); qty = "1" }
                        }
                    }, modifier = Modifier.fillMaxWidth(), tonal = true)
                }
            }
            AppCard {
                Heading("Items")
                CartRows(cart, "each") { cart.removeAt(it) }
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Paid to supplier", paidText, { paidText = it }, Modifier.fillMaxWidth(), placeholder = "Full amount")
                    Column {
                        SumLine("Total", money(total), big = true)
                        if (total - paid > 0.004) SumLine("Still owed", money(total - paid), color = MaterialTheme.colorScheme.error)
                    }
                    BigButton("Save purchase", onClick = {
                        val r = Store.createPurchase(supplier, cart.toList(), if (paidText.isBlank()) null else paid)
                        if (r.error != null) {
                            toast(context, r.error)
                        } else {
                            cart.clear(); supplier = ""; paidText = ""
                            toast(context, "Purchase saved.")
                            openPurchase = r.id
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        right = {
            AppCard {
                Heading("Purchase history")
                if (history.isEmpty()) EmptyState("No purchases yet.")
                history.forEach { p ->
                    val pill = if (p.due <= 0.004) "Paid" to Green else "Owing ${money(p.due)}" to Amber
                    ListRow("#${p.no} · ${p.supplier}", fmtDate(p.date), money(p.total), pill) { openPurchase = p.id }
                }
            }
        },
    )

    openPurchase?.let { PurchaseDialog(it) { openPurchase = null } }
}
