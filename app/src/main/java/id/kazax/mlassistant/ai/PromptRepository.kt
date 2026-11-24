package id.kazax.mlassistant.ai

import android.content.Context
import id.kazax.mlassistant.core.AppSettings

/**
 * Prompt sistem. Bawaan ada di assets/prompt_default.txt.
 * Jika "URL prompt jarak jauh" diisi, prompt diunduh saat asisten dimulai -> perilaku AI
 * bisa diperbarui tanpa membangun ulang APK.
 */
class PromptRepository(private val ctx: Context, private val s: AppSettings) {
    private val default: String by lazy {
        ctx.assets.open("prompt_default.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Mengembalikan pesan status, atau null bila tidak ada URL. */
    fun refreshRemote(): String? {
        val url = s.remotePromptUrl
        if (url.isBlank()) return null
        return try {
            val t = Http.getText(url)
            if (t.length in 200..20000) {
                s.remotePromptCache = t
                "Prompt jarak jauh diperbarui"
            } else "Prompt jarak jauh tidak valid, pakai cache"
        } catch (e: Exception) {
            "Gagal unduh prompt, pakai cache/bawaan"
        }
    }

    fun systemPrompt(): String {
        val base = if (s.remotePromptUrl.isNotBlank() && s.remotePromptCache.isNotBlank()) s.remotePromptCache else default
        val notes = s.notes
        return if (notes.isBlank()) base else "$base\n\nINFO PEMAIN (dari pengguna): $notes"
    }
}
