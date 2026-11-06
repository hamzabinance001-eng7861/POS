package com.inventry.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/** [kind] is "customers" or "suppliers". The two screens are the same apart from the words. */
@Composable
fun PartiesScreen(kind: String) {
    val context = LocalContext.current
    val isCustomer = kind == "customers"
    val one = if (isCustomer) "customer" else "supplier"
    var search by rememberSaveable { mutableStateOf("") }
    val list = remember(Store.rev, search, kind) { Store.parties(kind, search.trim()) }
    val everyone = remember(Store.rev, kind) { Store.parties(kind) }

    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<Party?>(null) }

    fun clearForm() { editId = null; name = ""; phone = ""; address = "" }

    TwoPane(
        left = {
            AppCard {
                Heading(if (editId == null) "Add a $one" else "Edit $one")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Name", name, { name = it })
                    Field("Phone / WhatsApp", phone, { phone = it }, placeholder = "e.g. 923001234567")
                    Field("Address", address, { address = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(
                            if (editId == null) "Add $one" else "Save changes",
                            onClick = {
                                val err = Store.saveParty(kind, editId, name, phone, address)
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
                val owing = everyone.sumOf { it.balance }
                Heading(if (isCustomer) "Customers (${everyone.size})" else "Suppliers (${everyone.size})")
                Text(
                    if (isCustomer) "They owe you ${money(owing)}" else "You owe them ${money(owing)}",
                    color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                Field("Search name or phone", search, { search = it })
                if (list.isEmpty()) EmptyState(if (everyone.isEmpty()) "Nobody here yet. Add one, or just type a name on a ${if (isCustomer) "sale" else "purchase"}." else "No match.")
                list.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open = p }.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.name, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(p.phone.ifBlank { "No phone" }, color = muted(), fontSize = 12.sp)
                        }
                        if (p.balance > 0.004) Pill(money(p.balance), if (isCustomer) Red else Amber) else Pill("Clear", Green)
                    }
                    HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        },
    )

    open?.let { p ->
        // Re-read it so the balance is current after a payment.
        val fresh = remember(Store.rev, p.id) { Store.parties(kind).firstOrNull { it.id == p.id } } ?: p
        PartyDialog(
            kind, fresh,
            onClose = { open = null },
            onEdit = {
                editId = fresh.id; name = fresh.name; phone = fresh.phone; address = fresh.address
                open = null
            },
        )
    }
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
        ) {
            Store.settleParty(kind, party.name, it)
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
