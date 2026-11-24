package id.kazax.mlassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import id.kazax.mlassistant.BuildConfig
import id.kazax.mlassistant.ai.ProviderFactory
import id.kazax.mlassistant.core.AppSettings
import id.kazax.mlassistant.core.FramePack
import id.kazax.mlassistant.service.AssistantService
import id.kazax.mlassistant.tts.VoiceSpeaker
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var s: AppSettings
    private lateinit var etKey: EditText
    private lateinit var etModel: EditText
    private lateinit var etBase: EditText
    private lateinit var etNotes: EditText
    private lateinit var etRemote: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: MaterialButton
    private var voiceTest: VoiceSpeaker? = null
    private var notifAsked = false

    private val projLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_START)
                    .putExtra(AssistantService.EXTRA_CODE, r.resultCode)
                    .putExtra(AssistantService.EXTRA_DATA, data)
            )
            toast("Aktif. Buka Mobile Legends — gelembung kazax akan melayang di atas game.")
            btnStart.postDelayed({ refreshUi() }, 700)
        } else toast("Izin rekam layar ditolak")
    }
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { onStartClicked() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = AppSettings(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(32)) }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply { text = "kazax asisten ml Live Voice"; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        col.addView(TextView(this).apply { text = "v${BuildConfig.VERSION_NAME} · pelatih suara Mobile Legends (tidak mengendalikan game)"; textSize = 12f; setPadding(0, dp(2), 0, dp(10)) })
        tvStatus = TextView(this).apply { textSize = 13f; setPadding(0, 0, 0, dp(8)) }
        col.addView(tvStatus)

        btnStart = MaterialButton(this).apply { setOnClickListener { onStartClicked() } }
        col.addView(btnStart)
        col.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Buka Mobile Legends"; setOnClickListener { openMl() }
        })

        // --- AI ---
        col.addView(section("Layanan AI"))
        val spProvider = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Google Gemini", "OpenAI-kompatibel"))
            setSelection(if (s.provider == AppSettings.PROVIDER_OPENAI) 1 else 0)
        }
        col.addView(spProvider)
        etKey = edit("API key", s.apiKey, password = true)
        etModel = edit("Model (kosong = bawaan untuk Gemini)", s.model)
        etBase = edit("Base URL (OpenAI-kompatibel)", s.baseUrl)
        etBase.visibility = if (s.provider == AppSettings.PROVIDER_OPENAI) View.VISIBLE else View.GONE
        spProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                s.provider = if (pos == 1) AppSettings.PROVIDER_OPENAI else AppSettings.PROVIDER_GEMINI
                etBase.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(etKey); col.addView(etModel); col.addView(etBase)
        col.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Tes koneksi AI"; setOnClickListener { testAi() }
        })

        // --- Mode ---
        col.addView(section("Mode"))
        val rg = RadioGroup(this)
        val rAuto = RadioButton(this).apply { id = View.generateViewId(); text = "Otomatis — bicara terus sesuai situasi" }
        val rSig = RadioButton(this).apply { id = View.generateViewId(); text = "Sinyal — bicara hanya saat penting/darurat" }
        rg.addView(rAuto); rg.addView(rSig)
        rg.check(if (s.mode == AppSettings.MODE_SIGNAL) rSig.id else rAuto.id)
        rg.setOnCheckedChangeListener { _, id -> s.mode = if (id == rSig.id) AppSettings.MODE_SIGNAL else AppSettings.MODE_AUTO }
        col.addView(rg)

        // --- Suara & frekuensi ---
        col.addView(section("Suara & frekuensi"))
        col.addView(slider("Frekuensi analisis", 3, 30, 1, s.intervalSec, { "setiap $it detik (+ waktu respons AI)" }) { s.intervalSec = it })
        col.addView(slider("Volume suara", 0, 100, 5, s.volumePct, { "$it%" }) { s.volumePct = it })
        col.addView(slider("Kecepatan bicara", 60, 160, 10, s.ratePct, { "${it / 100f}x" }) { s.ratePct = it })
        val row = LinearLayout(this)
        row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Tes suara"; setOnClickListener { testVoice() }
        })
        row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Pasang suara Indonesia"
            setOnClickListener { try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } catch (e: Exception) { toast("Buka Pengaturan > Bahasa > Text-to-speech") } }
        })
        col.addView(row)

        // --- Minimap ---
        col.addView(section("Minimap"))
        col.addView(MaterialSwitch(this).apply {
            text = "Kirim crop minimap ke AI (lebih akurat)"; isChecked = s.minimapEnabled
            setOnCheckedChangeListener { _, c -> s.minimapEnabled = c }
        })
        col.addView(MaterialSwitch(this).apply {
            text = "Minimap di pojok kanan atas"; isChecked = s.minimapRight
            setOnCheckedChangeListener { _, c -> s.minimapRight = c }
        })
        col.addView(slider("Ukuran minimap", 20, 60, 2, s.minimapSizePct, { "$it% tinggi layar" }) { s.minimapSizePct = it })

        // --- Lanjutan ---
        col.addView(section("Lanjutan"))
        etNotes = edit("Info untuk AI (hero, role, lane)", s.notes)
        etRemote = edit("URL prompt jarak jauh (opsional)", s.remotePromptUrl)
        col.addView(etNotes); col.addView(etRemote)
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(0xFF00897B.toInt()); setPadding(0, dp(18), 0, dp(4))
    }

    private fun edit(hint: String, value: String, password: Boolean = false) = EditText(this).apply {
        this.hint = hint; setText(value); setSingleLine(true)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun slider(label: String, from: Int, to: Int, step: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val v = (((value - from) / step.toFloat()).roundToInt() * step + from).coerceIn(from, to)
        val tv = TextView(this).apply { text = "$label: ${fmt(v)}"; textSize = 13f }
        val sl = Slider(this).apply {
            valueFrom = from.toFloat(); valueTo = to.toFloat(); stepSize = step.toFloat(); this.value = v.toFloat()
            addOnChangeListener { _, x, _ -> val i = x.roundToInt(); tv.text = "$label: ${fmt(i)}"; onChange(i) }
        }
        box.addView(tv); box.addView(sl)
        return box
    }

    private fun saveFields() {
        s.apiKey = etKey.text.toString(); s.model = etModel.text.toString(); s.baseUrl = etBase.text.toString()
        s.notes = etNotes.text.toString(); s.remotePromptUrl = etRemote.text.toString()
    }

    override fun onPause() { saveFields(); super.onPause() }
    override fun onResume() { super.onResume(); refreshUi() }
    override fun onDestroy() { voiceTest?.shutdown(); super.onDestroy() }

    private fun refreshUi() {
        val overlayOk = android.provider.Settings.canDrawOverlays(this)
        btnStart.text = if (AssistantService.running) "Hentikan asisten" else "Mulai asisten"
        tvStatus.text = (if (AssistantService.running) "● Asisten aktif\n" else "○ Asisten mati\n") +
            (if (overlayOk) "✓ Izin jendela melayang\n" else "✗ Izin jendela melayang belum diberikan (akan diminta saat Mulai)\n") +
            (if (s.apiKey.isNotBlank()) "✓ API key terisi" else "✗ API key belum diisi")
    }

    private fun onStartClicked() {
        saveFields()
        if (AssistantService.running) {
            startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_STOP))
            btnStart.postDelayed({ refreshUi() }, 500)
            return
        }
        if (ProviderFactory.create(s) == null) { toast("Lengkapi API key (dan model untuk OpenAI-kompatibel) dulu"); return }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            toast("Aktifkan 'Tampil di atas aplikasi lain' untuk kazax, lalu tekan Mulai lagi")
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !notifAsked &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifAsked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun openMl() {
        for (pkg in listOf("com.mobile.legends", "com.mobilelegends.mi", "com.mobilelegends.hwag")) {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) { startActivity(i); return }
        }
        toast("Mobile Legends tidak ditemukan di HP ini")
    }

    private fun testVoice() {
        voiceTest?.shutdown()
        voiceTest = VoiceSpeaker(this, s) { ok, msg ->
            if (ok) voiceTest?.speak("Halo, aku kazax. Musuh hilang dari minimap, hati-hati gank.", true) else toast(msg)
        }
    }

    private fun testAi() {
        saveFields()
        val p = ProviderFactory.create(s) ?: run { toast("Lengkapi API key/model dulu"); return }
        toast("Menguji koneksi…")
        lifecycleScope.launch {
            try {
                val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
                val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
                p.analyze("Balas hanya JSON: {\"say\":\"\",\"priority\":0,\"tags\":[],\"state\":\"tes\"}", "Tes koneksi.", FramePack(out.toByteArray(), null))
                toast("Koneksi ${p.name} OK ✓")
            } catch (e: Exception) { toast("Gagal: ${e.message?.take(140)}") }
        }
    }
}
