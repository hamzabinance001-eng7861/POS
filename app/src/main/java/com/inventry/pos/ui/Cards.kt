package com.inventry.pos.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Product
import com.inventry.pos.data.Store
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText

/** A rounded white search box with a magnifier, like the design's search bar. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = muted(), fontSize = 14.sp) },
        leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 8.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CircleButton(label: String, onClick: () -> Unit, filled: Boolean = true, size: Dp = 30.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) scheme.primary else scheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) scheme.onPrimary else scheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Shows a single "+" until something is chosen, then "− 2 +". */
@Composable
fun QtyStepper(qty: Double, onMinus: () -> Unit, onPlus: () -> Unit) {
    if (qty <= 0) {
        CircleButton("+", onPlus)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleButton("−", onMinus, filled = false, size = 26.dp)
            Text(qtyText(qty), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            CircleButton("+", onPlus, size = 26.dp)
        }
    }
}

/** The product's photo filling its space, or a soft tile with its first letter. */
@Composable
fun ProductPhoto(p: Product, modifier: Modifier) {
    val bytes = remember(p.id, p.hasImage, Store.rev) { if (p.hasImage) Store.productImage(p.id) else null }
    val bmp = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                p.name.trim().take(1).uppercase().ifEmpty { "?" },
                color = MaterialTheme.colorScheme.primary, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** One tile in the product grid: photo, name, price and the quantity stepper. */
@Composable
fun ProductCard(p: Product, qty: Double, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val out = p.stock <= 0
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (qty > 0) 1.5.dp else 0.dp, if (qty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape)
            .padding(8.dp),
    ) {
        Box(Modifier.alpha(if (out) 0.45f else 1f)) {
            ProductPhoto(p, Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp)))
        }
        Text(
            p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(
            if (out) "Out of stock" else "${qtyText(p.stock)} in stock",
            color = if (out) Red else muted(), fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(money(p.price), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (!out) QtyStepper(qty, onMinus, onPlus)
        }
    }
}
