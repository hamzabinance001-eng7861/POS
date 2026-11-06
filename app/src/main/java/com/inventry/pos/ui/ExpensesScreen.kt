package com.inventry.pos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.inventry.pos.data.EXPENSE_CATEGORIES
import com.inventry.pos.data.Expense
import com.inventry.pos.data.Periods
import com.inventry.pos.data.Store
import com.inventry.pos.data.addExpense
import com.inventry.pos.data.deleteExpense
import com.inventry.pos.data.expenses
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money

@Composable
fun ExpensesScreen() {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var periodIndex by rememberSaveable { mutableStateOf(3) }
    var deleting by remember { mutableStateOf<Expense?>(null) }

    val periods = remember(Store.rev) { Periods.all() }
    val period = periods[periodIndex.coerceIn(0, periods.size - 1)]
    val list = remember(Store.rev, periodIndex) { Store.expenses(period) }

    TwoPane(
        left = {
            AppCard {
                Heading("Add an expense")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EXPENSE_CATEGORIES.forEach { c -> Chip(c, category == c) { category = c } }
                    }
                    Field("Category", category, { category = it }, placeholder = "Rent, Electricity…")
                    NumField("Amount", amount, { amount = it }, Modifier.fillMaxWidth())
                    Field("Note (optional)", note, { note = it })
                    BigButton("Save expense", onClick = {
                        val err = Store.addExpense(category, amount.toDoubleOrNull() ?: 0.0, note)
                        if (err != null) toast(context, err) else { toast(context, "Expense saved."); amount = ""; note = "" }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        right = {
            AppCard {
                Heading("Expenses")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    periods.forEachIndexed { i, p -> Chip(p.label, periodIndex == i) { periodIndex = i } }
                }
                SumLine("Total spent", money(list.sumOf { it.amount }), big = true)
                if (list.isEmpty()) EmptyState("No expenses in this period.")
                list.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.category, fontWeight = FontWeight.SemiBold)
                            Text(listOf(fmtDate(e.date), e.note).filter { it.isNotBlank() }.joinToString(" · "), color = muted(), fontSize = 12.sp)
                        }
                        Text(money(e.amount), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { deleting = e }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        },
    )

    deleting?.let { e ->
        ConfirmDialog("Delete this expense?", "${e.category} · ${money(e.amount)}", "Delete", { deleting = null }) {
            Store.deleteExpense(e.id)
            deleting = null
            toast(context, "Deleted.")
        }
    }
}
