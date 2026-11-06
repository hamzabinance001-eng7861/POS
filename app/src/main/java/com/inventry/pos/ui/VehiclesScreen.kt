package com.inventry.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.inventry.pos.data.EntryKind
import com.inventry.pos.data.Periods
import com.inventry.pos.data.Store
import com.inventry.pos.data.Vehicle
import com.inventry.pos.data.VehicleEntry
import com.inventry.pos.data.addVehicleEntry
import com.inventry.pos.data.deleteVehicleEntry
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText
import com.inventry.pos.data.saveVehicle
import com.inventry.pos.data.vehicleEntries
import com.inventry.pos.data.vehicleSales
import com.inventry.pos.data.vehicleSummaries
import com.inventry.pos.data.vehicleSummary
import com.inventry.pos.data.vehicles

@Composable
fun VehiclesScreen() {
    val context = LocalContext.current
    val periods = remember(Store.rev) { Periods.all() }
    var periodIndex by rememberSaveable { mutableStateOf(3) }
    val period = periods[periodIndex.coerceIn(0, periods.size - 1)]
    val summaries = remember(Store.rev, periodIndex) { Store.vehicleSummaries(period) }

    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var reg by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var open by remember { mutableStateOf<Long?>(null) }

    fun clearForm() { editId = null; name = ""; reg = ""; active = true }

    TwoPane(
        left = {
            AppCard {
                Heading(if (editId == null) "Add a vehicle" else "Edit vehicle")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Name", name, { name = it }, placeholder = "Hino truck, Suzuki pickup…")
                    Field("Registration number", reg, { reg = it })
                    if (editId != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("In use", active) { active = true }
                            Chip("Retired", !active) { active = false }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(
                            if (editId == null) "Add vehicle" else "Save changes",
                            onClick = {
                                val err = Store.saveVehicle(editId, name, reg, active)
                                if (err != null) toast(context, err) else { toast(context, "Saved."); clearForm() }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (editId != null) BigButton("Cancel", onClick = { clearForm() }, modifier = Modifier.weight(1f), tonal = true)
                    }
                }
            }
            AppCard {
                Heading("How it works")
                Text(
                    "Add a vehicle, then open it to record fuel, maintenance, driver salary, other costs and earnings. " +
                        "On a sale, choose “Delivered on” and the delivery charge counts as that vehicle’s income.",
                    color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        right = {
            AppCard {
                Heading("Vehicles")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { periods.forEachIndexed { i, p -> Chip(p.label, periodIndex == i) { periodIndex = i } } }
                if (summaries.isEmpty()) EmptyState("No vehicles yet. Add your first one.")
                summaries.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open = s.vehicle.id }.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(s.vehicle.name, 46.dp, circle = false)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.vehicle.name + if (s.vehicle.active) "" else " (retired)", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(s.vehicle.reg, "${s.deliveries} deliveries").filter { it.isNotBlank() }.joinToString(" · "),
                                color = muted(), fontSize = 12.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(s.net), fontWeight = FontWeight.Bold, color = if (s.net >= 0) Green else Red)
                            Text("net", color = muted(), fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        },
    )

    open?.let { id ->
        VehicleDialog(
            id, period,
            onClose = { open = null },
            onEdit = { v -> editId = v.id; name = v.name; reg = v.reg; active = v.active; open = null },
        )
    }
}

@Composable
private fun VehicleDialog(id: Long, period: com.inventry.pos.data.Period, onClose: () -> Unit, onEdit: (Vehicle) -> Unit) {
    val context = LocalContext.current
    val vehicle = remember(Store.rev, id) { Store.vehicles().firstOrNull { it.id == id } }
    val entries = remember(Store.rev, id, period) { Store.vehicleEntries(id, period) }
    val delivered = remember(Store.rev, id, period) { Store.vehicleSales(id, period) }
    var recordKind by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<VehicleEntry?>(null) }
    val s = remember(Store.rev, id, period) { vehicle?.let { Store.vehicleSummary(it, period) } }
    if (vehicle == null || s == null) return

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(vehicle.name, fontWeight = FontWeight.Bold)
                Text(listOf(vehicle.reg, period.label).filter { it.isNotBlank() }.joinToString(" · "), color = muted(), fontSize = 12.sp)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SumLine("Delivery charges (${s.deliveries})", money(s.charges))
                SumLine("Other earnings", money(s.earnings))
                SumLine("Fuel", "− " + money(s.fuel))
                SumLine("Maintenance", "− " + money(s.maintenance))
                SumLine("Driver salary", "− " + money(s.salary))
                SumLine("Other costs", "− " + money(s.other))
                SumLine("Net", money(s.net), big = true, color = if (s.net >= 0) Green else MaterialTheme.colorScheme.error)

                Text("Record", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryKind.all.forEach { (k, label) -> Chip(label, false) { recordKind = k } }
                }

                Text("Entries", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                if (entries.isEmpty()) EmptyState("Nothing recorded in this period.")
                entries.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(EntryKind.name(e.kind) + if (e.litres > 0) " · ${qtyText(e.litres)} L" else "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(listOf(fmtDate(e.date), e.note).filter { it.isNotBlank() }.joinToString(" · "), color = muted(), fontSize = 12.sp)
                        }
                        Text(
                            (if (e.kind == EntryKind.EARNING) "+ " else "− ") + money(e.amount),
                            color = if (e.kind == EntryKind.EARNING) Green else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        TextButton(onClick = { deleting = e }) { Text("✕", color = MaterialTheme.colorScheme.error) }
                    }
                }

                if (delivered.isNotEmpty()) {
                    Text("Deliveries", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                    delivered.take(20).forEach { d ->
                        ListRow("#${d.no} · ${d.customer}", fmtDate(d.date), if (d.charge > 0) money(d.charge) else "No charge", null)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = { TextButton(onClick = { onEdit(vehicle) }) { Text("Edit vehicle") } },
    )

    recordKind?.let { k ->
        EntryDialog(vehicle.id, k) { recordKind = null }
    }
    deleting?.let { e ->
        ConfirmDialog("Delete this entry?", "${EntryKind.name(e.kind)} · ${money(e.amount)}", "Delete", { deleting = null }) {
            Store.deleteVehicleEntry(e.id)
            deleting = null
            toast(context, "Deleted.")
        }
    }
}

@Composable
private fun EntryDialog(vehicleId: Long, kind: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text(EntryKind.name(kind), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Amount", amount, { amount = it }, Modifier.fillMaxWidth())
                if (kind == EntryKind.FUEL) NumField("Litres", litres, { litres = it }, Modifier.fillMaxWidth())
                Field(
                    if (kind == EntryKind.SALARY) "Driver name / month" else "Note (optional)", note, { note = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = Store.addVehicleEntry(vehicleId, kind, amount.toDoubleOrNull() ?: 0.0, litres.toDoubleOrNull() ?: 0.0, note)
                if (err != null) toast(context, err) else { toast(context, "Saved."); onClose() }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
