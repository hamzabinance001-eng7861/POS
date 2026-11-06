package com.inventry.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private class Entry(val emoji: String, val label: String, val color: Color, val target: String)

private val PEOPLE = listOf(
    Entry("📦", "Purchase", Color(0xFFE5484D), "buy"),
    Entry("👥", "Customers", Color(0xFF0EA5E9), "customers"),
    Entry("🏭", "Suppliers", Color(0xFF7C3AED), "suppliers"),
)
private val MONEY = listOf(
    Entry("💸", "Expenses", Amber, "expenses"),
    Entry("↩️", "Returns", Red, "returns"),
    Entry("📒", "Cash book", Green, "reports:5"),
    Entry("🧾", "Tax report", Color(0xFF64748B), "reports:7"),
)
private val BUSINESS = listOf(
    Entry("🚚", "Vehicles", Color(0xFF14B8A6), "vehicles"),
    Entry("📊", "Reports", Color(0xFFEC4899), "reports:0"),
    Entry("📈", "Profit", Indigo, "reports:4"),
    Entry("⚙️", "Settings", Color(0xFF64748B), "settings"),
)

@Composable
private fun Group(title: String, items: List<Entry>, nav: Nav) {
    SectionTitle(title)
    items.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { e -> EmojiTile(e.emoji, e.label, e.color, { nav.open(e.target) }, Modifier.weight(1f)) }
            // keep the tiles the same width when a row is short
            repeat(4 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
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
