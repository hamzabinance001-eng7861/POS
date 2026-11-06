package com.inventry.pos.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Opens the phone's share sheet (WhatsApp, SMS, e-mail…) with some text. */
fun shareText(context: Context, text: String, title: String = "Share") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/** Opens a WhatsApp chat with [phone] and the message ready. Falls back to the share sheet with no number. */
fun whatsApp(context: Context, phone: String, text: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.isEmpty()) {
        shareText(context, text)
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text))))
    } catch (e: ActivityNotFoundException) {
        shareText(context, text)
    }
}

fun dial(context: Context, phone: String) {
    if (phone.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim())))
    } catch (e: ActivityNotFoundException) {
        toast(context, "No phone app found.")
    }
}

/** A dropdown that picks one of [options] (id and label). Id 0 conventionally means "none". */
@Composable
fun ChoiceField(label: String, options: List<Pair<Long, String>>, selected: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(label, fontSize = 11.sp, color = muted())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "—", modifier = Modifier.weight(1f).padding(vertical = 2.dp))
                Text("▾", color = muted())
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(id) })
            }
        }
    }
}

private var printView: android.webkit.WebView? = null

/** Opens Android's print screen for an HTML page: print on a nearby printer or choose "Save as PDF". */
fun printHtml(context: Context, html: String, jobName: String) {
    val web = android.webkit.WebView(context)
    web.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView, url: String?) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            manager.print(jobName, view.createPrintDocumentAdapter(jobName), android.print.PrintAttributes.Builder().build())
        }
    }
    printView = web // keep it alive until the print screen has taken the page
    web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}
