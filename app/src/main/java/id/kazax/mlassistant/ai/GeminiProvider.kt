package id.kazax.mlassistant.ai

import android.util.Base64
import id.kazax.mlassistant.core.FramePack
import id.kazax.mlassistant.core.VisionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Google Gemini API (generateContent) dengan input gambar inline. */
class GeminiProvider(private val apiKey: String, private val model: String) : VisionProvider {
    override val name = "Gemini"

    private fun image(bytes: ByteArray) = JSONObject().put(
        "inline_data",
        JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
    )

    override suspend fun analyze(system: String, userText: String, frame: FramePack): String =
        withContext(Dispatchers.IO) {
            val parts = JSONArray().put(JSONObject().put("text", userText)).put(image(frame.full))
            frame.minimap?.let {
                parts.put(JSONObject().put("text", "Gambar berikut adalah crop minimap:"))
                parts.put(image(it))
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                .put(
                    "generationConfig",
                    JSONObject().put("temperature", 0.4).put("maxOutputTokens", 1024)
                        .put("responseMimeType", "application/json")
                )
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val resp = JSONObject(Http.postJson(url, mapOf("x-goog-api-key" to apiKey), body.toString()))
            val cand = resp.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IOException("Respons kosong atau diblokir")
            val ps = cand.optJSONObject("content")?.optJSONArray("parts") ?: throw IOException("Respons tanpa teks")
            val sb = StringBuilder()
            for (i in 0 until ps.length()) sb.append(ps.optJSONObject(i)?.optString("text", "") ?: "")
            sb.toString()
        }
}
