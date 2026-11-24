package id.kazax.mlassistant.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Int = 30000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return readBody(c)
        } finally {
            c.disconnect()
        }
    }

    fun getText(url: String, timeoutMs: Int = 15000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            return readBody(c)
        } finally {
            c.disconnect()
        }
    }

    private fun readBody(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IOException("HTTP $code ${text.take(160)}")
        return text
    }
}
