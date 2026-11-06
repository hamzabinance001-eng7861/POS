package com.inventry.pos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Periods
import com.inventry.pos.data.Store
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText

private val REPORTS = listOf("Sales by day", "Sales by product", "Profit", "Stock", "Low stock", "Money owed")

@Composable
fun ReportsScreen() {
    var kind by rememberSaveable { mutableStateOf(0) }
    var periodIndex by rememberSaveable { mutableStateOf(2) }
    val usesPeriod = kind <= 2

    OnePane {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REPORTS.forEachIndexed { i, label -> Chip(label, kind == i) { kind = i } }
        }
        if (usesPeriod) {
            val periods = remember(Store.rev) { Periods.all() }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEachIndexed { i, p -> Chip(p.label, periodIndex == i) { periodIndex = i } }
            }
        }
        AppCard {
            Heading(REPORTS[kind])
            Column(Modifier.padding(top = 8.dp)) {
                val period = remember(Store.rev, periodIndex) { Periods.all()[periodIndex.coerceIn(0, 4)] }
                when (kind) {
                    0 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.salesByDay(period) }
                        ReportTable(
                            listOf("Day", "Invoices", "Sales", "Received"),
                            rows.map { listOf(it.day, it.count.toString(), money(it.total), money(it.received)) },
                            listOf("Total", rows.sumOf { it.count }.toString(), money(rows.sumOf { it.total }), money(rows.sumOf { it.received })),
                        )
                    }
                    1 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.salesByProduct(period) }
                        ReportTable(
                            listOf("Product", "Qty sold", "Amount"),
                            rows.map { listOf(it.name, qtyText(it.qty), money(it.amount)) },
                            listOf("Total", qtyText(rows.sumOf { it.qty }), money(rows.sumOf { it.amount })),
                        )
                    }
                    2 -> {
                        val s = remember(Store.rev, periodIndex) { Store.profit(period) }
                        SumLine("Sales before discount", money(s.revenue))
                        SumLine("Discounts given", "− " + money(s.discount))
                        SumLine("Cost of goods sold", "− " + money(s.cogs))
                        SumLine(
                            "Gross profit", money(s.gross), big = true,
                            color = if (s.gross >= 0) Green else MaterialTheme.colorScheme.error,
                        )
                        Column(Modifier.padding(top = 10.dp)) {
                            SumLine("Margin", "${Math.round(s.margin * 10) / 10.0}%")
                            SumLine("Bought in this period", money(s.bought))
                        }
                    }
                    3 -> {
                        val rows = remember(Store.rev) { Store.products() }
                        ReportTable(
                            listOf("Product", "Qty", "Cost", "Value"),
                            rows.map { listOf(it.name, qtyText(it.stock), money(it.cost), money(it.stock * it.cost)) },
                            listOf("Total", "", "", money(rows.sumOf { it.stock * it.cost })),
                        )
                    }
                    4 -> {
                        val rows = remember(Store.rev) { Store.products().filter { it.stock <= it.low }.sortedBy { it.stock } }
                        ReportTable(
                            listOf("Product", "In stock", "Alert at"),
                            rows.map { listOf(it.name, qtyText(it.stock), qtyText(it.low)) },
                        )
                    }
                    else -> {
                        val customers = remember(Store.rev) { Store.customersOwing() }
                        val suppliers = remember(Store.rev) { Store.suppliersOwed() }
                        Text("Customers who owe you", fontSize = 13.sp, color = muted())
                        ReportTable(
                            listOf("Customer", "Owes"),
                            customers.map { listOf(it.name, money(it.amount)) },
                            listOf("Total", money(customers.sumOf { it.amount })),
                        )
                        Text("You owe suppliers", fontSize = 13.sp, color = muted(), modifier = Modifier.padding(top = 16.dp))
                        ReportTable(
                            listOf("Supplier", "You owe"),
                            suppliers.map { listOf(it.name, money(it.amount)) },
                            listOf("Total", money(suppliers.sumOf { it.amount })),
                        )
                    }
                }
            }
        }
    }
}
