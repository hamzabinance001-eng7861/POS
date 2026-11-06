package com.inventry.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Product
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText

/** Tap to open a searchable list of products, each with its photo. Typing a barcode picks it straight away. */
@Composable
fun ProductChooser(products: List<Product>, selected: Product?, onSelect: (Product) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(14.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { q = ""; open = true }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            ProductThumb(selected, 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(selected.name, fontWeight = FontWeight.SemiBold)
                Text("${money(selected.price)}  ·  ${qtyText(selected.stock)} in stock", color = muted(), fontSize = 12.sp)
            }
        } else {
            Text(
                if (products.isEmpty()) "Add a product first" else "Choose a product",
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp),
                color = muted(),
            )
        }
        Text("▾", color = muted(), modifier = Modifier.padding(horizontal = 6.dp))
    }

    if (open) {
        val shown = products.filter {
            q.isBlank() || it.name.contains(q.trim(), true) || it.category.contains(q.trim(), true) || it.barcode.contains(q.trim(), true)
        }
        LaunchedEffect(q) {
            val only = shown.singleOrNull()
            if (q.isNotBlank() && only != null && only.barcode.isNotEmpty() && only.barcode.equals(q.trim(), true)) {
                open = false
                onSelect(only)
            }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Choose a product", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Field("Search name, barcode or category", q, { q = it })
                    Column(Modifier.heightIn(max = 340.dp).padding(top = 8.dp).verticalScroll(rememberScrollState())) {
                        if (shown.isEmpty()) EmptyState("No product matches.")
                        shown.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { open = false; onSelect(p) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProductThumb(p, 40.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        listOf(money(p.price), p.category).filter { it.isNotBlank() }.joinToString("  ·  "),
                                        color = muted(), fontSize = 12.sp,
                                    )
                                }
                                Pill(qtyText(p.stock), if (p.stock <= 0) Red else if (p.stock <= p.low) Amber else Green)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** A text box for a customer or supplier name, with a ▾ to pick one already in the address book. */
@Composable
fun NameField(label: String, value: String, onChange: (String) -> Unit, names: List<String>, placeholder: String = "") {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = if (names.isEmpty()) null else ({
                Text("▾", color = muted(), modifier = Modifier.clickable { open = true }.padding(14.dp))
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.take(40).forEach { n ->
                DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onChange(n) })
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
            Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A rounded tile with a big emoji, used for the shortcuts on Home and the More screen. */
@Composable
fun EmojiTile(emoji: String, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 22.sp)
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Seven (or any number of) small rounded bars, tallest = biggest value. */
@Composable
fun BarChart(data: List<Pair<String, Double>>, bar: Color, label: Color, modifier: Modifier = Modifier) {
    val max = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(
        modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { (name, v) ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((0.07f + 0.7f * (v / max).toFloat()).coerceIn(0.07f, 0.77f))
                        .clip(RoundedCornerShape(7.dp))
                        .background(bar.copy(alpha = if (v > 0) 1f else 0.3f)),
                )
                Text(name.take(3), color = label, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = muted(),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}
