package com.inventry.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.inventry.pos.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.CartLine
import com.inventry.pos.data.LineKind
import com.inventry.pos.data.Product
import com.inventry.pos.data.Sale
import com.inventry.pos.data.ServiceItem
import com.inventry.pos.data.Store
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.plain
import com.inventry.pos.data.qtyText
import org.json.JSONArray
import org.json.JSONObject

/** One line in the sale being built. Products use their id as the key; services and typed-in lines use negative keys. */
internal data class SaleItem(
    val key: Long,
    val kind: Int,
    val productId: Long,
    val name: String,
    val qty: Double,
    val priceText: String,
    val cost: Double,
    val serial: String = "",
    val warranty: Int = 0,
) {
    val price: Double get() = priceText.toDoubleOrNull() ?: 0.0
    val amount: Double get() = qty * price

    fun toLine() = CartLine(if (kind == LineKind.PRODUCT) productId else 0L, name, qty, price, cost, kind, serial, warranty)
}

/** The sale being built lives here, so it stays when you switch tabs. */
internal object CartHolder {
    val items = mutableStateListOf<SaleItem>()
}

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
                if (l.serial.isNotBlank()) Text("IMEI/Serial: ${l.serial}", color = muted(), fontSize = 11.sp)
            }
            Text(money(l.amount), fontWeight = FontWeight.Bold)
            TextButton(onClick = { onRemove(i) }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}

fun salePill(s: Sale): Pair<String, Color> = when {
    s.voided -> "Voided" to Color.Gray
    s.due <= 0.004 -> "Paid" to Green
    s.paid > 0 -> "Part paid" to Amber
    else -> "Unpaid" to Red
}

/** Rounded grey switch between a few options; the chosen one is a white pill. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) MaterialTheme.colorScheme.primary else muted())
            }
        }
    }
}

private fun encodeItems(cart: List<SaleItem>): String {
    val arr = JSONArray()
    cart.forEach {
        arr.put(JSONObject().apply {
            put("key", it.key); put("kind", it.kind); put("productId", it.productId); put("name", it.name)
            put("qty", it.qty); put("price", it.priceText); put("cost", it.cost); put("serial", it.serial); put("warranty", it.warranty)
        })
    }
    return arr.toString()
}

private fun decodeItems(json: String): List<SaleItem> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        SaleItem(
            o.getLong("key"), o.getInt("kind"), o.getLong("productId"), o.getString("name"), o.getDouble("qty"),
            o.getString("price"), o.getDouble("cost"), o.optString("serial", ""), o.optInt("warranty", 0),
        )
    }
} catch (e: Exception) {
    emptyList()
}

/**
 * The Sell tab. "New sale" has three sections: Products (tap to add, search, search by photo),
 * Services (saved repairs and other services) and Custom (type any name and price). A violet bar at
 * the bottom leads to checkout. "Held" are parked sales; "History" lists every invoice.
 */
