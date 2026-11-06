package com.inventry.pos.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

/**
 * Finds products that look like a photo. Every product photo gets a small "signature": an 8×8 colour
 * thumbnail (what is where) plus a 16-bin colour histogram (what colours are in it). A new photo is
 * compared with those signatures. This is visual similarity, not object recognition, so it works best
 * when the product photos and the search photo are taken in similar light and framing.
 */
object ImageSearch {
    private const val GRID = 8
    private const val CELLS = GRID * GRID * 3
    private const val BINS = 16
    private const val SIZE = CELLS + BINS

    fun signature(bytes: ByteArray): ByteArray? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { signature(it) }
    } catch (e: Exception) {
        null
    }

    fun signature(src: Bitmap): ByteArray {
        val side = min(src.width, src.height).coerceAtLeast(1)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = ByteArray(SIZE)

        val small = Bitmap.createScaledBitmap(square, GRID, GRID, true)
        var i = 0
        for (y in 0 until GRID) for (x in 0 until GRID) {
            val c = small.getPixel(x, y)
            out[i++] = Color.red(c).toByte()
            out[i++] = Color.green(c).toByte()
            out[i++] = Color.blue(c).toByte()
        }

        val mid = Bitmap.createScaledBitmap(square, 32, 32, true)
        val hist = DoubleArray(BINS)
        val hsv = FloatArray(3)
        for (y in 0 until 32) for (x in 0 until 32) {
            Color.colorToHSV(mid.getPixel(x, y), hsv)
            val bin = if (hsv[1] < 0.18f || hsv[2] < 0.15f) {
                12 + min(3, (hsv[2] * 4f).toInt())      // greys, from dark to white
            } else {
                (hsv[0] / 30f).toInt().coerceIn(0, 11)  // 12 hues
            }
            hist[bin] += 1.0
        }
        val total = hist.sum().coerceAtLeast(1.0)
        for (k in 0 until BINS) out[CELLS + k] = (hist[k] / total * 255.0).toInt().coerceIn(0, 255).toByte()
        return out
    }

    /** 0 = nothing alike, 1 = the same picture. */
    fun similarity(a: ByteArray, b: ByteArray): Double {
        if (a.size != SIZE || b.size != SIZE) return 0.0
        var diff = 0.0
        for (i in 0 until CELLS) diff += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
        val structure = (((1.0 - diff / (CELLS * 255.0)) - 0.6) / 0.4).coerceIn(0.0, 1.0)

        var shared = 0.0
        for (k in 0 until BINS) shared += min(a[CELLS + k].toInt() and 0xFF, b[CELLS + k].toInt() and 0xFF)
        val colours = (shared / 255.0).coerceIn(0.0, 1.0)
        return 0.5 * structure + 0.5 * colours
    }
}

/** The products whose photos look most like [target] (a signature from [ImageSearch.signature]), best first. */
fun Store.searchByImage(target: ByteArray, limit: Int = 8): List<Pair<Product, Double>> {
    // Photos saved before signatures existed get theirs now.
    val missing = query("SELECT id, image FROM products WHERE image IS NOT NULL AND image_sig IS NULL") { it.getLong(0) to it.getBlob(1) }
    for ((id, image) in missing) {
        val sig = ImageSearch.signature(image) ?: continue
        db.execSQL("UPDATE products SET image_sig = ? WHERE id = ?", arrayOf<Any>(sig, id))
    }
    val rows = query("SELECT id, image_sig FROM products WHERE image_sig IS NOT NULL") { it.getLong(0) to it.getBlob(1) }
    return rows
        .map { (id, sig) -> id to ImageSearch.similarity(target, sig) }
        .sortedByDescending { it.second }
        .take(limit)
        .mapNotNull { (id, score) -> product(id)?.let { it to score } }
}
