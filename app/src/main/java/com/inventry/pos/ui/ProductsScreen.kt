package com.inventry.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    val categories = remember(Store.rev) { Store.categories() }
    val products = remember(Store.rev, search, categoryFilter) {
        Store.products(search.trim()).filter { categoryFilter.isEmpty() || it.category == categoryFilter }
    }
    val total = remember(Store.rev) { Store.products().size }

    var showForm by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var low by rememberSaveable { mutableStateOf("5") }
    var category by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var newImage by remember { mutableStateOf<ByteArray?>(null) }
    var removeImage by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    var adjusting by remember { mutableStateOf<Product?>(null) }

    fun clearForm() {
        editId = null; name = ""; price = ""; cost = ""; stock = ""; low = "5"
        category = ""; barcode = ""; unit = ""; newImage = null; removeImage = false
    }

    val pickImage = rememberImagePicker { newImage = it; removeImage = false }
    val existing = remember(editId, Store.rev) { editId?.let { Store.productImage(it) } }
    val shown = newImage ?: if (removeImage) null else existing

    if (showForm) {
        BackHandler { showForm = false; clearForm() }
        OnePane {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleButton("←", { showForm = false; clearForm() }, filled = false, size = 38.dp)
                Text(if (editId == null) "Add New Product" else "Edit Product", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (shown != null) BytesImage(shown, 84.dp, corner = 20.dp) else Avatar(name.ifBlank { "+" }, 84.dp, circle = false)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BigButton(if (shown == null) "Add photo" else "Change photo", onClick = pickImage, tonal = true)
                        if (shown != null) {
                            TextButton(onClick = { newImage = null; removeImage = true }) {
                                Text("Remove photo", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Product name", name, { name = it }, placeholder = "e.g. Cement bag")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField("Selling price", price, { price = it }, Modifier.weight(1f))
                        NumField("Cost price", cost, { cost = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField(if (editId == null) "Opening stock" else "In stock", stock, { stock = it }, Modifier.weight(1f))
                        NumField("Low-stock alert", low, { low = it }, Modifier.weight(1f))
                    }
                    NameField("Category", category, { category = it }, categories, placeholder = "e.g. Cement, Pipes")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Barcode / code", barcode, { barcode = it }, Modifier.weight(1.3f))
                        Field("Unit", unit, { unit = it }, Modifier.weight(1f), placeholder = "bag, pc, ft")
                    }
                    BigButton(
                        if (editId == null) "Add product" else "Save changes",
                        onClick = {
                            val err = Store.saveProduct(
                                editId, name,
                                price.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0,
                                stock.toDoubleOrNull() ?: 0.0, low.toDoubleOrNull() ?: 0.0,
                                category, barcode, unit, newImage, removeImage,
                            )
                            if (err != null) toast(context, err) else { toast(context, "Saved."); showForm = false; clearForm() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    } else {
        OnePane {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(search, { search = it }, "Search items…", Modifier.weight(1f))
                BigButton("＋ Add Item", onClick = { clearForm(); showForm = true })
            }
            if (categories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All", categoryFilter.isEmpty()) { categoryFilter = "" }
                    categories.forEach { c -> Chip(c, categoryFilter == c) { categoryFilter = c } }
                }
            }
            Text("$total product${if (total == 1) "" else "s"}", color = muted(), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            AppCard {
                if (products.isEmpty()) EmptyState(if (total == 0) "No products yet. Tap “Add Item”." else "No product matches.")
                products.forEachIndexed { i, p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                editId = p.id; name = p.name; price = plain(p.price); cost = plain(p.cost)
                                stock = plain(p.stock); low = plain(p.low); category = p.category
                                barcode = p.barcode; unit = p.unit; newImage = null; removeImage = false
                                showForm = true
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProductThumb(p, 54.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                listOf(p.category, p.barcode).filter { it.isNotBlank() }.joinToString("  ·  ").ifEmpty { "No category" },
                                color = muted(), fontSize = 12.sp,
                            )
                            Text("Sell ${money(p.price)}  ·  Cost ${money(p.cost)}", color = muted(), fontSize = 12.sp)
                            Pill(
                                qtyText(p.stock) + (if (p.unit.isNotBlank()) " ${p.unit}" else "") + " in stock",
                                when {
                                    p.stock <= 0 -> Red
                                    p.stock <= p.low -> Amber
                                    else -> Green
                                },
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { adjusting = p }) { Text("Stock") }
                            TextButton(onClick = { deleting = p }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (i < products.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }

    deleting?.let { p ->
        ConfirmDialog("Delete ${p.name}?", "This cannot be undone.", "Delete", { deleting = null }) {
            val err = Store.deleteProduct(p.id)
            deleting = null
            toast(context, err ?: "Deleted.")
        }
    }
    adjusting?.let { p -> AdjustDialog(p) { adjusting = null } }
}

/** Add stock found, or take off stock that was lost, broken or used, and remember why. */
@Composable
private fun AdjustDialog(p: Product, onClose: () -> Unit) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(true) }
    var qty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Adjust ${p.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("In stock now: ${qtyText(p.stock)}", color = muted(), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Add stock", adding) { adding = true }
                    Chip("Remove stock", !adding) { adding = false }
                }
                NumField("Quantity", qty, { qty = it }, Modifier.fillMaxWidth())
                Field("Reason", reason, { reason = it }, placeholder = if (adding) "Found, counted, gift…" else "Damaged, lost, own use…")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toDoubleOrNull() ?: 0.0
                val err = Store.adjustStock(p.id, if (adding) q else -q, reason)
                if (err != null) toast(context, err) else { toast(context, "Stock updated."); onClose() }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
