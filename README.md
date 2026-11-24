# kazax asisten ml Live Voice

Pelatih suara Indonesia untuk Mobile Legends. Membaca layar lewat MediaProjection, mengirim frame
(+ crop minimap) ke AI vision secara berkala, lalu mengucapkan arahan dengan TTS bawaan Android.
**Tidak mengendalikan game**: tidak memakai Accessibility Service, tidak mengirim sentuhan.

## Kompatibilitas
- Android 7.0 (API 24) ke atas, APK universal (ARM & x86, tanpa kode native).
- Jendela melayang memakai `TYPE_APPLICATION_OVERLAY` (Android 8+) / `TYPE_PHONE` (Android 7).
- Android 14+: foreground service bertipe `mediaProjection` sudah ditangani.

## Struktur
```
app/src/main/java/id/kazax/mlassistant/
  core/     AppSettings, Models (Advice, VisionProvider), AnalysisEngine (loop & aturan bicara)
  ai/       GeminiProvider, OpenAiCompatProvider, ProviderFactory, PromptRepository, AdviceParser, Http
  capture/  ScreenCapturer (MediaProjection), FrameUtils (kompres, crop minimap, deteksi layar sama)
  tts/      VoiceSpeaker (TextToSpeech id-ID)
  overlay/  FloatingOverlay (gelembung melayang)
  service/  AssistantService (foreground service perangkai modul)
  ui/       MainActivity (pengaturan & tombol mulai)
app/src/main/assets/prompt_default.txt   <- "otak" perilaku AI
version.properties                        <- sumber tunggal versi
.github/workflows/build.yml               <- build APK otomatis
```

## Cara mendapat APK
**A. Tanpa PC (GitHub Actions) — paling mudah**
1. Buat repo GitHub (private boleh), unggah seluruh isi folder ini (termasuk folder `.github`).
2. Tab *Actions* → *Build APK* → *Run workflow*. Tunggu ±5 menit.
3. Buka run yang selesai → *Artifacts* → unduh `kazax-apk` (zip berisi `kazax-ml-live-voice.apk`).
4. Ekstrak, pasang di HP (izinkan "pasang dari sumber tidak dikenal").

**B. Android Studio**: *Open* folder ini → tunggu Gradle sync → *Build > Build APK(s)*.
APK ada di `app/build/outputs/apk/release/app-release.apk`.

**C. Command line** (JDK 17 + Android SDK 34 + Gradle 8.7): `gradle assembleRelease`

APK ditandatangani `kazax-release.jks` yang ikut di proyek, jadi versi baru bisa **menimpa** versi lama
tanpa uninstall. Untuk dibagikan ke publik, buat keystore sendiri dan ganti di `app/build.gradle.kts`.

## Memakai
1. Buka app → pilih layanan AI → isi API key → *Tes koneksi AI* → *Tes suara*
   (jika gagal: *Pasang suara Indonesia*).
2. *Mulai asisten* → beri izin tampil-di-atas-aplikasi, notifikasi, dan rekam layar.
3. *Buka Mobile Legends*. Geser gelembung kazax ke tempat yang tidak menutupi tombol.
   Ketuk judul gelembung untuk ciut/lebar. Tombol: Jeda, Analisis (paksa sekarang), Bisu, Stop.
4. Atur *Ukuran minimap* dan posisi kiri/kanan sampai crop pas dengan minimap (pengaturan HUD ML berbeda).
5. Pakai headset agar suara pelatih tidak bercampur dengan suara game.

**Mode otomatis**: bicara untuk tips rutin sampai darurat. **Mode sinyal**: hanya bicara untuk
peringatan penting/darurat (musuh hilang, gank, team fight, objektif) atau saat tombol *Analisis* ditekan.

## API & biaya
- **Gemini API** (disarankan): buat key di https://aistudio.google.com/apikey. Model bawaan
  `gemini-3.5-flash-lite` (bisa diganti di app tanpa build ulang). Tier gratis ada tapi terbatas dan data
  bisa dipakai Google untuk perbaikan produk; tier berbayar tidak.
- **OpenAI-kompatibel**: isi Base URL, key, dan nama model yang mendukung input gambar.
- **TTS**: bawaan Android, gratis.
- Perkiraan kasar: 1 analisis ≈ 2–3 ribu token input (2 gambar + prompt) + ratusan token output. Dengan harga
  tier murah (kisaran USD 0,3 / 1,5 per 1 juta token input/output) ±USD 0,002 per analisis; interval 6 dtk
  ≈ 300–400 analisis/jam ≈ USD 0,5–1,5 per jam bermain. **Cek harga terbaru** di
  https://ai.google.dev/gemini-api/docs/pricing sebelum memakai berjam-jam. Layar yang tidak berubah
  tidak dikirim (hemat), dan frekuensi bisa dinaikkan ke 15–30 dtk.
- Data layar dikirim ke penyedia AI yang Anda pilih. Jangan tampilkan info pribadi saat dipakai.

## Keterbatasan (jujur)
- **Bukan real-time.** Setiap analisis butuh ±2–8 detik (jaringan + model). Arahan bersifat "beberapa detik
  yang lalu", cocok untuk rotasi/objektif/waspada gank, **bukan** untuk reaksi frame-per-frame (skill, dodge).
- **Membaca minimap & mengenali hero adalah estimasi AI vision**, bukan deteksi pasti. Ikon kecil bisa salah
  baca. Prompt menyuruh AI diam jika tidak yakin, tetapi kesalahan tetap mungkin. Alternatif terbaik jangka
  panjang: model deteksi ikon hero on-device (TFLite) yang dilatih dari tangkapan layar ML — bisa ditambah sebagai
  modul `VisionProvider` baru tanpa mengubah modul lain.
- "Musuh hilang" dideteksi dengan membandingkan ringkasan kondisi antar-analisis, bukan tracking piksel.
- Game/HP tertentu bisa memblokir tangkap layar (layar hitam) atau mematikan overlay saat hemat daya;
  matikan optimasi baterai untuk kazax jika gelembung/service berhenti.
- Android 14+ meminta izin rekam layar **setiap kali** asisten dimulai (aturan sistem).
- Tidak ada pembaruan APK otomatis dari dalam app. Update = pasang APK baru (tanda tangan sama).
- Aturan Moonton soal alat pihak ketiga bisa berubah; gunakan atas risiko sendiri, hindari mode ranked
  jika ragu. App ini tidak memodifikasi game dan hanya memberi saran.
- Kode ini belum pernah saya kompilasi/uji di HP. Jika build gagal, kirim log error-nya untuk diperbaiki.

## Update tanpa bangun ulang dari nol
- **Perilaku AI**: edit `assets/prompt_default.txt`, atau host file teks serupa (mis. GitHub raw) dan isi
  *URL prompt jarak jauh* di app → prompt diperbarui saat asisten dimulai, tanpa APK baru.
- **Model AI**: ganti nama model di app.
- **Penyedia AI baru**: buat kelas `VisionProvider`, daftarkan di `ProviderFactory`.
- **Fitur baru**: tambah modul/paket baru; `AnalysisEngine` hanya bergantung pada interface.
- **Versi**: ubah `version.properties` (versionCode otomatis), tulis `CHANGELOG.md`, lalu
  `git tag v1.1.0 && git push --tags` → GitHub membuat Release berisi APK.
