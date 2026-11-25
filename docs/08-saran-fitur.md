# 08 — Saran Fitur Lain (di luar brief)

Sasaran: **tambah nilai tanpa merusak janji "cepat"**. Semua fitur di sini diurutkan berdasarkan rasio *impact/effort* untuk produk share-target yang mengambang. Fitur yang membuat app lambat / butuh login / terlalu banyak layar saya letakkan paling bawah.

## Kategori A — murah, sangat terasa, langsung masuk MVP+

### A1. Duplicate & "Edit lagi"
Simpan `EditorState` terakhir (atau 10 terakhir) di Room → buka app, lihat "Sesi terakhir", tap untuk lanjut edit non-destruktif. Ini yang membedakan "sekali pakai" dari "app yang saya buka tiap hari". Murah karena state sudah ada.

### A2. Share → WhatsApp dengan cepat
Setelah Simpan/Share, tampilkan 3 shortcut: **WhatsApp, Telegram, Instagram** (icon). Jangan buka share sheet penuh untuk share ulang ke app yang sama; ini polished. Pakai `ShareCompat` + explicit package kalau terpasang (fallback ke chooser).

### A3. Copy ke clipboard
Tombol "Salin gambar" (`ClipboardManager.setPrimaryClip(ClipDescription with URI/bitmap)`). Sangat sering diminta untuk share ke chat.

### A4. Simpan sebagai "screenshot-ready"
Tombol Save dengan pilihan: Galeri (MediaStore) / Files / "Salin". Ingat default pilihan user.

### A5. Watermark toggle (kecil)
Watermark halus "made with FloatPic" sebagai *opt-in* (Pro = hilangkan). Hanya sudut kanan bawah, opacity 40%. Banyak app tumbuh dari ini — asal tidak memaksa.

### A6. Grid & guides
Grid 3×3/rule of thirds + level indicator (horizon). 1 file overlay, 100 baris kode. Ini yang dicari fotografer.

### A7. Undo/Redo
Karena state immutable → cukup stack. Wajib, dan biasanya terlupakan di app kecil.

### A8. Ringkasan sebelum share
Bottom card kecil: rasio, resolusi, ukuran file/kualitas → tombol Share. Mencegah user share file 0 KB karena bug.

### A9. "Sembunyikan UI saat preview"
Tap sekali → UI fade out (toolbar + chips), tap lagi kembali. Sering dipakai untuk cek hasil akhir; terasa seperti app pro.

### A10. Auto-save draft saat gesture berhenti
Kalau app mati / user kembali ke chat dan balik lagi, state utuh.

## Kategori B — pembeda produk (pilih 1-2, jangan semua)

### B1. Floating bubble (bila ambil Level L2)
Editor menjadi jendela overlay yang bisa dikecilkan menjadi bubble melayang di atas app lain; tap bubble → balik ke editor. Ini "wow" sejati, tapi butuh `SYSTEM_ALERT_WINDOW` + onboarding izin yang baik. Rekomendasi: fase 2.

### B2. "Drop zone" quick-edit
Android 12+ app bisa menerima drag-and-drop dari app lain (`ACTION_DRAG_STARTED` / `OnReceiveContentListener`). Jadi user bisa drag gambar dari galeri ke jendela FloatPic. Ini fitur "terasa masa depan" yang jarang dipakai.

### B3. Privacy-first mode
Badge "Diproses di perangkat, tidak ada upload" di onboarding. Karena share dari chat bisa sensitif, ini diferensiasi kepercayaan. Tidak butuh teknis, butuh disiplin (jangan pasang SDK analytics berat).

### B4. Template cepat berbasis konteks
Deteksi gambar: screenshot (rasio layar + status bar) → tawarkan preset "Screenshot Polish"; foto potret → "Story Clean"; meme (rasio persegi, teks) → "Meme Frame". Rule-based ringan, hasilnya terasa pintar. **Rekomendasi kuat untuk MVP+.**

### B5. Batch edit (multiple share)
Share 5 gambar sekaligus → editor menerapkan rasio/background yang sama ke semua → preview grid → "Terapkan ke semua". Fitur ini membuat app *ramai* dipakai untuk jualan online (IG carousel). Effort sedang, impact tinggi.

### B6. Kolase grid (2-9 gambar)
Sekali lagi untuk penjual: grid 2×2, 3×3 dengan gap & background yang bisa diatur. Berbobot, bisa jadi fitur Pro pertama.

## Kategori C — jangan dulu (tunda/pertimbangkan nanti)

- **AI remove background / AI expand** — mahal, butuh model/API, tapi sudah jadi standar di app besar. Kalau ikut, pakai MLKit subject segmentation (on-device, gratis) untuk "cutout" sederhana. Tunda sampai core stabil.
- **Teks berlapis / font picker** — mengubah produk dari editor cepat menjadi editor desain; scope creep terbesar. Mungkin v2.
- **Filter LUT penuh / curve** — pasar sudah, effort tinggi.
- **Video** — beda dunia (codec, transcode, permission). Jangan.
- **Cloud sync / akun** — bertentangan dengan janji "no-login, cepat".
- **Community feed** — sepi kalau baru, sia-sia.

## Roadmap yang saya sarankan

**MVP (v0.1) — fokus janji:**
share (single+multiple) → editor L1 → rasio populer → crop/align → background solid/gradient/blur → pinch/pan/rotate + snap → undo/redo → share/save → draft.

**v0.2 — polish + retention:**
preset, auto ambient color, grid/level, "sembunyikan UI", copy clipboard, konteks otomatis (B4), batch edit.

**v0.3 — monetisasi + wow:**
Pro (preset & brand kit & batch), floating bubble (L2), kolase (B6), watermark opt-out.

## Prioritas (MoSCoW ringkas)

- **Must:** share target, editor, rasio, crop/align, background dasar, pinch/rotate, save/share, undo.
- **Should:** preset, draft, batch, context detection, blur background.
- **Could:** bubble L2, drag-drop, kolase, brand kit.
- **Won't (sekarang):** AI background removal, text tool, video, akun.

## Pertanyaan

1. Dari Kategori B, mana yang paling kamu inginkan sebagai ciri khas: floating bubble, batch edit, atau context-aware preset?
2. Batch edit jualan online — target user nyata untukmu?
3. Pro plan: fitur apa yang pantas (preset Pro + no-watermark + batch)?
