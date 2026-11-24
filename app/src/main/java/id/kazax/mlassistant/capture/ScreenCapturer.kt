package id.kazax.mlassistant.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Menangkap layar lewat MediaProjection pada resolusi rendah (sisi terpanjang maks 1280 px)
 * agar hemat RAM/baterai. Mendukung rotasi layar tanpa membuat VirtualDisplay kedua
 * (dilarang di Android 14+).
 */
class ScreenCapturer(ctx: Context, private val projection: MediaProjection) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dpi = ctx.resources.displayMetrics.densityDpi
    private val thread = HandlerThread("kazax-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var cw = 0
    private var ch = 0
    private var last: Bitmap? = null

    private fun targetSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        val scale = min(1f, MAX_SIDE / max(dm.widthPixels, dm.heightPixels).toFloat())
        val w = ((dm.widthPixels * scale).toInt() and 1.inv()).coerceAtLeast(2)
        val h = ((dm.heightPixels * scale).toInt() and 1.inv()).coerceAtLeast(2)
        return w to h
    }

    private fun newReader(w: Int, h: Int) = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)

    fun start() {
        val (w, h) = targetSize()
        cw = w; ch = h
        val r = newReader(w, h)
        reader = r
        display = projection.createVirtualDisplay(
            "kazax", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
        )
    }

    /** Frame terbaru, atau frame terakhir bila layar tidak berubah. Null jika belum ada frame. */
    @Synchronized
    fun grab(): Bitmap? {
        val (w, h) = targetSize()
        if (w != cw || h != ch) { // layar diputar
            cw = w; ch = h
            val old = reader
            val nr = newReader(w, h)
            display?.resize(w, h, dpi)
            display?.surface = nr.surface
            reader = nr
            old?.close()
            last = null
        }
        val img = try { reader?.acquireLatestImage() } catch (e: Exception) { null }
        if (img != null) {
            try {
                val plane = img.planes[0]
                val pixelStride = plane.pixelStride
                val rowPadding = plane.rowStride - pixelStride * img.width
                val wide = Bitmap.createBitmap(img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888)
                wide.copyPixelsFromBuffer(plane.buffer)
                last = if (wide.width != img.width) {
                    Bitmap.createBitmap(wide, 0, 0, img.width, img.height).also { wide.recycle() }
                } else wide
            } finally {
                img.close()
            }
        }
        return last
    }

    fun release() {
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        thread.quitSafely()
        last = null
    }

    companion object { private const val MAX_SIDE = 1280f }
}
