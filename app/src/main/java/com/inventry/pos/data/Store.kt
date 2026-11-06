package com.inventry.pos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Every table, in the order a backup writes them. */
val TABLES = listOf(
    "products", "customers", "suppliers", "sales", "sale_lines", "sale_returns", "sale_return_lines",
    "purchases", "purchase_lines", "payments", "expenses", "vehicles", "vehicle_entries", "stock_adjustments",
)

private val SCHEMA = listOf(
    "CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price REAL NOT NULL, " +
        "cost REAL NOT NULL, stock REAL NOT NULL, low REAL NOT NULL, category TEXT NOT NULL DEFAULT '', " +
        "barcode TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL DEFAULT '', image BLOB)",
    "CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, no INTEGER NOT NULL, date INTEGER NOT NULL, customer TEXT NOT NULL, " +
        "sub REAL NOT NULL, discount REAL NOT NULL, tax_rate REAL NOT NULL, tax REAL NOT NULL, charge_label TEXT NOT NULL, " +
        "charge REAL NOT NULL, total REAL NOT NULL, paid REAL NOT NULL, returned REAL NOT NULL, vehicle_id INTEGER NOT NULL, voided INTEGER NOT NULL)",
    "CREATE TABLE sale_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, product_id INTEGER NOT NULL, " +
        "name TEXT NOT NULL, qty REAL NOT NULL, price REAL NOT NULL, cost REAL NOT NULL)",
    "CREATE TABLE sale_returns (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, date INTEGER NOT NULL, " +
        "amount REAL NOT NULL, refund REAL NOT NULL, note TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE sale_return_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, return_id INTEGER NOT NULL, product_id INTEGER NOT NULL, " +
        "name TEXT NOT NULL, qty REAL NOT NULL, price REAL NOT NULL, cost REAL NOT NULL)",
    "CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, no INTEGER NOT NULL, date INTEGER NOT NULL, supplier TEXT NOT NULL, " +
        "total REAL NOT NULL, paid REAL NOT NULL)",
    "CREATE TABLE purchase_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER NOT NULL, product_id INTEGER NOT NULL, " +
        "name TEXT NOT NULL, qty REAL NOT NULL, cost REAL NOT NULL)",
    "CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, date INTEGER NOT NULL, kind TEXT NOT NULL, ref_id INTEGER NOT NULL, " +
        "party TEXT NOT NULL, amount REAL NOT NULL)",
    "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, date INTEGER NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, note TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, reg TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE vehicle_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL, kind INTEGER NOT NULL, " +
        "date INTEGER NOT NULL, amount REAL NOT NULL, litres REAL NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE stock_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, date INTEGER NOT NULL, product_id INTEGER NOT NULL, " +
        "name TEXT NOT NULL, delta REAL NOT NULL, reason TEXT NOT NULL DEFAULT '')",
)

/**
 * A real SQLite database file in the app's private storage, so records stay after the app is
 * closed, the phone restarts or the app is updated. Uninstalling the app (or clearing its data)
 * removes it; Settings → Backup keeps a copy outside the app.
 *
 * When the tables change in a later version, raise the version number and add the change to
 * [MemoryHelper.onUpgrade] so existing records are kept.
 */