@Composable
fun SellScreen() {
    val context = LocalContext.current
    val products = remember(Store.rev) { Store.products() }
    val categories = remember(Store.rev) { Store.categories() }
    val services = remember(Store.rev) { Store.services() }
    val history = remember(Store.rev) { Store.sales(60) }
    val held = remember(Store.rev) { Store.heldSales() }

    var page by rememberSaveable { mutableStateOf(0) }       // 0 new sale, 1 held, 2 history
    var section by rememberSaveable { mutableStateOf(0) }    // 0 products, 1 services, 2 custom
    var checkout by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    val cart = CartHolder.items
    var nextCustomKey by remember { mutableStateOf(-1_000_000L) }
    var openSale by remember { mutableStateOf<Long?>(null) }
    var doneSale by remember { mutableStateOf<Long?>(null) }
    var editService by remember { mutableStateOf<ServiceItem?>(null) }
    var newService by remember { mutableStateOf(false) }
    var photoSearch by remember { mutableStateOf(false) }

    fun qtyOf(key: Long) = cart.firstOrNull { it.key == key }?.qty ?: 0.0

    fun addProduct(p: Product) {
        val i = cart.indexOfFirst { it.key == p.id }
        val now = if (i >= 0) cart[i].qty else 0.0
        when {
            now + 1 > p.stock -> toast(context, "Only ${qtyText(p.stock)} of ${p.name} in stock.")
            i >= 0 -> cart[i] = cart[i].copy(qty = now + 1)
            else -> cart.add(SaleItem(p.id, LineKind.PRODUCT, p.id, p.name, 1.0, plain(p.price), p.cost, "", p.warrantyDays))
        }
    }

    fun addService(s: ServiceItem) {
        val key = -s.id
        val i = cart.indexOfFirst { it.key == key }
        if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + 1)
        else cart.add(SaleItem(key, LineKind.SERVICE, 0L, s.name, 1.0, plain(s.price), s.cost))
    }

    fun plus(key: Long) {
        val it = cart.firstOrNull { x -> x.key == key } ?: return
        if (it.kind == LineKind.PRODUCT) {
            products.firstOrNull { p -> p.id == it.productId }?.let { p -> addProduct(p) }
        } else {
            val i = cart.indexOf(it)
            cart[i] = it.copy(qty = it.qty + 1)
        }
    }

    fun minus(key: Long) {
        val i = cart.indexOfFirst { it.key == key }
        if (i < 0) return
        if (cart[i].qty - 1 <= 0.0) cart.removeAt(i) else cart[i] = cart[i].copy(qty = cart[i].qty - 1)
    }

    // Taking the last item out of the cart leaves the checkout page.
    LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty()) checkout = false }

    if (checkout && cart.isNotEmpty()) {
        BackHandler { checkout = false }
        CheckoutPage(
            cart = cart,
            onBack = { checkout = false },
            onChange = { key, change ->
                val i = cart.indexOfFirst { it.key == key }
                if (i >= 0) cart[i] = change(cart[i])
            },
            onPlus = { plus(it) },
            onMinus = { minus(it) },
            onRemove = { key -> cart.removeAll { it.key == key } },
            onHold = { customer, total ->
                Store.holdSale(customer, encodeItems(cart.toList()), total, cart.size)
                cart.clear()
                toast(context, "Sale put on hold.")
            },
            onSaved = { id -> cart.clear(); checkout = false; doneSale = id },
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("New sale", page == 0) { page = 0 }
                Chip("Held${if (held.isEmpty()) "" else " (${held.size})"}", page == 1) { page = 1 }
                Chip("History", page == 2) { page = 2 }
            }

            when (page) {
                0 -> {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Segmented(listOf("Products", "Services", "Custom"), section) { section = it }
                    }
                    when (section) {
                        0 -> {
                            Row(
                                Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SearchField(search, { search = it }, "Search name, barcode or category…", Modifier.weight(1f))
                                IconCircle(painterResource(R.drawable.ic_camera), { photoSearch = true }, 48.dp)
                            }
                            if (categories.isNotEmpty()) {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Chip("All", category.isEmpty()) { category = "" }
                                    categories.forEach { c -> Chip(c, category == c) { category = c } }
                                }
                            }
                            val shown = products.filter {
                                (category.isEmpty() || it.category == category) &&
                                    (search.isBlank() || it.name.contains(search.trim(), true) || it.barcode.contains(search.trim(), true) || it.category.contains(search.trim(), true))
                            }
                            if (products.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    EmptyState("No products yet.\nAdd some in Items, or use Services and Custom to sell without stock.")
                                }
                            } else {
                                LazyColumn(
                                    Modifier.weight(1f),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(shown, key = { it.id }) { p ->
                                        ProductRow(p, qtyOf(p.id), onMinus = { minus(p.id) }, onPlus = { addProduct(p) })
                                    }
                                }
                            }
                        }
                        1 -> {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Services", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text("Repairs and other work. No stock.", color = muted(), fontSize = 12.sp)
                                }
                                BigButton("＋ New", onClick = { newService = true })
                            }
                            if (services.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    EmptyState("No services saved yet.\nTap “New” to add one, e.g. Screen replacement.")
                                }
                            } else {
                                LazyColumn(
                                    Modifier.weight(1f),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(services, key = { it.id }) { s ->
                                        ServiceRow(
                                            "🛠️", s.name, s.price, qtyOf(-s.id), "",
                                            onMinus = { minus(-s.id) }, onPlus = { addService(s) }, onEdit = { editService = s },
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            CustomItemForm(onAdd = { name, qty, price, cost ->
                                nextCustomKey -= 1
                                cart.add(SaleItem(nextCustomKey, LineKind.CUSTOM, 0L, name, qty, plain(price), cost))
                                toast(context, "$name added.")
                            }, modifier = Modifier.weight(1f))
                        }
                    }

                    AnimatedVisibility(
                        visible = cart.isNotEmpty(),
                        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        CartBar(count = cart.sumOf { it.qty }, lines = cart.size, total = cart.sumOf { it.amount }) { checkout = true }
                    }
                }
                1 -> HeldList(held, modifier = Modifier.weight(1f),
                    onResume = { h ->
                        val back = decodeItems(h.json)
                        if (back.isEmpty()) {
                            toast(context, "Could not open that sale.")
                        } else {
                            cart.clear(); cart.addAll(back)
                            Store.deleteHeld(h.id)
                            page = 0; checkout = true
                        }
                    },
                    onDelete = { Store.deleteHeld(it.id) })
                else -> Column(Modifier.weight(1f).fillMaxWidth()) { HistoryList(history) { openSale = it } }
            }
        }
    }

    openSale?.let { SaleDialog(it) { openSale = null } }
    doneSale?.let { id -> SaleDoneDialog(id, onInvoice = { openSale = id; doneSale = null }, onClose = { doneSale = null }) }
    if (newService) ServiceDialog(null) { newService = false }
    editService?.let { s -> ServiceDialog(s) { editService = null } }
    if (photoSearch) {
        ImageSearchDialog(onPick = { p -> addProduct(p); section = 0 }, onClose = { photoSearch = false })
    }
}

