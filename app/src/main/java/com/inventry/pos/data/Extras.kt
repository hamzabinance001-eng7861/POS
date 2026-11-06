package com.inventry.pos.data

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/* =============================================================== customers & suppliers */

private fun partySql(kind: String): String =
    if (kind == "suppliers") {
        "SELECT c.id, c.name, c.phone, c.address, " +
            "(SELECT COALESCE(SUM(MAX(p.total - p.returned - p.paid, 0)), 0) FROM purchases p WHERE lower(p.supplier) = lower(c.name)) FROM suppliers c"
    } else {
        "SELECT c.id, c.name, c.phone, c.address, " +
            "(SELECT COALESCE(SUM(MAX(s.total - s.returned - s.paid, 0)), 0) FROM sales s WHERE s.voided = 0 AND lower(s.customer) = lower(c.name)) FROM customers c"
    }

private fun partyTable(kind: String) = if (kind == "suppliers") "suppliers" else "customers"

/** [kind] is "customers" or "suppliers". */
fun Store.parties(kind: String, search: String = ""): List<Party> =
    query(
        partySql(kind) + " WHERE c.name LIKE ? OR c.phone LIKE ? ORDER BY c.name COLLATE NOCASE",
        "%$search%", "%$search%",
    ) { Party(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getDouble(4)) }

fun Store.saveParty(kind: String, id: Long?, name: String, phone: String, address: String): String? {
    val table = partyTable(kind)
    val clean = name.trim()
    if (clean.isEmpty()) return "Enter a name."
    if (scalar("SELECT COUNT(*) FROM $table WHERE lower(name) = lower(?) AND id <> ?", clean, (id ?: 0L).toString()) > 0) {
        return "That name is already in the list."
    }
    val cv = ContentValues().apply { put("name", clean); put("phone", phone.trim()); put("address", address.trim()) }
    if (id == null) {
        db.insert(table, null, cv)
    } else {
        val old = query("SELECT name FROM $table WHERE id = ?", id.toString()) { it.getString(0) }.firstOrNull()
        db.update(table, cv, "id = ?", arrayOf(id.toString()))
        if (old != null && !old.equals(clean, ignoreCase = true)) {
            if (kind == "suppliers") {
                db.execSQL("UPDATE purchases SET supplier = ? WHERE lower(supplier) = lower(?)", arrayOf<Any>(clean, old))
            } else {
                db.execSQL("UPDATE sales SET customer = ? WHERE lower(customer) = lower(?)", arrayOf<Any>(clean, old))
            }
        }
    }
    bump()
    return null
}

fun Store.deleteParty(kind: String, party: Party): String? {
    val used = if (kind == "suppliers") {
        scalar("SELECT COUNT(*) FROM purchases WHERE lower(supplier) = lower(?)", party.name)
    } else {
        scalar("SELECT COUNT(*) FROM sales WHERE lower(customer) = lower(?)", party.name)
    }
    if (used > 0) return "They have invoices, so they cannot be deleted."
    db.delete(partyTable(kind), "id = ?", arrayOf(party.id.toString()))
    bump()
    return null
}

/** Takes a payment from a customer (or pays a supplier) and spreads it over the oldest unpaid invoices first. */
fun Store.settleParty(kind: String, name: String, amount: Double, method: String = "Cash") {
    var left = amount
    if (kind == "suppliers") {
        for (p in purchasesOf(name).reversed()) {
            if (left <= 0.004) break
            val take = minOf(left, p.due)
            if (take > 0.004) { paySupplier(p.id, take, method); left -= take }
        }
    } else {
        for (s in salesOf(name).reversed()) {
            if (left <= 0.004) break
            if (s.voided) continue
            val take = minOf(left, s.due)
            if (take > 0.004) { receivePayment(s.id, take, method); left -= take }
        }
    }
}

/* ==================================================================== expenses */

val EXPENSE_CATEGORIES = listOf("Rent", "Electricity", "Salaries", "Transport", "Tea & food", "Repairs", "Other")

