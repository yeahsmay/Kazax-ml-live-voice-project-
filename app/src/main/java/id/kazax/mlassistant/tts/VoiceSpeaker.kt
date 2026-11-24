package id.kazax.mlassistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import id.kazax.mlassistant.core.AppSettings
import java.util.Locale

/** Text-to-speech bawaan Android (gratis, offline jika data suara Indonesia terpasang). */
class VoiceSpeaker(
    ctx: Context,
    private val settings: AppSettings,
    private val onReady: (ok: Boolean, message: String) -> Unit
) {
    private lateinit var tts: TextToSpeech
    @Volatile private var ready = false
    @Volatile var muted = false

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale("id", "ID"))
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                onReady(ready, if (ready) "Suara Indonesia siap" else "Data suara Indonesia belum terpasang")
            } else onReady(false, "Mesin suara gagal dimulai")
        }
    }

    val isSpeaking: Boolean get() = ready && try { tts.isSpeaking } catch (_: Exception) { false }

    /** Mengembalikan true jika ucapan benar-benar diantrikan. */
    fun speak(text: String, flush: Boolean): Boolean {
        if (!ready || muted || text.isBlank()) return false
        tts.setSpeechRate((settings.ratePct / 100f).coerceIn(0.5f, 2f))
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (settings.volumePct / 100f).coerceIn(0f, 1f))
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        return tts.speak(text, mode, params, "kz" + System.nanoTime()) == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
    }
}
