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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.CartLine
import com.inventry.pos.data.Store
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText

@Composable
private fun LineTable(lines: List<CartLine>, priceHeader: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Item", Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted())
        Text("Qty", Modifier.weight(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted(), textAlign = TextAlign.End)
        Text(priceHeader, Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted(), textAlign = TextAlign.End)
        Text("Amount", Modifier.weight(1.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted(), textAlign = TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    lines.forEach { l ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
            Text(l.name, Modifier.weight(2f), fontSize = 13.sp)
            Text(qtyText(l.qty), Modifier.weight(0.8f), fontSize = 13.sp, textAlign = TextAlign.End)
            Text(money(l.price), Modifier.weight(1.2f), fontSize = 13.sp, textAlign = TextAlign.End)
            Text(money(l.amount), Modifier.weight(1.4f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
fun SaleDialog(saleId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val sale = remember(Store.rev, saleId) { Store.sale(saleId) }
    val lines = remember(Store.rev, saleId) { Store.saleLines(saleId) }
    var paying by remember { mutableStateOf(false) }
    var voiding by remember { mutableStateOf(false) }
    if (sale == null) return

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Invoice #${sale.no}" + if (sale.voided) "  (voided)" else "", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("${sale.customer}  ·  ${fmtDate(sale.date)}", color = muted(), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                LineTable(lines, "Price")
                Column(Modifier.padding(top = 8.dp)) {
                    SumLine("Subtotal", money(sale.sub))
                    if (sale.discount > 0) SumLine("Discount", "− " + money(sale.discount))
                    SumLine("Total", money(sale.total), big = true)
                    SumLine("Received", money(sale.paid))
                    if (sale.due > 0.004 && !sale.voided) SumLine("Left to pay", money(sale.due), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!sale.voided && sale.due > 0.004) TextButton(onClick = { paying = true }) { Text("Receive payment") }
                if (!sale.voided) TextButton(onClick = { voiding = true }) { Text("Void", color = MaterialTheme.colorScheme.error) }
            }
        },
    )

    if (paying) {
        AmountDialog("Receive payment", "Invoice #${sale.no} · left to pay ${money(sale.due)}", sale.due, { paying = false }) {
            Store.receivePayment(sale.id, it)
            paying = false
            toast(context, "Payment saved.")
        }
    }
    if (voiding) {
        ConfirmDialog("Void invoice #${sale.no}?", "The goods go back into stock and the sale stops counting in reports.", "Void sale", { voiding = false }) {
            Store.voidSale(sale.id)
            voiding = false
            toast(context, "Sale voided.")
        }
    }
}

@Composable
fun PurchaseDialog(purchaseId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val purchase = remember(Store.rev, purchaseId) { Store.purchase(purchaseId) }
    val lines = remember(Store.rev, purchaseId) { Store.purchaseLines(purchaseId) }
    var paying by remember { mutableStateOf(false) }
    if (purchase == null) return

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Purchase #${purchase.no}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("${purchase.supplier}  ·  ${fmtDate(purchase.date)}", color = muted(), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                LineTable(lines, "Cost")
                Column(Modifier.padding(top = 8.dp)) {
                    SumLine("Total", money(purchase.total), big = true)
                    SumLine("Paid", money(purchase.paid))
                    if (purchase.due > 0.004) SumLine("Still owed", money(purchase.due), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = {
            if (purchase.due > 0.004) TextButton(onClick = { paying = true }) { Text("Pay supplier") }
        },
    )

    if (paying) {
        AmountDialog("Pay supplier", "Purchase #${purchase.no} · still owed ${money(purchase.due)}", purchase.due, { paying = false }) {
            Store.paySupplier(purchase.id, it)
            paying = false
            toast(context, "Payment saved.")
        }
    }
}
