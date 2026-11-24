package id.kazax.mlassistant.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import id.kazax.mlassistant.R
import id.kazax.mlassistant.ai.PromptRepository
import id.kazax.mlassistant.capture.ScreenCapturer
import id.kazax.mlassistant.core.Advice
import id.kazax.mlassistant.core.AnalysisEngine
import id.kazax.mlassistant.core.AppSettings
import id.kazax.mlassistant.overlay.FloatingOverlay
import id.kazax.mlassistant.tts.VoiceSpeaker
import id.kazax.mlassistant.ui.MainActivity

/** Foreground service (tipe mediaProjection) yang merangkai semua modul. */
class AssistantService : Service(), AnalysisEngine.Listener, FloatingOverlay.Callbacks {

    companion object {
        const val ACTION_START = "id.kazax.mlassistant.START"
        const val ACTION_STOP = "id.kazax.mlassistant.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "kazax_live"
        private const val NOTIF_ID = 42
        @Volatile var running = false
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var speaker: VoiceSpeaker? = null
    private var overlay: FloatingOverlay? = null
    private var engine: AnalysisEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intent = intent ?: run { stopSelf(); return START_NOT_STICKY }
        when (intent.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                goForeground()
                if (running) return START_NOT_STICKY
                val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
                begin(code, data)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val settings = AppSettings(this)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = try { mpm.getMediaProjection(code, data) } catch (e: Exception) { null }
        if (proj == null) { stopSelf(); return }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopSelf() } }
        }, main)

        val cap = ScreenCapturer(this, proj).also { it.start() }
        capturer = cap
        val ov = FloatingOverlay(this, this).also { it.show() }
        overlay = ov
        val sp = VoiceSpeaker(this, settings) { _, msg -> ov.setStatus(msg) }
        speaker = sp
        engine = AnalysisEngine(settings, cap, sp, PromptRepository(this, settings), this).also { it.start() }
        running = true
    }

    private fun goForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Asisten aktif", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AssistantService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("kazax asisten ml aktif")
            .setContentText("Membaca layar & memberi arahan suara")
            .setContentIntent(open)
            .addAction(0, "Berhenti", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIF_ID, n)
    }

    // --- AnalysisEngine.Listener ---
    override fun onStatus(text: String) { overlay?.setStatus(text) }
    override fun onAdvice(advice: Advice, spoken: Boolean) { overlay?.setAdvice(advice.say, advice.priority) }

    // --- FloatingOverlay.Callbacks ---
    override fun onTogglePause(): Boolean {
        val e = engine ?: return false
        e.paused = !e.paused
        return e.paused
    }
    override fun onAnalyzeNow() { engine?.analyzeNow() }
    override fun onToggleMute(): Boolean {
        val s = speaker ?: return false
        s.muted = !s.muted
        return s.muted
    }
    override fun onStop() { stopSelf() }

    override fun onDestroy() {
        running = false
        engine?.stop()
        overlay?.hide()
        speaker?.shutdown()
        capturer?.release()
        try { projection?.stop() } catch (_: Exception) {}
        super.onDestroy()
    }
}
