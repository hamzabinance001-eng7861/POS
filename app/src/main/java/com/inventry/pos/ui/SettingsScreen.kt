package com.inventry.pos.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.inventry.pos.data.Settings
import com.inventry.pos.data.Store
import com.inventry.pos.data.backupJson
import com.inventry.pos.data.plain
import com.inventry.pos.data.restoreJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var taxText by remember { mutableStateOf(if (Settings.taxRate > 0) plain(Settings.taxRate) else "") }
    var confirmRestore by remember { mutableStateOf(false) }

    val pickLogo = rememberImagePicker(maxSide = 320) { Settings.logo = it }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            val err = if (text == null) "Could not open that file." else Store.restoreJson(text)
            toast(context, err ?: "Backup restored.")
            if (err == null) taxText = if (Settings.taxRate > 0) plain(Settings.taxRate) else ""
        }
    }

    OnePane {
        AppCard {
            Heading("Shop")
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val logo = Settings.logo
                    if (logo != null) BytesImage(logo, 72.dp, circle = true) else Avatar(Settings.shop, 72.dp)
                    Column {
                        BigButton(if (logo == null) "Upload logo" else "Change logo", onClick = pickLogo, tonal = true)
                        if (logo != null) {
                            TextButton(onClick = { Settings.logo = null }) {
                                Text("Remove logo", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Field("Shop name", Settings.shop, { Settings.shop = it })
                Field("Currency shown before amounts", Settings.currency, { Settings.currency = it.take(6) }, placeholder = "Rs, \$, AED …")
            }
        }

        AppCard {
            Heading("Selling")
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField(
                    "Default tax %", taxText, { taxText = it; Settings.taxRate = it.toDoubleOrNull() ?: 0.0 },
                    Modifier.fillMaxWidth(), placeholder = "0 for no tax",
                )
                Field("Name of the extra charge", Settings.chargeLabel, { Settings.chargeLabel = it.take(40) }, placeholder = "Delivery charge")
                Text(
                    "The tax is filled in on every new sale and can be changed there. The extra charge name is printed on the receipt " +
                        "when a charge is added (Delivery charge, Shipping, Freight, Fuel charge…).",
                    color = muted(), fontSize = 12.sp,
                )
            }
        }

        AppCard {
            Heading("Backup")
            Text(
                "The data lives only in the phone’s memory, so it is cleared when Android closes the app. " +
                    "Make a backup file before you close it, and restore it next time.",
                color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Back up now", onClick = {
                    try {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        val file = File(context.cacheDir, "pos-backup-$stamp.json")
                        file.writeText(Store.backupJson())
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Save the backup"))
                    } catch (e: Exception) {
                        toast(context, "Could not make the backup.")
                    }
                }, modifier = Modifier.weight(1f))
                BigButton("Restore", onClick = { confirmRestore = true }, modifier = Modifier.weight(1f), tonal = true)
            }
        }

        AppCard {
            Heading("About")
            Text(
                "Inventry POS · version 1.0\nSigned in on this phone — you will not be asked again.",
                color = muted(), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            "Restore a backup?", "Everything in the app now is replaced by the contents of the backup file.", "Choose file",
            { confirmRestore = false },
        ) {
            confirmRestore = false
            restoreLauncher.launch(arrayOf("*/*"))
        }
    }
}