fun Store.expenses(p: Period, limit: Int = 100): List<Expense> =
    query(
        "SELECT id, date, category, amount, note FROM expenses WHERE date >= ? AND date < ? ORDER BY id DESC LIMIT ?",
        p.from.toString(), p.to.toString(), limit.toString(),
    ) { Expense(it.getLong(0), it.getLong(1), it.getString(2), it.getDouble(3), it.getString(4)) }

fun Store.addExpense(category: String, amount: Double, note: String): String? {
    if (amount <= 0) return "Enter an amount."
    db.insert("expenses", null, ContentValues().apply {
        put("date", now()); put("category", category.trim().ifEmpty { "Other" }); put("amount", amount); put("note", note.trim())
    })
    bump()
    return null
}

fun Store.deleteExpense(id: Long) {
    db.delete("expenses", "id = ?", arrayOf(id.toString()))
    bump()
}

fun Store.expensesByCategory(p: Period): List<NamedAmount> =
    query(
        "SELECT category, COUNT(*), SUM(amount) AS a FROM expenses WHERE date >= ? AND date < ? GROUP BY category ORDER BY a DESC",
        p.from.toString(), p.to.toString(),
    ) { NamedAmount(it.getString(0), it.getInt(1), it.getDouble(2)) }

/* ==================================================================== vehicles */

private fun vehicleOf(c: Cursor) = Vehicle(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1)

fun Store.vehicles(onlyActive: Boolean = false): List<Vehicle> =
    query(
        "SELECT id, name, reg, active FROM vehicles " + (if (onlyActive) "WHERE active = 1 " else "") + "ORDER BY name COLLATE NOCASE",
    ) { vehicleOf(it) }

fun Store.vehicle(id: Long): Vehicle? =
    query("SELECT id, name, reg, active FROM vehicles WHERE id = ?", id.toString()) { vehicleOf(it) }.firstOrNull()

fun Store.saveVehicle(id: Long?, name: String, reg: String, active: Boolean): String? {
    val clean = name.trim()
    if (clean.isEmpty()) return "Enter a name for the vehicle."
    if (scalar("SELECT COUNT(*) FROM vehicles WHERE lower(name) = lower(?) AND id <> ?", clean, (id ?: 0L).toString()) > 0) {
        return "You already have a vehicle with that name."
    }
    val cv = ContentValues().apply { put("name", clean); put("reg", reg.trim()); put("active", if (active) 1 else 0) }
    if (id == null) db.insert("vehicles", null, cv) else db.update("vehicles", cv, "id = ?", arrayOf(id.toString()))
    bump()
    return null
}

fun Store.vehicleEntries(vehicleId: Long, p: Period): List<VehicleEntry> =
    query(
        "SELECT id, vehicle_id, kind, date, amount, litres, note FROM vehicle_entries " +
            "WHERE vehicle_id = ? AND date >= ? AND date < ? ORDER BY date DESC",
        vehicleId.toString(), p.from.toString(), p.to.toString(),
    ) { VehicleEntry(it.getLong(0), it.getLong(1), it.getInt(2), it.getLong(3), it.getDouble(4), it.getDouble(5), it.getString(6)) }

fun Store.addVehicleEntry(vehicleId: Long, kind: Int, amount: Double, litres: Double, note: String): String? {
    if (amount <= 0) return "Enter an amount."
    db.insert("vehicle_entries", null, ContentValues().apply {
        put("vehicle_id", vehicleId); put("kind", kind); put("date", now()); put("amount", amount)
        put("litres", if (kind == EntryKind.FUEL) litres else 0.0); put("note", note.trim())
    })
    bump()
    return null
}

fun Store.deleteVehicleEntry(id: Long) {
    db.delete("vehicle_entries", "id = ?", arrayOf(id.toString()))
    bump()
}

/** Sales that named this vehicle in "Delivered on". */
fun Store.vehicleSales(vehicleId: Long, p: Period): List<Sale> =
    salesWhere("vehicle_id = ? AND voided = 0 AND date >= ? AND date < ?", vehicleId.toString(), p.from.toString(), p.to.toString())

