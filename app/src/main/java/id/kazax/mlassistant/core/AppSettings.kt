package id.kazax.mlassistant.core

import android.content.Context

/** Semua pengaturan pengguna. Perubahan langsung berlaku saat asisten berjalan. */
class AppSettings(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("kazax_settings", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun int(k: String, d: Int) = sp.getInt(k, d)
    private fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var provider: String get() = str("provider", PROVIDER_GEMINI); set(v) = putStr("provider", v)
    var apiKey: String get() = str("api_key", ""); set(v) = putStr("api_key", v.trim())
    var model: String get() = str("model", ""); set(v) = putStr("model", v.trim())
    var baseUrl: String get() = str("base_url", "https://api.openai.com/v1"); set(v) = putStr("base_url", v.trim())

    var mode: String get() = str("mode", MODE_AUTO); set(v) = putStr("mode", v)
    var intervalSec: Int get() = int("interval", 6); set(v) = putInt("interval", v)
    var volumePct: Int get() = int("volume", 100); set(v) = putInt("volume", v)
    var ratePct: Int get() = int("rate", 110); set(v) = putInt("rate", v)

    var minimapEnabled: Boolean get() = bool("mm_on", true); set(v) = putBool("mm_on", v)
    var minimapSizePct: Int get() = int("mm_size", 36); set(v) = putInt("mm_size", v)
    var minimapRight: Boolean get() = bool("mm_right", false); set(v) = putBool("mm_right", v)

    var notes: String get() = str("notes", ""); set(v) = putStr("notes", v.trim())
    var remotePromptUrl: String get() = str("remote_prompt_url", ""); set(v) = putStr("remote_prompt_url", v.trim())
    var remotePromptCache: String get() = str("remote_prompt_cache", ""); set(v) = putStr("remote_prompt_cache", v)

    fun effectiveModel(): String =
        model.ifBlank { if (provider == PROVIDER_GEMINI) DEFAULT_GEMINI_MODEL else "" }

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI = "openai"
        const val MODE_AUTO = "auto"
        const val MODE_SIGNAL = "signal"
        // Nama model berubah dari waktu ke waktu: bisa diganti di layar pengaturan tanpa build ulang.
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"
    }
}
