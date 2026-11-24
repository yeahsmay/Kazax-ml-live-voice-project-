package id.kazax.mlassistant.capture

import android.graphics.Bitmap
import id.kazax.mlassistant.core.AppSettings
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object FrameUtils {
    fun toJpeg(src: Bitmap, maxSide: Int, quality: Int): ByteArray {
        val s = min(1f, maxSide / max(src.width, src.height).toFloat())
        val bmp = if (s < 1f) Bitmap.createScaledBitmap(
            src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true
        ) else src
        val out = ByteArrayOutputStream(64 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (bmp !== src) bmp.recycle()
        return out.toByteArray()
    }

    /** Tanda tangan kecil (32x18 abu-abu) untuk mendeteksi layar yang tidak berubah -> hemat biaya API. */
    fun signature(src: Bitmap): IntArray {
        val t = Bitmap.createScaledBitmap(src, 32, 18, true)
        val px = IntArray(32 * 18)
        t.getPixels(px, 0, 32, 0, 0, 32, 18)
        if (t !== src) t.recycle()
        return IntArray(px.size) {
            val c = px[it]
            (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10
        }
    }

    fun similar(a: IntArray, b: IntArray): Boolean {
        if (a.size != b.size) return false
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / a.size.toFloat() < 3f
    }

    /** Minimap persegi di pojok atas (kiri bawaan MLBB). Ukuran = % tinggi layar. */
    fun cropMinimap(src: Bitmap, s: AppSettings): Bitmap {
        val side = (src.height * s.minimapSizePct / 100f).toInt().coerceIn(32, min(src.width, src.height))
        val x = if (s.minimapRight) src.width - side else 0
        return Bitmap.createBitmap(src, x.coerceAtLeast(0), 0, side, side)
    }
}
