package com.inventry.pos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.Product
import com.inventry.pos.data.Store
import java.io.ByteArrayOutputStream

/** Reads a picture the user chose and shrinks it to a small JPEG (longest side [maxSide] pixels). */
fun compressImage(context: Context, uri: Uri, maxSide: Int = 480): ByteArray? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bmp
        }
        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 82, bytes)
        bytes.toByteArray()
    } catch (e: Exception) {
        null
    }
}

/** Returns a function that opens the phone's photo picker; [onPicked] gets the shrunken picture. */
@Composable
fun rememberImagePicker(maxSide: Int = 480, onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = compressImage(context, uri, maxSide)
            if (bytes != null) current(bytes) else toast(context, "Could not read that picture.")
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
fun BytesImage(bytes: ByteArray, size: Dp, corner: Dp = 14.dp, circle: Boolean = false) {
    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(if (circle) CircleShape else RoundedCornerShape(corner)),
        )
    } else {
        Avatar("?", size, circle)
    }
}

private val AvatarColors = listOf(
    Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF0EA5E9), Color(0xFF16A34A),
    Color(0xFFF97316), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFD97706),
)

/** A coloured tile with the first letter of the name. The colour is the same every time for the same name. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, circle: Boolean = true) {
    val color = AvatarColors[(name.lowercase().hashCode() and 0x7fffffff) % AvatarColors.size]
    Box(
        Modifier
            .size(size)
            .clip(if (circle) CircleShape else RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** The product's photo, or a coloured initial when it has none. */
@Composable
fun ProductThumb(p: Product, size: Dp = 48.dp) {
    val bytes = remember(p.id, p.hasImage, Store.rev) { if (p.hasImage) Store.productImage(p.id) else null }
    if (bytes != null) BytesImage(bytes, size) else Avatar(p.name, size, circle = false)
}