private fun Store.salesWhere(where: String, vararg args: String): List<Sale> =
    query(
        "SELECT id, no, date, customer, sub, discount, tax_rate, tax, charge_label, charge, total, paid, returned, vehicle_id, voided " +
            "FROM sales WHERE $where ORDER BY id DESC",
        *args,
    ) {
        Sale(
            it.getLong(0), it.getInt(1), it.getLong(2), it.getString(3), it.getDouble(4), it.getDouble(5), it.getDouble(6),
            it.getDouble(7), it.getString(8), it.getDouble(9), it.getDouble(10), it.getDouble(11), it.getDouble(12),
            it.getLong(13), it.getInt(14) == 1,
        )
    }

private fun Store.entrySum(vid: Long, kind: Int, p: Period): Double =
    scalar(
        "SELECT SUM(amount) FROM vehicle_entries WHERE vehicle_id = ? AND kind = ? AND date >= ? AND date < ?",
        vid.toString(), kind.toString(), p.from.toString(), p.to.toString(),
    )

fun Store.vehicleSummary(v: Vehicle, p: Period): VehicleSummary {
    val id = v.id.toString()
    val from = p.from.toString()
    val to = p.to.toString()
    return VehicleSummary(
        vehicle = v,
        deliveries = scalar("SELECT COUNT(*) FROM sales WHERE vehicle_id = ? AND voided = 0 AND date >= ? AND date < ?", id, from, to).toInt(),
        charges = scalar("SELECT SUM(charge) FROM sales WHERE vehicle_id = ? AND voided = 0 AND date >= ? AND date < ?", id, from, to),
        earnings = entrySum(v.id, EntryKind.EARNING, p),
        fuel = entrySum(v.id, EntryKind.FUEL, p),
        maintenance = entrySum(v.id, EntryKind.MAINTENANCE, p),
        salary = entrySum(v.id, EntryKind.SALARY, p),
        other = entrySum(v.id, EntryKind.OTHER, p),
    )
}

fun Store.vehicleSummaries(p: Period): List<VehicleSummary> = vehicles().map { vehicleSummary(it, p) }

/* ===================================================================== reports */

fun Store.salesByDay(p: Period): List<DayRow> =
    query(
        "SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS d, COUNT(*), SUM(total), SUM(paid) " +
            "FROM sales WHERE voided = 0 AND date >= ? AND date < ? GROUP BY d ORDER BY d DESC",
        p.from.toString(), p.to.toString(),
    ) { DayRow(it.getString(0), it.getInt(1), it.getDouble(2), it.getDouble(3)) }

/** Tax collected each day: [DayRow.total] is the tax, [DayRow.received] the goods it was charged on. */
fun Store.taxByDay(p: Period): List<DayRow> =
    query(
        "SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS d, COUNT(*), SUM(tax), SUM(sub - discount) " +
            "FROM sales WHERE voided = 0 AND tax > 0 AND date >= ? AND date < ? GROUP BY d ORDER BY d DESC",
        p.from.toString(), p.to.toString(),
    ) { DayRow(it.getString(0), it.getInt(1), it.getDouble(2), it.getDouble(3)) }

/** Total sales for each of the last [days] days, oldest first, with zero for quiet days. */
fun Store.lastDaysTotals(days: Int): List<Pair<String, Double>> {
    val rows = salesByDay(Periods.lastDays(days)).associate { it.day to it.total }
    val key = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val label = SimpleDateFormat("EEE", Locale.getDefault())
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -(days - 1))
    val out = ArrayList<Pair<String, Double>>()
    repeat(days) {
        out.add(label.format(c.time) to (rows[key.format(c.time)] ?: 0.0))
        c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return out
}

