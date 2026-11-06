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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Product
import com.inventry.pos.data.Store
import com.inventry.pos.data.money
import com.inventry.pos.data.plain
import com.inventry.pos.data.qtyText

@Composable
fun ProductsScreen() {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    val products = remember(Store.rev, search) { Store.products(search.trim()) }
    val total = remember(Store.rev) { Store.products().size }

    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var low by rememberSaveable { mutableStateOf("5") }
    var deleting by remember { mutableStateOf<Product?>(null) }

    fun clearForm() { editId = null; name = ""; price = ""; cost = ""; stock = ""; low = "5" }

    TwoPane(
        left = {
            AppCard {
                Heading(if (editId == null) "Add a product" else "Edit product")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Name", name, { name = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField("Selling price", price, { price = it }, Modifier.weight(1f))
                        NumField("Cost price", cost, { cost = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField(if (editId == null) "Opening stock" else "In stock", stock, { stock = it }, Modifier.weight(1f))
                        NumField("Low-stock alert", low, { low = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(
                            if (editId == null) "Add product" else "Save changes",
                            onClick = {
                                val err = Store.saveProduct(
                                    editId, name,
                                    price.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0,
                                    stock.toDoubleOrNull() ?: 0.0, low.toDoubleOrNull() ?: 0.0,
                                )
                                if (err != null) toast(context, err) else { toast(context, "Saved."); clearForm() }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (editId != null) BigButton("Cancel", onClick = { clearForm() }, modifier = Modifier.weight(1f), tonal = true)
                    }
                }
            }
        },
        right = {
            AppCard {
                Heading("Products ($total)")
                Column(Modifier.padding(top = 8.dp)) { Field("Search", search, { search = it }) }
                if (products.isEmpty()) EmptyState(if (total == 0) "No products yet. Add your first one." else "No product matches.")
                products.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Sell ${money(p.price)}  ·  Cost ${money(p.cost)}", color = muted(), fontSize = 12.sp)
                            Pill(
                                "${qtyText(p.stock)} in stock",
                                when {
                                    p.stock <= 0 -> Red
                                    p.stock <= p.low -> Amber
                                    else -> Green
                                },
                            )
                        }
                        TextButton(onClick = {
                            editId = p.id; name = p.name; price = plain(p.price); cost = plain(p.cost)
                            stock = plain(p.stock); low = plain(p.low)
                        }) { Text("Edit") }
                        TextButton(onClick = { deleting = p }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        },
    )

    deleting?.let { p ->
        ConfirmDialog("Delete ${p.name}?", "This cannot be undone.", "Delete", { deleting = null }) {
            val err = Store.deleteProduct(p.id)
            deleting = null
            toast(context, err ?: "Deleted.")
            if (err == null && editId == p.id) clearForm()
        }
    }
}
