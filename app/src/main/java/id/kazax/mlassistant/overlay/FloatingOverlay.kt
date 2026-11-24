package id.kazax.mlassistant.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Jendela melayang kecil: bisa digeser, ketuk judul untuk ciut/lebar. Tidak menyentuh kontrol game. */
class FloatingOverlay(private val ctx: Context, private val cb: Callbacks) {
    interface Callbacks {
        fun onTogglePause(): Boolean   // true = sedang dijeda
        fun onAnalyzeNow()
        fun onToggleMute(): Boolean    // true = dibisukan
        fun onStop()
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private lateinit var title: TextView
    private lateinit var advice: TextView
    private lateinit var buttons: LinearLayout
    private var expanded = true

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(72) }

    private fun pill(label: String, onClick: (TextView) -> Unit): TextView = TextView(ctx).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 11f
        setPadding(dp(9), dp(5), dp(9), dp(5))
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x33FFFFFF) }
        setOnClickListener { onClick(this) }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(5) }
    }

    fun show() {
        if (root != null) return
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xD90F172A.toInt()) }
        }
        title = TextView(ctx).apply {
            text = "kazax · siap"; setTextColor(0xFF5EEAD4.toInt()); textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        advice = TextView(ctx).apply {
            text = "Menunggu analisis pertama…"; setTextColor(0xFFE2E8F0.toInt()); textSize = 12f
            maxWidth = dp(220); maxLines = 4; setPadding(0, dp(4), 0, dp(4))
        }
        buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(pill("Jeda") { v -> v.text = if (cb.onTogglePause()) "Lanjut" else "Jeda" })
        buttons.addView(pill("Analisis") { cb.onAnalyzeNow() })
        buttons.addView(pill("Bisu") { v -> v.text = if (cb.onToggleMute()) "Suara" else "Bisu" })
        buttons.addView(pill("Stop") { cb.onStop() })
        r.addView(title); r.addView(advice); r.addView(buttons)

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = params.x; oy = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (moved) { params.x = ox + dx; params.y = oy + dy; try { wm.updateViewLayout(r, params) } catch (_: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) toggle(); true }
                else -> false
            }
        }
        try { wm.addView(r, params); root = r } catch (_: Exception) { root = null }
    }

    private fun toggle() {
        expanded = !expanded
        val vis = if (expanded) View.VISIBLE else View.GONE
        advice.visibility = vis; buttons.visibility = vis
    }

    fun setStatus(text: String) = main.post { if (root != null) title.text = "kazax · $text" }

    fun setAdvice(text: String, priority: Int) = main.post {
        if (root == null) return@post
        if (text.isBlank()) return@post
        advice.text = text
        advice.setTextColor(when (priority) { 3 -> 0xFFFCA5A5.toInt(); 2 -> 0xFFFDE68A.toInt(); else -> 0xFFE2E8F0.toInt() })
    }

    fun hide() = main.post {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
    }
}
