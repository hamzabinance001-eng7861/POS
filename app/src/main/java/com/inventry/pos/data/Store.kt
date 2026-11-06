package com.inventry.pos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Passing a null file name to SQLiteOpenHelper gives a real SQLite database that lives only in
 * memory. It is empty on every launch and is gone when Android closes the app.
 */
private class MemoryHelper(context: Context) : SQLiteOpenHelper(context, null, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "price REAL NOT NULL, cost REAL NOT NULL, stock REAL NOT NULL, low REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, no INTEGER NOT NULL, date INTEGER NOT NULL, " +
                "customer TEXT NOT NULL, sub REAL NOT NULL, discount REAL NOT NULL, total REAL NOT NULL, " +
                "paid REAL NOT NULL, voided INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE sale_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, " +
                "product_id INTEGER NOT NULL, name TEXT NOT NULL, qty REAL NOT NULL, price REAL NOT NULL, cost REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, no INTEGER NOT NULL, date INTEGER NOT NULL, " +
                "supplier TEXT NOT NULL, total REAL NOT NULL, paid REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE purchase_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER NOT NULL, " +
                "product_id INTEGER NOT NULL, name TEXT NOT NULL, qty REAL NOT NULL, cost REAL NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

object Store {
    private lateinit var helper: MemoryHelper

    /** Screens read this, so they redraw whenever something is saved. */
    var rev by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (!::helper.isInitialized) helper = MemoryHelper(context.applicationContext)
    }

    private val db: SQLiteDatabase get() = helper.writableDatabase

    private inline fun <T> query(sql: String, vararg args: String, map: (Cursor) -> T): List<T> {
        db.rawQuery(sql, arrayOf(*args)).use { c ->
            val out = ArrayList<T>()
            while (c.moveToNext()) out.add(map(c))
            return out
        }
    }

    private fun scalar(sql: String, vararg args: String): Double =
        db.rawQuery(sql, arrayOf(*args)).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else 0.0 }

    /* ---------------------------------------------------------------- products */

    private fun productOf(c: Cursor) =
        Product(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5))

    fun products(search: String = ""): List<Product> =
        query(
            "SELECT id, name, price, cost, stock, low FROM products WHERE name LIKE ? ORDER BY name COLLATE NOCASE",
            "%$search%",
        ) { productOf(it) }

    /** Adds a product, or changes it when [id] is given. Returns a message when it cannot be saved. */
    fun saveProduct(id: Long?, name: String, price: Double, cost: Double, stock: Double, low: Double): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return "Enter a name."
        if (scalar("SELECT COUNT(*) FROM products WHERE lower(name) = lower(?) AND id <> ?", clean, (id ?: 0L).toString()) > 0) {
            return "That product already exists."
        }
        val cv = ContentValues().apply {
            put("name", clean); put("price", price); put("cost", cost); put("stock", stock); put("low", low)
        }
        if (id == null) db.insert("products", null, cv) else db.update("products", cv, "id = ?", arrayOf(id.toString()))
        rev++
        return null
    }

    fun deleteProduct(id: Long): String? {
        val used = scalar("SELECT (SELECT COUNT(*) FROM sale_lines WHERE product_id = ?) + (SELECT COUNT(*) FROM purchase_lines WHERE product_id = ?)", id.toString(), id.toString())
        if (used > 0) return "It has sales or purchases, so it cannot be deleted."
        db.delete("products", "id = ?", arrayOf(id.toString()))
        rev++
        return null
    }

    /* ------------------------------------------------------------------- sales */

    private fun saleOf(c: Cursor) = Sale(
        c.getLong(0), c.getInt(1), c.getLong(2), c.getString(3),
        c.getDouble(4), c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getInt(8) == 1,
    )

    private val saleColumns = "id, no, date, customer, sub, discount, total, paid, voided"

    fun sales(limit: Int = 30): List<Sale> =
        query("SELECT $saleColumns FROM sales ORDER BY id DESC LIMIT ?", limit.toString()) { saleOf(it) }

    fun sale(id: Long): Sale? =
        query("SELECT $saleColumns FROM sales WHERE id = ?", id.toString()) { saleOf(it) }.firstOrNull()

    fun saleLines(saleId: Long): List<CartLine> =
        query("SELECT product_id, name, qty, price, cost FROM sale_lines WHERE sale_id = ? ORDER BY id", saleId.toString()) {
            CartLine(it.getLong(0), it.getString(1), it.getDouble(2), it.getDouble(3), it.getDouble(4))
        }

    /** [paidIn] null means "paid in full". */
    fun createSale(customer: String, lines: List<CartLine>, discount: Double, paidIn: Double?): Outcome {
        if (lines.isEmpty()) return Outcome(error = "Add at least one item.")
        val d = db
        d.beginTransaction()
        try {
            for ((pid, ls) in lines.groupBy { it.productId }) {
                val stock = scalar("SELECT stock FROM products WHERE id = ?", pid.toString())
                if (stock + 1e-9 < ls.sumOf { it.qty }) return Outcome(error = "Not enough stock for ${ls.first().name}.")
            }
            val sub = lines.sumOf { it.amount }
            val disc = discount.coerceIn(0.0, sub)
            val total = sub - disc
            val paid = (paidIn ?: total).coerceIn(0.0, total)
            val no = scalar("SELECT COALESCE(MAX(no), 0) + 1 FROM sales").toInt()
            val sid = d.insert("sales", null, ContentValues().apply {
                put("no", no); put("date", System.currentTimeMillis())
                put("customer", customer.trim().ifEmpty { "Walk-in customer" })
                put("sub", sub); put("discount", disc); put("total", total); put("paid", paid); put("voided", 0)
            })
            for (l in lines) {
                d.insert("sale_lines", null, ContentValues().apply {
                    put("sale_id", sid); put("product_id", l.productId); put("name", l.name)
                    put("qty", l.qty); put("price", l.price); put("cost", l.cost)
                })
                d.execSQL("UPDATE products SET stock = stock - ? WHERE id = ?", arrayOf<Any>(l.qty, l.productId))
            }
            d.setTransactionSuccessful()
            return Outcome(id = sid)
        } finally {
            d.endTransaction()
            rev++
        }
    }

    fun receivePayment(saleId: Long, amount: Double) {
        db.execSQL("UPDATE sales SET paid = MIN(total, paid + ?) WHERE id = ?", arrayOf<Any>(amount, saleId))
        rev++
    }

    /** Cancels the invoice and puts its goods back in stock. */
    fun voidSale(saleId: Long) {
        val d = db
        d.beginTransaction()
        try {
            val s = sale(saleId)
            if (s != null && !s.voided) {
                for (l in saleLines(saleId)) {
                    d.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?", arrayOf<Any>(l.qty, l.productId))
                }
                d.execSQL("UPDATE sales SET voided = 1 WHERE id = ?", arrayOf<Any>(saleId))
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
            rev++
        }
    }

    /* --------------------------------------------------------------- purchases */

    private fun purchaseOf(c: Cursor) =
        Purchase(c.getLong(0), c.getInt(1), c.getLong(2), c.getString(3), c.getDouble(4), c.getDouble(5))

    fun purchases(limit: Int = 30): List<Purchase> =
        query("SELECT id, no, date, supplier, total, paid FROM purchases ORDER BY id DESC LIMIT ?", limit.toString()) { purchaseOf(it) }

    fun purchase(id: Long): Purchase? =
        query("SELECT id, no, date, supplier, total, paid FROM purchases WHERE id = ?", id.toString()) { purchaseOf(it) }.firstOrNull()

    fun purchaseLines(purchaseId: Long): List<CartLine> =
        query("SELECT product_id, name, qty, cost FROM purchase_lines WHERE purchase_id = ? ORDER BY id", purchaseId.toString()) {
            CartLine(it.getLong(0), it.getString(1), it.getDouble(2), it.getDouble(3), it.getDouble(3))
        }

    /** Each line's price is its unit cost. Buying also moves the product's cost to the average. */
    fun createPurchase(supplier: String, lines: List<CartLine>, paidIn: Double?): Outcome {
        if (lines.isEmpty()) return Outcome(error = "Add at least one item.")
        val d = db
        d.beginTransaction()
        try {
            val total = lines.sumOf { it.amount }
            val paid = (paidIn ?: total).coerceIn(0.0, total)
            val no = scalar("SELECT COALESCE(MAX(no), 0) + 1 FROM purchases").toInt()
            val pid = d.insert("purchases", null, ContentValues().apply {
                put("no", no); put("date", System.currentTimeMillis())
                put("supplier", supplier.trim().ifEmpty { "Unnamed supplier" })
                put("total", total); put("paid", paid)
            })
            for (l in lines) {
                d.insert("purchase_lines", null, ContentValues().apply {
                    put("purchase_id", pid); put("product_id", l.productId); put("name", l.name)
                    put("qty", l.qty); put("cost", l.price)
                })
                val have = scalar("SELECT stock FROM products WHERE id = ?", l.productId.toString()).coerceAtLeast(0.0)
                val oldCost = scalar("SELECT cost FROM products WHERE id = ?", l.productId.toString())
                val all = have + l.qty
                val newCost = if (all > 0) (have * oldCost + l.qty * l.price) / all else l.price
                d.execSQL("UPDATE products SET stock = stock + ?, cost = ? WHERE id = ?", arrayOf<Any>(l.qty, newCost, l.productId))
            }
            d.setTransactionSuccessful()
            return Outcome(id = pid)
        } finally {
            d.endTransaction()
            rev++
        }
    }

    fun paySupplier(purchaseId: Long, amount: Double) {
        db.execSQL("UPDATE purchases SET paid = MIN(total, paid + ?) WHERE id = ?", arrayOf<Any>(amount, purchaseId))
        rev++
    }

    /* ----------------------------------------------------------------- reports */

    fun salesByDay(p: Period): List<DayRow> =
        query(
            "SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS d, COUNT(*), SUM(total), SUM(paid) " +
                "FROM sales WHERE voided = 0 AND date >= ? AND date < ? GROUP BY d ORDER BY d DESC",
            p.from.toString(), p.to.toString(),
        ) { DayRow(it.getString(0), it.getInt(1), it.getDouble(2), it.getDouble(3)) }

    fun salesByProduct(p: Period): List<ProductSalesRow> =
        query(
            "SELECT l.name, SUM(l.qty), SUM(l.qty * l.price) AS a FROM sale_lines l JOIN sales s ON s.id = l.sale_id " +
                "WHERE s.voided = 0 AND s.date >= ? AND s.date < ? GROUP BY l.name ORDER BY a DESC",
            p.from.toString(), p.to.toString(),
        ) { ProductSalesRow(it.getString(0), it.getDouble(1), it.getDouble(2)) }

    fun profit(p: Period): ProfitSummary {
        val from = p.from.toString()
        val to = p.to.toString()
        return ProfitSummary(
            revenue = scalar("SELECT SUM(sub) FROM sales WHERE voided = 0 AND date >= ? AND date < ?", from, to),
            discount = scalar("SELECT SUM(discount) FROM sales WHERE voided = 0 AND date >= ? AND date < ?", from, to),
            cogs = scalar(
                "SELECT SUM(l.qty * l.cost) FROM sale_lines l JOIN sales s ON s.id = l.sale_id " +
                    "WHERE s.voided = 0 AND s.date >= ? AND s.date < ?", from, to,
            ),
            bought = scalar("SELECT SUM(total) FROM purchases WHERE date >= ? AND date < ?", from, to),
        )
    }

    fun customersOwing(): List<OwedRow> =
        query(
            "SELECT customer, SUM(total - paid) AS due FROM sales WHERE voided = 0 AND total - paid > 0.004 " +
                "GROUP BY customer ORDER BY due DESC",
        ) { OwedRow(it.getString(0), it.getDouble(1)) }

    fun suppliersOwed(): List<OwedRow> =
        query(
            "SELECT supplier, SUM(total - paid) AS due FROM purchases WHERE total - paid > 0.004 " +
                "GROUP BY supplier ORDER BY due DESC",
        ) { OwedRow(it.getString(0), it.getDouble(1)) }

    fun customersOweTotal(): Double =
        scalar("SELECT SUM(total - paid) FROM sales WHERE voided = 0")

    fun stockValue(): Double = scalar("SELECT SUM(stock * cost) FROM products")
}