private class MemoryHelper(context: Context) : SQLiteOpenHelper(context, "khan_mobile.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

object Store {
    private lateinit var helper: MemoryHelper

    /** Screens read this, so they redraw whenever something is saved. */
    var rev by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        Settings.init(context)
        if (!::helper.isInitialized) helper = MemoryHelper(context.applicationContext)
    }

    internal val db: SQLiteDatabase get() = helper.writableDatabase

    internal fun bump() {
        rev++
    }

    internal inline fun <T> query(sql: String, vararg args: String, map: (Cursor) -> T): List<T> {
        db.rawQuery(sql, arrayOf(*args)).use { c ->
            val out = ArrayList<T>()
            while (c.moveToNext()) out.add(map(c))
            return out
        }
    }

    internal fun scalar(sql: String, vararg args: String): Double =
        db.rawQuery(sql, arrayOf(*args)).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else 0.0 }

    internal fun now(): Long = System.currentTimeMillis()

    /** Keeps a customer or supplier in the address book the first time their name is typed on a sale or purchase. */
    internal fun ensureParty(table: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (scalar("SELECT COUNT(*) FROM $table WHERE lower(name) = lower(?)", n) == 0.0) {
            db.insert(table, null, ContentValues().apply { put("name", n); put("phone", ""); put("address", "") })
        }
    }

    /** A line in the cash book: money that came in ("sale_in") or went out ("purchase_out", "refund_out"). */
    internal fun addPayment(kind: String, refId: Long, party: String, amount: Double) {
        if (amount <= 0.004) return
        db.insert("payments", null, ContentValues().apply {
            put("date", now()); put("kind", kind); put("ref_id", refId); put("party", party); put("amount", amount)
        })
    }

    /* ---------------------------------------------------------------- products */

    private val productColumns = "id, name, price, cost, stock, low, category, barcode, unit, image IS NOT NULL"

    private fun productOf(c: Cursor) = Product(
        c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5),
        c.getString(6), c.getString(7), c.getString(8), c.getInt(9) == 1,
    )

    /** The product's photo, if it has one. Read on demand so lists stay light. */
    fun productImage(id: Long): ByteArray? =
        db.rawQuery("SELECT image FROM products WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getBlob(0) else null
        }

    /** Matches the name, the barcode or the category. */
    fun products(search: String = ""): List<Product> =
        query(
            "SELECT $productColumns FROM products WHERE name LIKE ? OR barcode LIKE ? OR category LIKE ? ORDER BY name COLLATE NOCASE",
            "%$search%", "%$search%", "%$search%",
        ) { productOf(it) }

    fun product(id: Long): Product? =
        query("SELECT $productColumns FROM products WHERE id = ?", id.toString()) { productOf(it) }.firstOrNull()

    fun categories(): List<String> =
        query("SELECT DISTINCT category FROM products WHERE category <> '' ORDER BY category COLLATE NOCASE") { it.getString(0) }

    /** Adds a product, or changes it when [id] is given. Returns a message when it cannot be saved. */
    fun saveProduct(
        id: Long?, name: String, price: Double, cost: Double, stock: Double, low: Double,
        category: String, barcode: String, unit: String,
        image: ByteArray? = null, removeImage: Boolean = false,
    ): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return "Enter a name."
        val myId = (id ?: 0L).toString()
        if (scalar("SELECT COUNT(*) FROM products WHERE lower(name) = lower(?) AND id <> ?", clean, myId) > 0) {
            return "That product already exists."
        }
        val code = barcode.trim()
        if (code.isNotEmpty() && scalar("SELECT COUNT(*) FROM products WHERE barcode = ? AND id <> ?", code, myId) > 0) {
            return "Another product already has that barcode."
        }
        val cv = ContentValues().apply {
            put("name", clean); put("price", price); put("cost", cost); put("stock", stock); put("low", low)
            put("category", category.trim()); put("barcode", code); put("unit", unit.trim())
            if (image != null) put("image", image) else if (removeImage) putNull("image")
        }
        if (id == null) db.insert("products", null, cv) else db.update("products", cv, "id = ?", arrayOf(id.toString()))
        bump()
        return null
    }

    fun deleteProduct(id: Long): String? {
        val used = scalar(
            "SELECT (SELECT COUNT(*) FROM sale_lines WHERE product_id = ?) + (SELECT COUNT(*) FROM purchase_lines WHERE product_id = ?)",
            id.toString(), id.toString(),
        )
        if (used > 0) return "It has sales or purchases, so it cannot be deleted."
        db.delete("products", "id = ?", arrayOf(id.toString()))
        bump()
        return null
    }

    /** Adds ([delta] > 0) or removes ([delta] < 0) stock and remembers why. */
    fun adjustStock(productId: Long, delta: Double, reason: String): String? {
        val p = product(productId) ?: return "Product not found."
        if (delta == 0.0) return "Enter how many to add or remove."
        if (p.stock + delta < 0) return "There is only ${qtyText(p.stock)} in stock."
        db.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?", arrayOf<Any>(delta, productId))
        db.insert("stock_adjustments", null, ContentValues().apply {
            put("date", now()); put("product_id", productId); put("name", p.name); put("delta", delta); put("reason", reason.trim())
        })
        bump()
        return null
    }

    /* ------------------------------------------------------------------- sales */

    private const val SALE_COLUMNS =
        "id, no, date, customer, sub, discount, tax_rate, tax, charge_label, charge, total, paid, returned, vehicle_id, voided"

    private fun saleOf(c: Cursor) = Sale(
        c.getLong(0), c.getInt(1), c.getLong(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getDouble(6),
        c.getDouble(7), c.getString(8), c.getDouble(9), c.getDouble(10), c.getDouble(11), c.getDouble(12),
        c.getLong(13), c.getInt(14) == 1,
    )

    fun sales(limit: Int = 30): List<Sale> =
        query("SELECT $SALE_COLUMNS FROM sales ORDER BY id DESC LIMIT ?", limit.toString()) { saleOf(it) }

    fun sale(id: Long): Sale? =
        query("SELECT $SALE_COLUMNS FROM sales WHERE id = ?", id.toString()) { saleOf(it) }.firstOrNull()

    fun salesOf(customer: String): List<Sale> =
        query("SELECT $SALE_COLUMNS FROM sales WHERE lower(customer) = lower(?) ORDER BY id DESC", customer) { saleOf(it) }

    fun saleLines(saleId: Long): List<CartLine> =
        query("SELECT product_id, name, qty, price, cost FROM sale_lines WHERE sale_id = ? ORDER BY id", saleId.toString()) {
            CartLine(it.getLong(0), it.getString(1), it.getDouble(2), it.getDouble(3), it.getDouble(4))
        }

    /**
     * [paidIn] null means "paid in full". The tax is a percentage of the goods after discount; the
     * extra charge (delivery, shipping, fuel…) is not taxed and is not part of gross profit.
     */
    fun createSale(
        customer: String,
        lines: List<CartLine>,
        discount: Double,
        taxRate: Double,
        chargeLabel: String,
        charge: Double,
        vehicleId: Long,
        paidIn: Double?,
    ): Outcome {
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
            val base = sub - disc
            val rate = taxRate.coerceAtLeast(0.0)
            val tax = base * rate / 100.0
            val extra = charge.coerceAtLeast(0.0)
            val total = base + tax + extra
            val paid = (paidIn ?: total).coerceIn(0.0, total)
            val who = customer.trim().ifEmpty { "Walk-in customer" }
            if (who != "Walk-in customer") ensureParty("customers", who)
            val no = scalar("SELECT COALESCE(MAX(no), 0) + 1 FROM sales").toInt()
            val sid = d.insert("sales", null, ContentValues().apply {
                put("no", no); put("date", now()); put("customer", who)
                put("sub", sub); put("discount", disc); put("tax_rate", rate); put("tax", tax)
                put("charge_label", chargeLabel.trim().ifEmpty { Settings.chargeLabel }); put("charge", extra)
                put("total", total); put("paid", paid); put("returned", 0.0)
                put("vehicle_id", vehicleId); put("voided", 0)
            })
            for (l in lines) {
                d.insert("sale_lines", null, ContentValues().apply {
                    put("sale_id", sid); put("product_id", l.productId); put("name", l.name)
                    put("qty", l.qty); put("price", l.price); put("cost", l.cost)
                })
                d.execSQL("UPDATE products SET stock = stock - ? WHERE id = ?", arrayOf<Any>(l.qty, l.productId))
            }
            addPayment("sale_in", sid, who, paid)
            d.setTransactionSuccessful()
            return Outcome(id = sid)
        } finally {
            d.endTransaction()
            bump()
        }
    }

    fun receivePayment(saleId: Long, amount: Double) {
        val s = sale(saleId) ?: return
        val take = amount.coerceAtMost(s.due)
        if (take <= 0.004) return
        db.execSQL("UPDATE sales SET paid = paid + ? WHERE id = ?", arrayOf<Any>(take, saleId))
        addPayment("sale_in", saleId, s.customer, take)
        bump()
    }

    /** Cancels the invoice, puts its goods back in stock and hands back what the customer had paid. */
    fun voidSale(saleId: Long) {
        val d = db
        d.beginTransaction()
        try {
            val s = sale(saleId)
            if (s != null && !s.voided) {
                // Goods already returned were put back by the return itself.
                for (l in saleLines(saleId)) {
                    val back = l.qty - returnedQty(saleId, l.productId)
                    if (back > 0) d.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?", arrayOf<Any>(back, l.productId))
                }
                d.execSQL("UPDATE sales SET voided = 1 WHERE id = ?", arrayOf<Any>(saleId))
                addPayment("refund_out", saleId, s.customer, s.paid)
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
            bump()
        }
    }

    /* ----------------------------------------------------------------- returns */

    /** How much of a product has already been brought back from this sale. */
    fun returnedQty(saleId: Long, productId: Long): Double =
        scalar(
            "SELECT COALESCE(SUM(rl.qty), 0) FROM sale_return_lines rl JOIN sale_returns r ON r.id = rl.return_id " +
                "WHERE r.sale_id = ? AND rl.product_id = ?",
            saleId.toString(), productId.toString(),
        )

    fun saleReturns(limit: Int = 30): List<ReturnRow> =
        query(
            "SELECT r.id, r.date, s.no, s.customer, r.amount, r.refund FROM sale_returns r JOIN sales s ON s.id = r.sale_id " +
                "ORDER BY r.id DESC LIMIT ?",
            limit.toString(),
        ) { ReturnRow(it.getLong(0), it.getLong(1), it.getInt(2), it.getString(3), it.getDouble(4), it.getDouble(5)) }

    /**
     * Takes goods back from a sale: they return to stock, the customer's bill goes down, and any money
     * they had already paid beyond what they now owe is handed back.
     */
    fun createSaleReturn(saleId: Long, items: List<CartLine>, note: String): Outcome {
        val s = sale(saleId) ?: return Outcome(error = "Sale not found.")
        if (s.voided) return Outcome(error = "That sale was voided.")
        val items2 = items.filter { it.qty > 0 }
        if (items2.isEmpty()) return Outcome(error = "Enter a quantity to return.")
        val d = db
        d.beginTransaction()
        try {
            val sold = saleLines(saleId)
            for (l in items2) {
                val line = sold.firstOrNull { it.productId == l.productId } ?: return Outcome(error = "${l.name} is not on that sale.")
                val soldQty = sold.filter { it.productId == l.productId }.sumOf { it.qty }
                val left = soldQty - returnedQty(saleId, l.productId)
                if (l.qty > left + 1e-9) return Outcome(error = "Only ${qtyText(left)} of ${line.name} can still be returned.")
            }
            val amount = items2.sumOf { it.amount }
            val rid = d.insert("sale_returns", null, ContentValues().apply {
                put("sale_id", saleId); put("date", now()); put("amount", amount); put("refund", 0.0); put("note", note.trim())
            })
            for (l in items2) {
                d.insert("sale_return_lines", null, ContentValues().apply {
                    put("return_id", rid); put("product_id", l.productId); put("name", l.name)
                    put("qty", l.qty); put("price", l.price); put("cost", l.cost)
                })
                d.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?", arrayOf<Any>(l.qty, l.productId))
            }
            val returnedNow = s.returned + amount
            val excess = (s.paid - (s.total - returnedNow)).coerceAtLeast(0.0).coerceAtMost(s.paid)
            d.execSQL("UPDATE sales SET returned = ?, paid = paid - ? WHERE id = ?", arrayOf<Any>(returnedNow, excess, saleId))
            d.execSQL("UPDATE sale_returns SET refund = ? WHERE id = ?", arrayOf<Any>(excess, rid))
            addPayment("refund_out", saleId, s.customer, excess)
            d.setTransactionSuccessful()
            return Outcome(id = rid)
        } finally {
            d.endTransaction()
            bump()
        }
    }

    /* --------------------------------------------------------------- purchases */

    private fun purchaseOf(c: Cursor) =
        Purchase(c.getLong(0), c.getInt(1), c.getLong(2), c.getString(3), c.getDouble(4), c.getDouble(5))

    fun purchases(limit: Int = 30): List<Purchase> =
        query("SELECT id, no, date, supplier, total, paid FROM purchases ORDER BY id DESC LIMIT ?", limit.toString()) { purchaseOf(it) }

    fun purchasesOf(supplier: String): List<Purchase> =
        query("SELECT id, no, date, supplier, total, paid FROM purchases WHERE lower(supplier) = lower(?) ORDER BY id DESC", supplier) { purchaseOf(it) }

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
            val who = supplier.trim().ifEmpty { "Unnamed supplier" }
            if (who != "Unnamed supplier") ensureParty("suppliers", who)
            val no = scalar("SELECT COALESCE(MAX(no), 0) + 1 FROM purchases").toInt()
            val pid = d.insert("purchases", null, ContentValues().apply {
                put("no", no); put("date", now()); put("supplier", who); put("total", total); put("paid", paid)
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
            addPayment("purchase_out", pid, who, paid)
            d.setTransactionSuccessful()
            return Outcome(id = pid)
        } finally {
            d.endTransaction()
            bump()
        }
    }

    fun paySupplier(purchaseId: Long, amount: Double) {
        val p = purchase(purchaseId) ?: return
        val give = amount.coerceAtMost(p.due)
        if (give <= 0.004) return
        db.execSQL("UPDATE purchases SET paid = paid + ? WHERE id = ?", arrayOf<Any>(give, purchaseId))
        addPayment("purchase_out", purchaseId, p.supplier, give)
        bump()
    }
}
