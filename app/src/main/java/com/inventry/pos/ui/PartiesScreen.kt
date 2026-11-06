package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Party
import com.inventry.pos.data.Settings
import com.inventry.pos.data.Store
import com.inventry.pos.data.deleteParty
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.parties
import com.inventry.pos.data.saveParty
import com.inventry.pos.data.settleParty

/** The Parties tab: customers and suppliers behind two chips, with a floating "New Party" button. */
@Composable
fun PartiesTab(initial: String) {
    var kind by rememberSaveable(initial) { mutableStateOf(initial) }
    var search by rememberSaveable { mutableStateOf("") }
    var form by remember { mutableStateOf<Party?>(null) }
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Party?>(null) }
    val isCustomer = kind == "customers"
    val list = remember(Store.rev, search, kind) { Store.parties(kind, search.trim()) }
    val everyone = remember(Store.rev, kind) { Store.parties(kind) }
    val owing = everyone.sumOf { it.balance }

    Box(Modifier.fillMaxSize()) {
        OnePane {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Customer", isCustomer) { kind = "customers" }
                Chip("Supplier", !isCustomer) { kind = "suppliers" }
            }
            SearchField(search, { search = it }, "Search by name or phone…")
            Text(
                if (isCustomer) "${everyone.size} customers · they owe you ${money(owing)}" else "${everyone.size} suppliers · you owe them ${money(owing)}",
                color = muted(), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp),
            )
            AppCard {
                if (list.isEmpty()) {
                    EmptyState(
                        if (everyone.isEmpty()) "Nobody here yet. Tap “New Party”, or just type a name on a ${if (isCustomer) "sale" else "purchase"}."
                        else "No match.",
                    )
                }
                list.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().clickable { open = p }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, 46.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(p.phone.ifBlank { "No phone" }, color = muted(), fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(p.balance), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (p.balance > 0.004) Pill(if (isCustomer) "Due" else "To pay", if (isCustomer) Red else Amber) else Pill("Settled", Green)
                        }
                    }
                    if (i < list.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(76.dp))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp)) {
            BigButton(if (isCustomer) "＋ New Customer" else "＋ New Supplier", onClick = { form = null; adding = true }, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    if (adding || form != null) {
        PartyFormDialog(kind, form) { adding = false; form = null }
    }

    open?.let { p ->
        // Re-read it so the balance is current after a payment.
        val fresh = remember(Store.rev, p.id) { Store.parties(kind).firstOrNull { it.id == p.id } } ?: p
        PartyDialog(
            kind, fresh,
            onClose = { open = null },
            onEdit = { form = fresh; open = null },
        )
    }
}

@Composable
private fun PartyFormDialog(kind: String, party: Party?, onClose: () -> Unit) {
    val context = LocalContext.current
    val one = if (kind == "customers") "customer" else "supplier"
    var name by remember { mutableStateOf(party?.name ?: "") }
    var phone by remember { mutableStateOf(party?.phone ?: "") }
    var address by remember { mutableStateOf(party?.address ?: "") }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (party == null) "New $one" else "Edit $one", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", name, { name = it })
                Field("Phone / WhatsApp", phone, { phone = it }, placeholder = "e.g. 923001234567")
                Field("Address", address, { address = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = Store.saveParty(kind, party?.id, name, phone, address)
                if (err != null) toast(context, err) else { toast(context, "Saved."); onClose() }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun PartyDialog(kind: String, party: Party, onClose: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val isCustomer = kind == "customers"
    var settling by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var openSale by remember { mutableStateOf<Long?>(null) }
    var openPurchase by remember { mutableStateOf<Long?>(null) }
    val sales = remember(Store.rev, party.name) { if (isCustomer) Store.salesOf(party.name) else emptyList() }
    val purchases = remember(Store.rev, party.name) { if (!isCustomer) Store.purchasesOf(party.name) else emptyList() }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(party.name, 42.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(party.name, fontWeight = FontWeight.Bold)
                    Text(party.phone.ifBlank { "No phone" }, color = muted(), fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (party.address.isNotBlank()) Text(party.address, color = muted(), fontSize = 13.sp)
                SumLine(
                    if (isCustomer) "They owe you" else "You owe them",
                    money(party.balance), big = true,
                    color = if (party.balance > 0.004) MaterialTheme.colorScheme.error else Green,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (party.phone.isNotBlank()) TextButton(onClick = { dial(context, party.phone) }) { Text("Call") }
                    if (isCustomer && party.balance > 0.004) {
                        TextButton(onClick = {
                            whatsApp(
                                context, party.phone,
                                "Hello ${party.name}, a friendly reminder from ${Settings.shop}: your balance is ${money(party.balance)}. Thank you!",
                            )
                        }) { Text("WhatsApp") }
                    }
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
                Text("Invoices", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                if (isCustomer) {
                    if (sales.isEmpty()) EmptyState("No sales to this customer yet.")
                    sales.take(15).forEach { s ->
                        ListRow("#${s.no}", fmtDate(s.date), money(s.total), salePill(s)) { openSale = s.id }
                    }
                } else {
                    if (purchases.isEmpty()) EmptyState("No purchases from this supplier yet.")
                    purchases.take(15).forEach { p ->
                        val pill = if (p.due <= 0.004) "Paid" to Green else "Owing ${money(p.due)}" to Amber
                        ListRow("#${p.no}", fmtDate(p.date), money(p.total), pill) { openPurchase = p.id }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (party.balance > 0.004) {
                    TextButton(onClick = { settling = true }) { Text(if (isCustomer) "Receive payment" else "Pay supplier") }
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = { TextButton(onClick = { deleting = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
    )

    if (settling) {
        AmountDialog(
            if (isCustomer) "Receive payment" else "Pay supplier",
            "${party.name} · balance ${money(party.balance)}. It goes against the oldest invoices first.",
            party.balance, { settling = false },
        ) { amount, how ->
            Store.settleParty(kind, party.name, amount, how)
            settling = false
            toast(context, "Payment saved.")
        }
    }
    if (deleting) {
        ConfirmDialog("Delete ${party.name}?", "Only people with no invoices can be deleted.", "Delete", { deleting = false }) {
            val err = Store.deleteParty(kind, party)
            deleting = false
            toast(context, err ?: "Deleted.")
            if (err == null) onClose()
        }
    }
    openSale?.let { SaleDialog(it) { openSale = null } }
    openPurchase?.let { PurchaseDialog(it) { openPurchase = null } }
}
