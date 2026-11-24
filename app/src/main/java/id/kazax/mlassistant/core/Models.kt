package id.kazax.mlassistant.core

/** Hasil analisis AI. priority: 0 diam, 1 tips, 2 penting, 3 darurat. */
data class Advice(val say: String, val priority: Int, val tags: List<String>, val state: String)

/** Gambar yang dikirim ke AI: layar penuh + (opsional) crop minimap, keduanya JPEG. */
class FramePack(val full: ByteArray, val minimap: ByteArray?)

/** Titik ekstensi: tambah penyedia AI baru cukup dengan mengimplementasikan interface ini. */
interface VisionProvider {
    val name: String
    /** Mengembalikan teks mentah dari model (idealnya JSON). Dipanggil dari coroutine. */
    suspend fun analyze(system: String, userText: String, frame: FramePack): String
}