fun Store.salesByProduct(p: Period): List<ProductSalesRow> =
    query(
        "SELECT l.name, SUM(l.qty), SUM(l.qty * l.price) AS a FROM sale_lines l JOIN sales s ON s.id = l.sale_id " +
            "WHERE s.voided = 0 AND s.date >= ? AND s.date < ? GROUP BY l.name ORDER BY a DESC",
        p.from.toString(), p.to.toString(),
    ) { ProductSalesRow(it.getString(0), it.getDouble(1), it.getDouble(2)) }

fun Store.salesByCategory(p: Period): List<ProductSalesRow> =
    query(
        "SELECT CASE WHEN l.kind = 1 THEN 'Services' WHEN l.kind = 2 THEN 'Custom items' WHEN pr.category IS NULL OR pr.category = '' THEN 'No category' ELSE pr.category END AS cat, SUM(l.qty), SUM(l.qty * l.price) AS a " +
            "FROM sale_lines l JOIN sales s ON s.id = l.sale_id LEFT JOIN products pr ON pr.id = l.product_id " +
            "WHERE s.voided = 0 AND s.date >= ? AND s.date < ? GROUP BY cat ORDER BY a DESC",
        p.from.toString(), p.to.toString(),
    ) { ProductSalesRow(it.getString(0), it.getDouble(1), it.getDouble(2)) }

fun Store.salesByCustomer(p: Period): List<NamedAmount> =
    query(
        "SELECT customer, COUNT(*), SUM(total) AS a FROM sales WHERE voided = 0 AND date >= ? AND date < ? GROUP BY customer ORDER BY a DESC",
        p.from.toString(), p.to.toString(),
    ) { NamedAmount(it.getString(0), it.getInt(1), it.getDouble(2)) }

fun Store.profit(p: Period): ProfitSummary {
    val from = p.from.toString()
    val to = p.to.toString()
    val returnsValue = scalar("SELECT SUM(amount) FROM sale_returns WHERE date >= ? AND date < ?", from, to)
    val returnsCost = scalar(
        "SELECT SUM(rl.qty * rl.cost) FROM sale_return_lines rl JOIN sale_returns r ON r.id = rl.return_id WHERE r.date >= ? AND r.date < ?",
        from, to,
    )
    return ProfitSummary(
        revenue = scalar("SELECT SUM(sub) FROM sales WHERE voided = 0 AND date >= ? AND date < ?", from, to) - returnsValue,
        discount = scalar("SELECT SUM(discount) FROM sales WHERE voided = 0 AND date >= ? AND date < ?", from, to),
        cogs = scalar(
            "SELECT SUM(l.qty * l.cost) FROM sale_lines l JOIN sales s ON s.id = l.sale_id WHERE s.voided = 0 AND s.date >= ? AND s.date < ?",
            from, to,
        ) - returnsCost,
        bought = scalar("SELECT SUM(total) FROM purchases WHERE date >= ? AND date < ?", from, to),
        charges = scalar("SELECT SUM(charge) FROM sales WHERE voided = 0 AND date >= ? AND date < ?", from, to),
        tax = scalar("SELECT SUM(tax) FROM sales WHERE voided = 0 AND date >= ? AND date < ?", from, to),
        expenses = scalar("SELECT SUM(amount) FROM expenses WHERE date >= ? AND date < ?", from, to),
        vehicleCosts = scalar("SELECT SUM(amount) FROM vehicle_entries WHERE kind <> 5 AND date >= ? AND date < ?", from, to),
        vehicleEarnings = scalar("SELECT SUM(amount) FROM vehicle_entries WHERE kind = 5 AND date >= ? AND date < ?", from, to),
    )
}

fun Store.customersOwing(): List<OwedRow> =
    query(
        "SELECT customer, SUM(total - returned - paid) AS due FROM sales WHERE voided = 0 AND total - returned - paid > 0.004 " +
            "GROUP BY customer ORDER BY due DESC",
    ) { OwedRow(it.getString(0), it.getDouble(1)) }

