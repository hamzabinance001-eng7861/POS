package com.inventry.pos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventry.pos.data.ImageSearch
import com.inventry.pos.data.Product
import com.inventry.pos.data.Store
import com.inventry.pos.data.money
import com.inventry.pos.data.qtyText
import com.inventry.pos.data.searchByImage

/** Take or choose a photo and see which products look the most like it. */
@Composable
fun ImageSearchDialog(onPick: (Product) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<Pair<Product, Double>>?>(null) }

    fun search(bitmap: Bitmap) {
        results = Store.searchByImage(ImageSearch.signature(bitmap))
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) search(bitmap)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = compressImage(context, uri, 320)
            val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bitmap != null) search(bitmap) else toast(context, "Could not read that picture.")
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Search by photo", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Take or choose a photo of the item. The app compares it with the photos you saved on your products " +
                        "and shows the closest matches. Best in similar light, with the item in the middle.",
                    color = muted(), fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Take photo", onClick = { camera.launch(null) }, modifier = Modifier.weight(1f), tonal = true)
                    BigButton("Choose photo", onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), tonal = true)
                }
                val list = results
                if (list != null) {
                    if (list.isEmpty()) EmptyState("No products have photos yet.\nAdd photos in Items first.")
                    list.forEachIndexed { i, (p, score) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(p); onClose() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProductThumb(p, 48.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${money(p.price)} · ${qtyText(p.stock)} in stock", color = muted(), fontSize = 12.sp)
                            }
                            val pct = (score * 100).toInt()
                            Pill("$pct% match", if (pct >= 70) Green else if (pct >= 45) Amber else Red)
                        }
                        if (i < list.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
