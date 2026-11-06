package com.inventry.pos.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Product
import com.inventry.pos.data.money
import com.inventry.pos.data.plain
import com.inventry.pos.data.qtyText

fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

@Composable
fun muted(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d*\.?\d*"""))) onChange(v) },
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tonal: Boolean = false, danger: Boolean = false) {
    val container = when {
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        tonal -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when {
        danger -> MaterialTheme.colorScheme.error
        tonal -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** A small rounded label such as "Paid" or "3 in stock". */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SumLine(label: String, value: String, big: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (big) MaterialTheme.colorScheme.onSurface else muted(), fontSize = if (big) 17.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
        Text(value, color = color, fontSize = if (big) 17.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = muted(), textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

/** A name and a small grey line under it on the left, an amount and an optional pill on the right. */
@Composable
fun ListRow(title: String, subtitle: String, amount: String, pill: Pair<String, Color>?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = muted(), fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (pill != null) Pill(pill.first, pill.second)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
fun ProductPicker(products: List<Product>, selected: Product?, onSelect: (Product) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                selected?.let { "${it.name}  ·  ${qtyText(it.stock)} in stock" } ?: if (products.isEmpty()) "Add a product first" else "Choose a product",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("▾", color = muted())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            products.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.name}  ·  ${qtyText(p.stock)} in stock") },
                    onClick = { open = false; onSelect(p) },
                )
            }
        }
    }
}

@Composable
fun AmountDialog(title: String, note: String, initial: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(plain(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(note, color = muted(), fontSize = 13.sp)
                Box(Modifier.padding(top = 12.dp)) { NumField("Amount", text, { text = it }, Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { val v = text.toDoubleOrNull() ?: 0.0; if (v > 0) onConfirm(v) }) { Text("Save payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rows and columns for the reports. The first column is text; the rest are numbers. */
@Composable
fun ReportTable(headers: List<String>, rows: List<List<String>>, footer: List<String>? = null) {
    if (rows.isEmpty()) {
        EmptyState("Nothing to show for this report yet.")
        return
    }
    Column {
        TableLine(headers, header = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        rows.forEach {
            TableLine(it)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
        if (footer != null) TableLine(footer, bold = true)
    }
}

@Composable
private fun TableLine(cells: List<String>, header: Boolean = false, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                modifier = Modifier.weight(if (i == 0) 2f else 1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                fontSize = if (header) 11.sp else 13.sp,
                fontWeight = if (header || bold) FontWeight.Bold else FontWeight.Normal,
                color = if (header) muted() else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