fun Store.suppliersOwed(): List<OwedRow> =
    query(
        "SELECT supplier, SUM(total - returned - paid) AS due FROM purchases WHERE total - returned - paid > 0.004 GROUP BY supplier ORDER BY due DESC",
    ) { OwedRow(it.getString(0), it.getDouble(1)) }

fun Store.customersOweTotal(): Double =
    scalar("SELECT SUM(MAX(total - returned - paid, 0)) FROM sales WHERE voided = 0")

fun Store.stockValue(): Double = scalar("SELECT SUM(stock * cost) FROM products")

/** Every movement of money in the period: payments in and out, expenses and vehicle entries. Newest first. */
fun Store.cashBook(p: Period): List<CashRow> =
    query(
        "SELECT date, label, amt FROM (" +
            "SELECT date, CASE kind WHEN 'sale_in' THEN 'Received from ' WHEN 'supplier_refund_in' THEN 'Refund from ' WHEN 'purchase_out' THEN 'Paid to ' ELSE 'Refunded to ' END || party || CASE WHEN method = 'Cash' THEN '' ELSE ' · ' || method END AS label, " +
            "CASE kind WHEN 'sale_in' THEN amount WHEN 'supplier_refund_in' THEN amount ELSE -amount END AS amt FROM payments " +
            "UNION ALL SELECT date, 'Expense · ' || category, -amount FROM expenses " +
            "UNION ALL SELECT e.date, v.name || ' · ' || CASE e.kind WHEN 1 THEN 'Fuel' WHEN 2 THEN 'Maintenance' WHEN 3 THEN 'Driver salary' " +
            "WHEN 4 THEN 'Other cost' ELSE 'Earning' END, CASE e.kind WHEN 5 THEN e.amount ELSE -e.amount END " +
            "FROM vehicle_entries e JOIN vehicles v ON v.id = e.vehicle_id" +
            ") WHERE date >= ? AND date < ? ORDER BY date DESC",
        p.from.toString(), p.to.toString(),
    ) { CashRow(it.getLong(0), it.getString(1), it.getDouble(2)) }

/* ===================================================================== receipt */

/** The invoice as plain text, ready to share on WhatsApp or by SMS. */
fun Store.receiptText(saleId: Long): String {
    val s = sale(saleId) ?: return ""
    val sb = StringBuilder()
    sb.append(Settings.shop).append('\n')
    sb.append("Invoice #").append(s.no).append("  ").append(fmtDate(s.date)).append('\n')
    sb.append("Customer: ").append(s.customer).append("\n\n")
    for (l in saleLines(saleId)) {
        sb.append(l.name).append("\n  ").append(qtyText(l.qty)).append(" × ").append(money(l.price))
            .append(" = ").append(money(l.amount)).append('\n')
        if (l.serial.isNotBlank()) sb.append("  IMEI/Serial: ").append(l.serial).append('\n')
        if (l.warrantyDays > 0) sb.append("  Warranty: ").append(l.warrantyDays).append(" days\n")
    }
    sb.append('\n')
    sb.append("Subtotal: ").append(money(s.sub)).append('\n')
    if (s.discount > 0) sb.append("Discount: -").append(money(s.discount)).append('\n')
    if (s.tax > 0) sb.append("Tax (").append(qtyText(s.taxRate)).append("%): ").append(money(s.tax)).append('\n')
    if (s.charge > 0) sb.append(s.chargeLabel).append(": ").append(money(s.charge)).append('\n')
    sb.append("TOTAL: ").append(money(s.total)).append('\n')
    sb.append("Paid: ").append(money(s.paid)).append('\n')
    if (s.returned > 0) sb.append("Returned: ").append(money(s.returned)).append('\n')
    if (s.due > 0.004) sb.append("Balance due: ").append(money(s.due)).append('\n')
    sb.append("\nThank you!")
    return sb.toString()
}

/* ====================================================================== backup */

