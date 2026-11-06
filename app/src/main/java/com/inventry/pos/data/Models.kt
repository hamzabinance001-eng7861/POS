package com.inventry.pos.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The one fixed account. There is no sign-up and no server. */
object Auth {
    const val USERNAME = "admin"
    const val PASSWORD = "Start12345@"
}

data class Product(
    val id: Long,
    val name: String,
    val price: Double,
    val cost: Double,
    val stock: Double,
    val low: Double,
)

/** One line being typed into a sale or a purchase (the price is the sell price or the unit cost). */
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
    val total: Double,
    val paid: Double,
    val voided: Boolean,
) {
    val due: Double get() = total - paid
}

data class Purchase(
    val id: Long,
    val no: Int,
    val date: Long,
    val supplier: String,
    val total: Double,
    val paid: Double,
) {
    val due: Double get() = total - paid
}

data class Outcome(val id: Long = 0, val error: String? = null)

data class DayRow(val day: String, val count: Int, val total: Double, val received: Double)
data class ProductSalesRow(val name: String, val qty: Double, val amount: Double)
data class OwedRow(val name: String, val amount: Double)
data class ProfitSummary(val revenue: Double, val discount: Double, val cogs: Double, val bought: Double) {
    val net: Double get() = revenue - discount
    val gross: Double get() = net - cogs
    val margin: Double get() = if (net > 0) gross / net * 100 else 0.0
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

    /** Built fresh each call so "today" is always today. */
    fun all(): List<Period> = listOf(
        today(),
        Period("7 days", startOfDay(-6), startOfDay(1)),
        Period("30 days", startOfDay(-29), startOfDay(1)),
        Period("This month", startOfMonth(), startOfDay(1)),
        Period("All time", 0L, Long.MAX_VALUE),
    )
}

/** Things the owner can change in Settings. Kept in memory like everything else. */
object Settings {
    var shop by mutableStateOf("My Shop")
    var currency by mutableStateOf("Rs")
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
