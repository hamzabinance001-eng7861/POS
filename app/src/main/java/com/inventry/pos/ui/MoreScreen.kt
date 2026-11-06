package com.inventry.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.inventry.pos.R

/** A shortcut: a line icon (built-in [icon], or the drawable [res]), its name, colour and where it goes. */
private class Entry(val icon: ImageVector?, val res: Int, val label: String, val color: Color, val target: String)

private val PEOPLE = listOf(
    Entry(Icons.Filled.Add, 0, "Purchase", Red, "buy"),
    Entry(Icons.Filled.Person, 0, "Customers", Indigo, "customers"),
    Entry(Icons.Filled.AccountBox, 0, "Suppliers", Violet, "suppliers"),
)
private val MONEY = listOf(
    Entry(Icons.Filled.Create, 0, "Expenses", Amber, "expenses"),
    Entry(Icons.Filled.Refresh, 0, "Returns", Red, "returns"),
    Entry(Icons.Filled.DateRange, 0, "Cash book", Green, "reports:5"),
    Entry(null, R.drawable.ic_receipt, "Tax report", Indigo, "reports:7"),
)
private val BUSINESS = listOf(
    Entry(Icons.Filled.Search, 0, "IMEI / Warranty", Indigo, "serial"),
    Entry(Icons.Filled.CheckCircle, 0, "Stock count", Green, "count"),
    Entry(null, R.drawable.ic_truck, "Vehicles", Amber, "vehicles"),
    Entry(Icons.Filled.Info, 0, "Reports", Violet, "reports:0"),
    Entry(Icons.Filled.Star, 0, "Profit", Green, "reports:4"),
    Entry(Icons.Filled.Settings, 0, "Settings", Color(0xFF7C7C96), "settings"),
)

@Composable
private fun Group(title: String, items: List<Entry>, nav: Nav) {
    SectionTitle(title)
    items.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { e ->
                val painter = if (e.icon != null) rememberVectorPainter(e.icon) else painterResource(e.res)
                IconTile(painter, e.label, e.color, { nav.open(e.target) }, Modifier.weight(1f))
            }
            // keep the tiles the same width when a row is short
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun MoreScreen(nav: Nav) {
    OnePane {
        Group("People", PEOPLE, nav)
        Group("Money", MONEY, nav)
        Group("Business", BUSINESS, nav)
    }
}