/** Everything in the app as one JSON text. Restoring it later brings the data back. */
fun Store.backupJson(): String {
    val root = JSONObject()
    root.put("shop", Settings.shop)
    root.put("currency", Settings.currency)
    root.put("taxRate", Settings.taxRate)
    root.put("chargeLabel", Settings.chargeLabel)
    Settings.logo?.let { root.put("logo", Base64.encodeToString(it, Base64.NO_WRAP)) }
    val data = JSONObject()
    for (t in TABLES) {
        val rows = JSONArray()
        db.rawQuery("SELECT * FROM $t", null).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                for (i in 0 until c.columnCount) {
                    val n = c.getColumnName(i)
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> o.put(n, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> o.put(n, c.getDouble(i))
                        Cursor.FIELD_TYPE_NULL -> Unit
                        Cursor.FIELD_TYPE_BLOB -> o.put(n, "b64:" + Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                        else -> o.put(n, c.getString(i))
                    }
                }
                rows.put(o)
            }
        }
        data.put(t, rows)
    }
    root.put("data", data)
    return root.toString()
}

/** Replaces everything with the contents of a backup. Returns a message if it cannot. */
fun Store.restoreJson(text: String): String? {
    val root = try { JSONObject(text) } catch (e: Exception) { return "That is not a backup file." }
    val data = root.optJSONObject("data") ?: return "That is not a backup file."
    val d = db
    d.beginTransaction()
    try {
        for (t in TABLES.reversed()) d.delete(t, null, null)
        for (t in TABLES) {
            val rows = data.optJSONArray(t) ?: continue
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                val cv = ContentValues()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    when (val v = o.get(k)) {
                        is Int -> cv.put(k, v.toLong())
                        is Long -> cv.put(k, v)
                        is Double -> cv.put(k, v)
                        is String -> if (v.startsWith("b64:")) cv.put(k, Base64.decode(v.substring(4), Base64.NO_WRAP)) else cv.put(k, v)
                        else -> cv.put(k, v.toString())
                    }
                }
                d.insert(t, null, cv)
            }
        }
        Settings.shop = root.optString("shop", Settings.shop)
        Settings.currency = root.optString("currency", Settings.currency)
        Settings.taxRate = root.optDouble("taxRate", Settings.taxRate)
        Settings.chargeLabel = root.optString("chargeLabel", Settings.chargeLabel)
        val logo = root.optString("logo", "")
        Settings.logo = if (logo.isEmpty()) null else Base64.decode(logo, Base64.NO_WRAP)
        d.setTransactionSuccessful()
    } catch (e: Exception) {
        return "Could not read that backup."
    } finally {
        d.endTransaction()
        bump()
    }
    return null
}

/* ============================================================ more reports & lookups */

/** Money in and out for each way of paying (cash, card, bank, wallet…). */
fun Store.paymentMethods(p: Period): List<MethodRow> =
    query(
        "SELECT method, SUM(CASE WHEN kind IN ('sale_in', 'supplier_refund_in') THEN amount ELSE 0 END), " +
            "SUM(CASE WHEN kind IN ('sale_in', 'supplier_refund_in') THEN 0 ELSE amount END) " +
            "FROM payments WHERE date >= ? AND date < ? GROUP BY method ORDER BY 2 DESC",
        p.from.toString(), p.to.toString(),
    ) { MethodRow(it.getString(0), it.getDouble(1), it.getDouble(2)) }

/** What the shop earned from services and items typed in at the till. */
fun Store.serviceSales(p: Period): List<ProductSalesRow> =
    query(
        "SELECT l.name, SUM(l.qty), SUM(l.qty * l.price) AS a FROM sale_lines l JOIN sales s ON s.id = l.sale_id " +
            "WHERE l.kind IN (1, 2) AND s.voided = 0 AND s.date >= ? AND s.date < ? GROUP BY l.name ORDER BY a DESC",
        p.from.toString(), p.to.toString(),
    ) { ProductSalesRow(it.getString(0), it.getDouble(1), it.getDouble(2)) }

fun Store.adjustments(p: Period): List<AdjustmentRow> =
    query(
        "SELECT date, name, delta, reason FROM stock_adjustments WHERE date >= ? AND date < ? ORDER BY id DESC LIMIT 200",
        p.from.toString(), p.to.toString(),
    ) { AdjustmentRow(it.getLong(0), it.getString(1), it.getDouble(2), it.getString(3)) }

