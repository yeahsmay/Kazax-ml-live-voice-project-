package id.kazax.mlassistant.ai

import android.util.Base64
import id.kazax.mlassistant.core.FramePack
import id.kazax.mlassistant.core.VisionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Endpoint /chat/completions yang kompatibel OpenAI (OpenAI, OpenRouter, dsb.) dengan model yang mendukung gambar. */
class OpenAiCompatProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : VisionProvider {
    override val name = "OpenAI-kompatibel"

    private fun image(bytes: ByteArray) = JSONObject().put("type", "image_url").put(
        "image_url",
        JSONObject().put("url", "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
    )

    override suspend fun analyze(system: String, userText: String, frame: FramePack): String =
        withContext(Dispatchers.IO) {
            val content = JSONArray().put(JSONObject().put("type", "text").put("text", userText)).put(image(frame.full))
            frame.minimap?.let {
                content.put(JSONObject().put("type", "text").put("text", "Gambar berikut adalah crop minimap:"))
                content.put(image(it))
            }
            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0.4)
                .put("max_tokens", 400)
                .put(
                    "messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", content))
                )
            val url = baseUrl.trimEnd('/') + "/chat/completions"
            val resp = JSONObject(Http.postJson(url, mapOf("Authorization" to "Bearer $apiKey"), body.toString()))
            resp.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?: throw IOException("Respons tanpa teks")
        }
}
