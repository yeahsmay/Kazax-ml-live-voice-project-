package id.kazax.mlassistant.ai

import id.kazax.mlassistant.core.AppSettings
import id.kazax.mlassistant.core.VisionProvider

/** Tambahkan penyedia AI baru di sini (satu baris). */
object ProviderFactory {
    fun create(s: AppSettings): VisionProvider? {
        if (s.apiKey.isBlank()) return null
        return when (s.provider) {
            AppSettings.PROVIDER_OPENAI -> {
                val m = s.effectiveModel()
                if (m.isBlank() || s.baseUrl.isBlank()) null else OpenAiCompatProvider(s.baseUrl, s.apiKey, m)
            }
            else -> GeminiProvider(s.apiKey, s.effectiveModel())
        }
    }
}
