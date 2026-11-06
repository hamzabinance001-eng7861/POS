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
import com.inventry.pos.data.adjustments
import com.inventry.pos.data.paymentMethods
import com.inventry.pos.data.serviceSales
import com.inventry.pos.data.cashBook
import com.inventry.pos.data.customersOwing
import com.inventry.pos.data.expensesByCategory
import com.inventry.pos.data.fmtDate
import com.inventry.pos.data.money
import com.inventry.pos.data.profit
import com.inventry.pos.data.qtyText
import com.inventry.pos.data.salesByCategory
import com.inventry.pos.data.salesByCustomer
import com.inventry.pos.data.salesByDay
import com.inventry.pos.data.salesByProduct
import com.inventry.pos.data.suppliersOwed
import com.inventry.pos.data.taxByDay
import com.inventry.pos.data.vehicleSummaries

private val REPORTS = listOf(
    "Sales by day", "By product", "By category", "By customer", "Profit", "Cash book",
    "Expenses", "Tax", "Stock", "Low stock", "Money owed", "Vehicles",
    "Payment methods", "Services", "Stock changes",
)

/** Stock, low stock and money owed are "as of now", so they do not use a date range. */
private fun usesPeriod(kind: Int) = kind !in listOf(8, 9, 10)

@Composable
fun ReportsScreen(initialKind: Int = 0) {
    var kind by rememberSaveable(initialKind) { mutableStateOf(initialKind.coerceIn(0, REPORTS.size - 1)) }
    var periodIndex by rememberSaveable { mutableStateOf(2) }

    OnePane {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REPORTS.forEachIndexed { i, label -> Chip(label, kind == i) { kind = i } }
        }
        if (usesPeriod(kind)) {
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
                        val rows = remember(Store.rev, periodIndex) { Store.salesByCategory(period) }
                        ReportTable(
                            listOf("Category", "Qty sold", "Amount"),
                            rows.map { listOf(it.name, qtyText(it.qty), money(it.amount)) },
                            listOf("Total", qtyText(rows.sumOf { it.qty }), money(rows.sumOf { it.amount })),
                        )
                    }
                    3 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.salesByCustomer(period) }
                        ReportTable(
                            listOf("Customer", "Invoices", "Bought"),
                            rows.map { listOf(it.name, it.count.toString(), money(it.amount)) },
                            listOf("Total", rows.sumOf { it.count }.toString(), money(rows.sumOf { it.amount })),
                        )
                    }
                    4 -> {
                        val s = remember(Store.rev, periodIndex) { Store.profit(period) }
                        SumLine("Sales before discount", money(s.revenue))
                        SumLine("Discounts given", "− " + money(s.discount))
                        SumLine("Cost of goods sold", "− " + money(s.cogs))
                        SumLine("Gross profit", money(s.gross), big = true, color = if (s.gross >= 0) Green else MaterialTheme.colorScheme.error)
                        Column(Modifier.padding(top = 10.dp)) {
                            SumLine("Delivery & extra charges", "+ " + money(s.charges))
                            SumLine("Vehicle earnings", "+ " + money(s.vehicleEarnings))
                            SumLine("Expenses", "− " + money(s.expenses))
                            SumLine("Vehicle costs", "− " + money(s.vehicleCosts))
                            SumLine(
                                "Net profit", money(s.netProfit), big = true,
                                color = if (s.netProfit >= 0) Green else MaterialTheme.colorScheme.error,
                            )
                        }
                        Column(Modifier.padding(top = 10.dp)) {
                            SumLine("Margin on goods", "${Math.round(s.margin * 10) / 10.0}%")
                            SumLine("Tax collected", money(s.tax))
                            SumLine("Bought in this period", money(s.bought))
                        }
                    }
                    5 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.cashBook(period) }
                        val moneyIn = rows.filter { it.amount > 0 }.sumOf { it.amount }
                        val moneyOut = rows.filter { it.amount < 0 }.sumOf { -it.amount }
                        SumLine("Money in", money(moneyIn), color = Green)
                        SumLine("Money out", money(moneyOut))
                        SumLine("Net cash", money(moneyIn - moneyOut), big = true, color = if (moneyIn >= moneyOut) Green else MaterialTheme.colorScheme.error)
                        Text("Every movement", fontSize = 13.sp, color = muted(), modifier = Modifier.padding(top = 12.dp))
                        ReportTable(
                            listOf("What", "Amount"),
                            rows.map { listOf(fmtDate(it.date) + " · " + it.label, (if (it.amount >= 0) "+ " else "− ") + money(kotlin.math.abs(it.amount))) },
                        )
                    }
                    6 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.expensesByCategory(period) }
                        ReportTable(
                            listOf("Category", "Entries", "Spent"),
                            rows.map { listOf(it.name, it.count.toString(), money(it.amount)) },
                            listOf("Total", rows.sumOf { it.count }.toString(), money(rows.sumOf { it.amount })),
                        )
                    }
                    7 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.taxByDay(period) }
                        ReportTable(
                            listOf("Day", "Invoices", "Taxable", "Tax"),
                            rows.map { listOf(it.day, it.count.toString(), money(it.received), money(it.total)) },
                            listOf("Total", rows.sumOf { it.count }.toString(), money(rows.sumOf { it.received }), money(rows.sumOf { it.total })),
                        )
                    }
                    8 -> {
                        val rows = remember(Store.rev) { Store.products() }
                        ReportTable(
                            listOf("Product", "Qty", "Cost", "Value"),
                            rows.map { listOf(it.name, qtyText(it.stock), money(it.cost), money(it.stock * it.cost)) },
                            listOf("Total", "", "", money(rows.sumOf { it.stock * it.cost })),
                        )
                    }
                    9 -> {
                        val rows = remember(Store.rev) { Store.products().filter { it.stock <= it.low }.sortedBy { it.stock } }
                        ReportTable(
                            listOf("Product", "In stock", "Alert at"),
                            rows.map { listOf(it.name, qtyText(it.stock), qtyText(it.low)) },
                        )
                    }
                    10 -> {
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
                    12 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.paymentMethods(period) }
                        ReportTable(
                            listOf("Method", "In", "Out"),
                            rows.map { listOf(it.method, money(it.received), money(it.paidOut)) },
                            listOf("Total", money(rows.sumOf { it.received }), money(rows.sumOf { it.paidOut })),
                        )
                    }
                    13 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.serviceSales(period) }
                        ReportTable(
                            listOf("Service / custom item", "Qty", "Earned"),
                            rows.map { listOf(it.name, qtyText(it.qty), money(it.amount)) },
                            listOf("Total", qtyText(rows.sumOf { it.qty }), money(rows.sumOf { it.amount })),
                        )
                    }
                    14 -> {
                        val rows = remember(Store.rev, periodIndex) { Store.adjustments(period) }
                        ReportTable(
                            listOf("When / product", "Change", "Reason"),
                            rows.map { listOf(fmtDate(it.date) + " · " + it.product, (if (it.delta > 0) "+" else "") + qtyText(it.delta), it.reason.ifBlank { "—" }) },
                        )
                    }
                    else -> {
                        val rows = remember(Store.rev, periodIndex) { Store.vehicleSummaries(period) }
                        ReportTable(
                            listOf("Vehicle", "Trips", "Income", "Costs", "Net"),
                            rows.map { listOf(it.vehicle.name, it.deliveries.toString(), money(it.income), money(it.costs), money(it.net)) },
                            listOf("Total", rows.sumOf { it.deliveries }.toString(), money(rows.sumOf { it.income }), money(rows.sumOf { it.costs }), money(rows.sumOf { it.net })),
                        )
                    }
                }
            }
        }
    }
}
