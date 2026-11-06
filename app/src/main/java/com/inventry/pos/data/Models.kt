package com.inventry.pos.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The one fixed account. It is asked for once, on the very first launch. */
object Auth {
    const val USERNAME = "admin"
    const val PASSWORD = "Start12345@"
}

/** Things the owner can change in Settings. */
object Settings {
    private var prefs: SharedPreferences? = null

    private var shopState by mutableStateOf("Khan Mobile")
    private var currencyState by mutableStateOf("Rs")
    private var taxState by mutableStateOf(0.0)
    private var chargeState by mutableStateOf("Delivery charge")
    private var logoState by mutableStateOf<ByteArray?>(null)

    /** Reads what was saved last time. Called once when the app starts. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        shopState = p.getString("shop", shopState) ?: shopState
        currencyState = p.getString("currency", currencyState) ?: currencyState
        taxState = (p.getString("tax", null)?.toDoubleOrNull()) ?: taxState
        chargeState = p.getString("charge", chargeState) ?: chargeState
        val logo = p.getString("logo", null)
        logoState = if (logo.isNullOrEmpty()) null else Base64.decode(logo, Base64.NO_WRAP)
    }

    private fun save(key: String, value: String?) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    // Every change is written to the phone straight away, so it is still there next time.
    var shop: String
        get() = shopState
        set(v) { shopState = v; save("shop", v) }
    var currency: String
        get() = currencyState
        set(v) { currencyState = v; save("currency", v) }
    var taxRate: Double
        get() = taxState
        set(v) { taxState = v; save("tax", v.toString()) }
    var chargeLabel: String
        get() = chargeState
        set(v) { chargeState = v; save("charge", v) }

    /** The shop logo as a small JPEG, shown on the home screen. */
    var logo: ByteArray?
        get() = logoState
        set(v) { logoState = v; save("logo", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }) }
}

data class Product(
    val id: Long,
    val name: String,
    val price: Double,
    val cost: Double,
    val stock: Double,
    val low: Double,
    val category: String = "",
    val barcode: String = "",
    val unit: String = "",
    val hasImage: Boolean = false,
)

/** One line of a sale, purchase or return. For a purchase [price] is the unit cost. */
data class CartLine(
    val productId: Long,
    val name: String,
    val qty: Double,
    val price: Double,
    val cost: Double,
) {
    val amount: Double get() = qty * price
}

data class Sale(
    val id: Long,
    val no: Int,
    val date: Long,
    val customer: String,
    val sub: Double,
    val discount: Double,
    val taxRate: Double,
    val tax: Double,
    val chargeLabel: String,
    val charge: Double,
    val total: Double,
    val paid: Double,
    val returned: Double,
    val vehicleId: Long,
    val voided: Boolean,
) {
    /** What the customer still owes, after any goods they sent back. */
    val due: Double get() = (total - returned - paid).coerceAtLeast(0.0)
}

data class Purchase(
    val id: Long,
    val no: Int,
    val date: Long,
    val supplier: String,
    val total: Double,
    val paid: Double,
) {
    val due: Double get() = (total - paid).coerceAtLeast(0.0)
}

/** A customer or a supplier. [balance] is what they owe you, or what you owe them. */
data class Party(val id: Long, val name: String, val phone: String, val address: String, val balance: Double)

data class Expense(val id: Long, val date: Long, val category: String, val amount: Double, val note: String)

data class ReturnRow(val id: Long, val date: Long, val saleNo: Int, val customer: String, val amount: Double, val refund: Double)

data class Vehicle(val id: Long, val name: String, val reg: String, val active: Boolean)

data class VehicleEntry(val id: Long, val vehicleId: Long, val kind: Int, val date: Long, val amount: Double, val litres: Double, val note: String)

data class VehicleSummary(
    val vehicle: Vehicle,
    val deliveries: Int,
    val charges: Double,
    val earnings: Double,
    val fuel: Double,
    val maintenance: Double,
    val salary: Double,
    val other: Double,
) {
    val income: Double get() = charges + earnings
    val costs: Double get() = fuel + maintenance + salary + other
    val net: Double get() = income - costs
}

data class Outcome(val id: Long = 0, val error: String? = null)

data class DayRow(val day: String, val count: Int, val total: Double, val received: Double)
data class ProductSalesRow(val name: String, val qty: Double, val amount: Double)
data class NamedAmount(val name: String, val count: Int, val amount: Double)
data class OwedRow(val name: String, val amount: Double)
data class CashRow(val date: Long, val label: String, val amount: Double)

data class ProfitSummary(
    val revenue: Double,
    val discount: Double,
    val cogs: Double,
    val bought: Double,
    val charges: Double,
    val tax: Double,
    val expenses: Double,
    val vehicleCosts: Double,
    val vehicleEarnings: Double,
) {
    val net: Double get() = revenue - discount
    val gross: Double get() = net - cogs
    val margin: Double get() = if (net > 0) gross / net * 100 else 0.0
    val netProfit: Double get() = gross + charges + vehicleEarnings - expenses - vehicleCosts
}

object EntryKind {
    const val FUEL = 1
    const val MAINTENANCE = 2
    const val SALARY = 3
    const val OTHER = 4
    const val EARNING = 5
    val all = listOf(
        FUEL to "Fuel", MAINTENANCE to "Maintenance", SALARY to "Driver salary", OTHER to "Other cost", EARNING to "Earning",
    )

    fun name(kind: Int): String = all.firstOrNull { it.first == kind }?.second ?: "Entry"
}

/** A date range for the reports. [to] is exclusive. */
data class Period(val label: String, val from: Long, val to: Long)

object Periods {
    private fun startOfDay(offsetDays: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.timeInMillis
    }

    private fun startOfMonth(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun today(): Period = Period("Today", startOfDay(0), startOfDay(1))

    /** The last [days] days including today, ending tomorrow 00:00. */
    fun lastDays(days: Int): Period = Period("$days days", startOfDay(-(days - 1)), startOfDay(1))

    /** Built fresh each call so "today" is always today. */
    fun all(): List<Period> = listOf(
        today(),
        lastDays(7),
        lastDays(30),
        Period("This month", startOfMonth(), startOfDay(1)),
        Period("All time", 0L, Long.MAX_VALUE),
    )
}

private val moneyFormat = DecimalFormat("#,##0.##")

/** An amount with the shop's currency in front, e.g. "Rs 1,500". */
fun money(v: Double): String {
    val text = moneyFormat.format(if (kotlin.math.abs(v) < 0.005) 0.0 else v)
    return if (Settings.currency.isBlank()) text else "${Settings.currency.trim()} $text"
}

/** A quantity: the same number format, with no currency. */
fun qtyText(v: Double): String = moneyFormat.format(v)

/** A number as it should appear inside a text box: no trailing ".0". */
fun plain(v: Double): String = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e12) v.toLong().toString() else v.toString()

fun fmtDate(ms: Long): String = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ms))
fun fmtDay(ms: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