/** The violet pill at the bottom: how many items, the total, and the way on to checkout. */
@Composable
private fun CartBar(count: Double, lines: Int, total: Double, onNext: () -> Unit) {
    val shownTotal by animateFloatAsState(total.toFloat(), tween(320), label = "cartTotal")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onNext)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Proceed to checkout", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            "$lines line${if (lines == 1) "" else "s"}  ·  ${money(shownTotal.toDouble())}",
            color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        )
        Text("  →", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Sell something that is not in the product list: type its name, quantity and price. */
@Composable
private fun CustomItemForm(onAdd: (String, Double, Double, Double) -> Unit, modifier: Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf("1") }
    var price by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            Text("Custom item", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "For anything not in your product list: a part you bought on the spot, a repair you will name yourself, a one-off charge.",
                color = muted(), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("What is it?", name, { name = it }, placeholder = "e.g. Display replacement – Samsung A12")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Quantity", qty, { qty = it }, Modifier.weight(1f))
                    NumField("Price", price, { price = it }, Modifier.weight(1f))
                }
                NumField("What it cost you (optional)", cost, { cost = it }, Modifier.fillMaxWidth(), placeholder = "For profit reports")
                BigButton("Add to sale", onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    when {
                        name.isBlank() -> toast(context, "Type a name for the item.")
                        q <= 0 -> toast(context, "Enter a quantity.")
                        else -> {
                            onAdd(name.trim(), q, price.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0)
                            name = ""; qty = "1"; price = ""; cost = ""
                        }
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeldList(
    held: List<com.inventry.pos.data.HeldSale>,
    modifier: Modifier,
    onResume: (com.inventry.pos.data.HeldSale) -> Unit,
    onDelete: (com.inventry.pos.data.HeldSale) -> Unit,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppCard {
            Text("Held sales", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Sales you put on hold at checkout. Open one to carry on.", color = muted(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            if (held.isEmpty()) EmptyState("Nothing on hold.")
            held.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.customer.ifBlank { "Walk-in customer" }, fontWeight = FontWeight.SemiBold)
                        Text("${h.lines} line${if (h.lines == 1) "" else "s"} · ${fmtDate(h.date)}", color = muted(), fontSize = 12.sp)
                    }
                    Text(money(h.total), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onResume(h) }) { Text("Open") }
                    TextButton(onClick = { onDelete(h) }) { Text("✕", color = MaterialTheme.colorScheme.error) }
                }
                if (i < held.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
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

/** Add, change or delete a saved service. */
@Composable
private fun ServiceDialog(service: ServiceItem?, onClose: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(service?.name ?: "") }
    var price by remember { mutableStateOf(service?.let { plain(it.price) } ?: "") }
    var cost by remember { mutableStateOf(service?.let { if (it.cost > 0) plain(it.cost) else "" } ?: "") }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (service == null) "New service" else "Edit service", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Service name", name, { name = it }, placeholder = "e.g. Screen replacement")
                NumField("Usual price", price, { price = it }, Modifier.fillMaxWidth())
                NumField("Your cost (optional)", cost, { cost = it }, Modifier.fillMaxWidth(), placeholder = "Parts, for profit reports")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = Store.saveService(service?.id, name, price.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0)
                if (err != null) toast(context, err) else { toast(context, "Saved."); onClose() }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (service != null) {
                    TextButton(onClick = { Store.deleteService(service.id); toast(context, "Deleted."); onClose() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}
