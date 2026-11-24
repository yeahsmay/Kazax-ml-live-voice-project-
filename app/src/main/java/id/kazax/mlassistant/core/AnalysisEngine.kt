package id.kazax.mlassistant.core

import android.os.SystemClock
import id.kazax.mlassistant.ai.AdviceParser
import id.kazax.mlassistant.ai.ProviderFactory
import id.kazax.mlassistant.ai.PromptRepository
import id.kazax.mlassistant.capture.FrameUtils
import id.kazax.mlassistant.capture.ScreenCapturer
import id.kazax.mlassistant.tts.VoiceSpeaker
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Loop utama: ambil layar -> (lewati jika tidak berubah) -> kirim ke AI -> parse -> ucapkan.
 * Mode otomatis: bicara untuk priority >= 1. Mode sinyal: hanya priority >= 2 (atau saat ditekan manual).
 */
class AnalysisEngine(
    private val settings: AppSettings,
    private val capturer: ScreenCapturer,
    private val speaker: VoiceSpeaker,
    private val prompts: PromptRepository,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(text: String)
        fun onAdvice(advice: Advice, spoken: Boolean)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val kick = Channel<Unit>(Channel.CONFLATED)
    @Volatile var paused = false

    private val history = ArrayDeque<String>()
    private var lastSig: IntArray? = null
    private var skipped = 0
    private var calls = 0
    private var errorStreak = 0
    private var lastSpoken = ""
    private var lastSpokenAt = 0L
    private var lastCallAt = 0L

    fun start() {
        scope.launch {
            prompts.refreshRemote()?.let { listener.onStatus(it) }
            loop()
        }
    }

    fun stop() = scope.cancel()

    fun analyzeNow() { kick.trySend(Unit) }

    private suspend fun loop() {
        var manual = false
        while (currentCoroutineContext().isActive) {
            if (!paused || manual) runOnce(manual)
            val wait = settings.intervalSec.coerceIn(3, 60) * 1000L + errorStreak.coerceAtMost(4) * 3000L
            manual = withTimeoutOrNull(wait) { kick.receive(); true } ?: false
        }
    }

    private suspend fun runOnce(manual: Boolean) {
        val bmp = capturer.grab() ?: run { listener.onStatus("Menunggu gambar layar…"); return }
        val sig = FrameUtils.signature(bmp)
        val prev = lastSig
        if (!manual && prev != null && FrameUtils.similar(prev, sig) && skipped < 3) {
            skipped++
            listener.onStatus("Layar tidak berubah (hemat kuota)")
            return
        }
        skipped = 0
        lastSig = sig
        val provider = ProviderFactory.create(settings) ?: run {
            listener.onStatus("API key/model belum lengkap"); return
        }
        val full = FrameUtils.toJpeg(bmp, 800, 60)
        val mini = if (settings.minimapEnabled) {
            val crop = FrameUtils.cropMinimap(bmp, settings)
            FrameUtils.toJpeg(crop, 400, 70).also { crop.recycle() }
        } else null

        listener.onStatus("Menganalisis…")
        val now = SystemClock.elapsedRealtime()
        val gap = if (lastCallAt == 0L) 0 else (now - lastCallAt) / 1000
        lastCallAt = now
        val userText = buildString {
            append("Analisis tangkapan layar sekarang. Gambar pertama = layar penuh.")
            if (mini != null) append(" Gambar kedua = crop minimap.")
            if (gap > 0) append(" Selang sejak analisis sebelumnya: $gap detik.")
            if (history.isNotEmpty()) {
                append("\nRingkasan sebelumnya (lama ke baru):")
                history.forEachIndexed { i, h -> append("\n${i + 1}) $h") }
            }
        }
        try {
            val raw = provider.analyze(prompts.systemPrompt(), userText, FramePack(full, mini))
            calls++
            errorStreak = 0
            val advice = AdviceParser.parse(raw) ?: run { listener.onStatus("Balasan AI tidak terbaca"); return }
            if (advice.state.isNotBlank()) {
                history.addLast(advice.state)
                while (history.size > 5) history.removeFirst()
            }
            handle(advice, manual)
            listener.onStatus("Aktif · $calls analisis")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorStreak++
            listener.onStatus("Error: ${e.message?.take(90)}")
        }
    }

    private fun handle(advice: Advice, manual: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val minPriority = if (settings.mode == AppSettings.MODE_SIGNAL) 2 else 1
        var spoken = false
        if (advice.say.isNotBlank() && (manual || advice.priority >= minPriority)) {
            val urgent = advice.priority >= 3
            val dup = advice.say.equals(lastSpoken, true) && now - lastSpokenAt < 25_000
            val tooSoon = now - lastSpokenAt < 2_500 && !urgent
            val busy = speaker.isSpeaking && !urgent && !manual
            if ((manual || !dup) && !tooSoon && !busy) {
                spoken = speaker.speak(advice.say, flush = urgent || manual)
                if (spoken) { lastSpoken = advice.say; lastSpokenAt = now }
            }
        } else if (manual && advice.say.isBlank()) {
            speaker.speak("Belum ada saran khusus.", flush = true)
        }
        listener.onAdvice(advice, spoken)
    }
}
