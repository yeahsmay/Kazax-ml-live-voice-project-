package id.kazax.mlassistant.ai

import id.kazax.mlassistant.core.Advice
import org.json.JSONObject

object AdviceParser {
    fun parse(raw: String): Advice? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            try {
                val o = JSONObject(raw.substring(start, end + 1))
                val tags = mutableListOf<String>()
                o.optJSONArray("tags")?.let { for (i in 0 until it.length()) tags.add(it.optString(i)) }
                return Advice(
                    o.optString("say", "").trim(),
                    o.optInt("priority", 0).coerceIn(0, 3),
                    tags,
                    o.optString("state", "").trim()
                )
            } catch (_: Exception) {
            }
        }
        val t = raw.trim()
        return if (t.isNotEmpty() && t.length <= 160) Advice(t, 1, emptyList(), "") else null
    }
}
