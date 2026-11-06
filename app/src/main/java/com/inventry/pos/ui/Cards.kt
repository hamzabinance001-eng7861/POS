package com.inventry.pos.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
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
        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = null, tint = muted(), modifier = Modifier.padding(start = 6.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CircleButton(label: String, onClick: () -> Unit, filled: Boolean = true, size: Dp = 30.dp) {
    val scheme = MaterialTheme.colorScheme
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(src)
            .size(size)
            .clip(CircleShape)
            .background(if (filled) scheme.primary else scheme.primaryContainer)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) scheme.onPrimary else scheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Shows a single "+" until something is chosen, then "− 2 +". The change slides and springs. */
@Composable
fun QtyStepper(qty: Double, onMinus: () -> Unit, onPlus: () -> Unit) {
    AnimatedContent(
        targetState = qty > 0,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(100)) + scaleOut(targetScale = 0.6f))
        },
        label = "stepper",
    ) { chosen ->
        if (!chosen) {
            CircleButton("+", onPlus)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleButton("−", onMinus, filled = false, size = 26.dp)
                AnimatedContent(
                    targetState = qty,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                        } else {
                            (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                        }
                    },
                    label = "qty",
                ) { q -> Text(qtyText(q), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                CircleButton("+", onPlus, size = 26.dp)
            }
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

/**
 * One product in the sale list: photo, name, price and a round stepper. Once something is chosen the
 * row turns soft violet with a violet outline, so the cart is easy to see at a glance.
 */
@Composable
fun ProductRow(p: Product, qty: Double, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val chosen = qty > 0
    val rowBg by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, tween(240), label = "rowBg")
    val rowEdge by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, tween(240), label = "rowEdge")
    val out = p.stock <= 0
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowBg)
            .border(1.5.dp, rowEdge, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (out) 0.45f else 1f)) {
            ProductPhoto(p, Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(money(p.price), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (out) "Out of stock" else "${qtyText(p.stock)} in stock",
                color = if (out) Red else muted(), fontSize = 11.sp,
            )
        }
        if (!out) QtyStepper(qty, onMinus, onPlus)
    }
}

/** A saved service or a line typed in at the till, with its stepper. */
@Composable
fun ServiceRow(emoji: String, name: String, price: Double, qty: Double, note: String, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(20.dp)
    val chosen = qty > 0
    val rowBg by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, tween(240), label = "rowBg")
    val rowEdge by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, tween(240), label = "rowEdge")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowBg)
            .border(1.5.dp, rowEdge, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(money(price), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (note.isNotBlank()) Text(note, color = muted(), fontSize = 11.sp)
        }
        if (onEdit != null) {
            Text("Edit", color = muted(), fontSize = 12.sp, modifier = Modifier.clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 6.dp))
        }
        QtyStepper(qty, onMinus, onPlus)
    }
}

/** A round violet-tinted button with a line icon, e.g. the camera next to a search box. */
@Composable
fun IconCircle(icon: androidx.compose.ui.graphics.painter.Painter, onClick: () -> Unit, size: Dp = 46.dp) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(src)
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}