/**
 * Every sale or purchase that mentions this IMEI or serial number (part of it is enough), newest first.
 * Lets you check who bought a phone and whether it is still under warranty.
 */
fun Store.findSerial(text: String): List<SerialHit> {
    val q = text.trim()
    if (q.length < 3) return emptyList()
    val like = "%$q%"
    val sold = query(
        "SELECT l.name, l.serial, s.date, s.customer, s.no, l.warranty_days FROM sale_lines l JOIN sales s ON s.id = l.sale_id " +
            "WHERE l.serial LIKE ? AND s.voided = 0 ORDER BY s.id DESC LIMIT 40",
        like,
    ) { SerialHit(false, it.getString(0), it.getString(1), it.getLong(2), it.getString(3), it.getInt(4), it.getInt(5)) }
    val bought = query(
        "SELECT l.name, l.serial, p.date, p.supplier, p.no FROM purchase_lines l JOIN purchases p ON p.id = l.purchase_id " +
            "WHERE l.serial LIKE ? ORDER BY p.id DESC LIMIT 40",
        like,
    ) { SerialHit(true, it.getString(0), it.getString(1), it.getLong(2), it.getString(3), it.getInt(4), 0) }
    return (sold + bought).sortedByDescending { it.date }
}

/** The invoice as a small HTML page, for printing or saving as a PDF. */
fun Store.receiptHtml(saleId: Long): String {
    val s = sale(saleId) ?: return ""
    fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val rows = StringBuilder()
    for (l in saleLines(saleId)) {
        rows.append("<tr><td>").append(esc(l.name))
        if (l.serial.isNotBlank()) rows.append("<br><small>IMEI/Serial: ").append(esc(l.serial)).append("</small>")
        if (l.warrantyDays > 0) rows.append("<br><small>Warranty: ").append(l.warrantyDays).append(" days</small>")
        rows.append("</td><td class='n'>").append(qtyText(l.qty)).append("</td><td class='n'>").append(esc(money(l.price)))
            .append("</td><td class='n'>").append(esc(money(l.amount))).append("</td></tr>")
    }
    fun line(label: String, value: String, strong: Boolean = false) =
        "<tr${if (strong) " class='t'" else ""}><td colspan='3' class='n'>${esc(label)}</td><td class='n'>${esc(value)}</td></tr>"
    val totals = StringBuilder()
    totals.append(line("Subtotal", money(s.sub)))
    if (s.discount > 0) totals.append(line("Discount", "- " + money(s.discount)))
    if (s.tax > 0) totals.append(line("Tax (${qtyText(s.taxRate)}%)", money(s.tax)))
    if (s.charge > 0) totals.append(line(s.chargeLabel, money(s.charge)))
    totals.append(line("Total", money(s.total), true))
    totals.append(line("Paid", money(s.paid)))
    if (s.due > 0.004) totals.append(line("Balance due", money(s.due)))
    return "<html><head><meta charset='utf-8'><style>" +
        "body{font-family:sans-serif;font-size:13px;margin:18px;color:#111}h2{margin:0}small{color:#555}" +
        "table{width:100%;border-collapse:collapse;margin-top:12px}td,th{padding:6px 4px;border-bottom:1px solid #ddd;text-align:left}" +
        ".n{text-align:right}.t td{font-weight:bold;font-size:15px}</style></head><body>" +
        "<h2>${esc(Settings.shop)}</h2><div>Invoice #${s.no} &middot; ${esc(fmtDate(s.date))}</div><div>Customer: ${esc(s.customer)}</div>" +
        "<table><tr><th>Item</th><th class='n'>Qty</th><th class='n'>Price</th><th class='n'>Amount</th></tr>$rows$totals</table>" +
        "<p style='text-align:center;margin-top:20px'>Thank you!</p></body></html>"
}
