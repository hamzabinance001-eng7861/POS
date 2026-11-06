package com.inventry.pos.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.CartLine
import com.inventry.pos.data.Store
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText

/** Choose how many of each item on an invoice the customer is bringing back. */
@Composable
fun ReturnDialog(saleId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val sale = remember(saleId, Store.rev) { Store.sale(saleId) }
    val items = remember(saleId, Store.rev) {
        Store.saleLines(saleId).groupBy { it.productId }.map { (pid, ls) ->
            val sold = ls.sumOf { it.qty }
            Triple(ls.first(), sold, Store.returnedQty(saleId, pid))
        }
    }
    val qtys = remember { mutableStateMapOf<Long, String>() }
    var note by remember { mutableStateOf("") }
    if (sale == null) return

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Return from #${sale.no}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Goods go back into stock. If the customer had already paid, the extra is handed back.",
                    color = muted(), fontSize = 13.sp,
                )
                items.forEach { (line, sold, back) ->
                    val left = sold - back
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Sold ${qtyText(sold)} · returned ${qtyText(back)} · ${money(line.price)} each",
                                color = muted(), fontSize = 12.sp,
                            )
                        }
                        if (left > 0) {
                            NumField("Return", qtys[line.productId] ?: "", { qtys[line.productId] = it }, Modifier.weight(0.7f))
                        } else {
                            Text("All returned", color = muted(), fontSize = 12.sp)
                        }
                    }
                }
                Field("Reason (optional)", note, { note = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val picked = items.mapNotNull { (line, _, _) ->
                    val q = qtys[line.productId]?.toDoubleOrNull() ?: 0.0
                    if (q > 0) CartLine(line.productId, line.name, q, line.price, line.cost) else null
                }
                val r = Store.createSaleReturn(saleId, picked, note)
                if (r.error != null) {
                    toast(context, r.error)
                } else {
                    toast(context, "Return saved.")
                    onClose()
                }
            }) { Text("Save return") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
fun ReturnsScreen() {
    val sales = remember(Store.rev) { Store.sales(40).filter { !it.voided } }
    val returns = remember(Store.rev) { Store.saleReturns() }
    var picking by remember { mutableStateOf<Long?>(null) }

    TwoPane(
        left = {
            AppCard {
                Heading("Take goods back")
                Text("Pick the invoice the goods were sold on.", color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                if (sales.isEmpty()) EmptyState("No sales yet.")
                sales.forEach { s -> ListRow("#${s.no} · ${s.customer}", fmtDate(s.date), money(s.total), salePill(s)) { picking = s.id } }
            }
        },
        right = {
            AppCard {
                Heading("Returns so far")
                if (returns.isEmpty()) EmptyState("No returns yet.")
                returns.forEach { r ->
                    ListRow(
                        "Invoice #${r.saleNo} · ${r.customer}", fmtDate(r.date), money(r.amount),
                        if (r.refund > 0.004) "Refunded ${money(r.refund)}" to Amber else null,
                    )
                }
            }
        },
    )

    picking?.let { ReturnDialog(it) { picking = null } }
}
