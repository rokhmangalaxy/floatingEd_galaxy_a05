# 02 — Arah Visi & Positioning: "Floating Editor"

Ini dokumen yang harus dibaca sebelum menulis kode editor. Pertanyaan besarnya: **app ini editor penuh, atau jembatan cepat?** Jawaban itu mengubah segalanya: jumlah tool, cara simpan, cara monetisasi.

## Dua arketipe yang sering tertukar

### A. "Bridge" — share → edit cepat → balik ke app asal
- **Pemakaian:** user masih di WhatsApp/IG/ChatGPT, share gambar, buka overlay, atur rasio, crop, selesai → back → otomatis kembali ke app asal (share target memang kembali ke sumber setelah hasil dikirim).
- **Tools:** harus sangat sedikit di layar. Main preview, ratio chips, crop, align, background, 1 tombol Simpan, 1 tombol Share.
- **Wow factor:** **floating window sungguhan** (bukan full-screen) yang tidak menutup app asal; user melihat chat di belakangnya.
- **Monetisasi:** IAP "Pro Tools" (preset, font, template) + ads ringan.
- **Keunggulan:** jarang ada pesaing (kebanyakan share-target full-screen), flow return cepat, sticky.

### B. "Editor penuh" yang lewat share target
- **Pemakaian:** user ingin edit sampai detail; share target cuma pintu masuk.
- **Tools:** layer, teks, sticker, filter, masker, curve, dst. Butuh 50+ layar.
- **Wow factor:** tidak ada — ini sudah padat (Canva, Snapseed, Picsart).

**Rekomendasi: pilih A, dan jadikan A sebagai identitas produk.** Floating window bukan gimmick, itu positioning. Semua keputusan desain selanjutnya diturunkan dari sini. Kalau A sukses, B tumbuh sebagai "mode lanjutan" nanti.

## Positioning statement (draft)

> **FloatPic** — editor yang tidak menutup aplikasimu. Share gambar dari aplikasi apa pun, atur rasio, crop, dan background di jendela kecil yang melayang, lalu kirim balik tanpa kehilangan konteks percakapan.

## Kenapa "floating" harus benar-benar floating

Ada tiga level ambisi yang berbeda — pilih level sekarang, karena teknisnya beda jauh:

| Level | Teknis | Pengalaman user | Effort |
|---|---|---|---|
| **L0 — Full-screen share target** | `ACTION_SEND` → Activity biasa | Pindah app sejenak (seperti editor lain) | 1x |
| **L1 — "Floating" secara visual** | Activity full-screen transparan + card editor melayang di tengah, dim/skrim, animasi masuk | Terasa mengambang, tapi app asal tetap tertutup | 1.1x (hampir sama) |
| **L2 — Floating window sungguhan** | `TYPE_APPLICATION_OVERLAY` + service foreground (`SYSTEM_ALERT_WINDOW`), atau `Activity` setara `freeform` di device yang mendukung | App asal tetap terlihat, editor bisa digeser, minimize ke bubble | 3x+ (izin overlay, OEM quirks, gesture conflict) |

**Rekomendasi: mulai L1**, design API-nya biar L2 bisa ditambah tanpa rombak (editor rendered ke `SurfaceView`/Compose card berdiri sendiri dengan `EditorHost` interface; overlay nanti cuma host lain). L1 sudah 90% "wow" dengan 10% risiko.

## Batasan teknis share target yang perlu diketahui sekarang

1. **Share sheet Android 10+ memakai `ChooserActivity` preview + `ShortcutManager` ranking.** Label activity harus pendek.
2. **`ACTION_SEND` vs `ACTION_SEND_MULTIPLE`:** dukung keduanya sejak awal — user sering share 2-9 gambar dari galeri. Kalau hanya single, share sheet akan menyembunyikan app untuk multiple.
3. **`ACTION_EDIT` / `ACTION_VIEW` (image/*):** dukung juga supaya bisa jadi handler "Open with"/default gallery viewer (dan dapat ikon di menu app galeri lain).
4. **`ClipData` MIME types:** jangan filter `image/*` saja; daftar juga `image/png`, `image/jpeg`, `image/webp`, `image/heic` — beberapa OEM gagal resolve wildcard di share sheet versi lama.
5. **Share kembali:** gunakan `ShareCompat.IntentBuilder` + `FLAG_GRANT_READ_URI_PERMISSION`; simpan hasil ke `cacheDir/shared/` (bukan `MediaStore`) supaya tidak mengotori galeri. Kalau user pilih "Simpan", baru tulis ke `MediaStore` via `MediaStore.Images` (scoped storage aman).
6. **HEIC/HEIF:** decode butuh `ImageDecoder` (API 28+) atau `HeifReader` (API 28); di API < 28 fallback `BitmapFactory` akan gagal. Putuskan: tolak dengan pesan jelas atau konversi via library decode (effort tinggi). Rekomendasi: Android 8 baseline, HEIC hanya di 28+.
7. **Ukuran data:** feed bisa 50 MP. Decode dengan `BitmapFactory.Options.inJustDecodeBounds` + `inSampleSize` ke ~2x ukuran preview; full-res hanya saat export. Wajib.
8. **EXIF orientation:** gambar kamera sering `Orientation=6/8`. Baca via `ExifInterface` (AndroidX), terapkan pada matrix render. Kalau lupa, crop preview akan terbalik di beberapa device — bug klasik.

## Ruang pasar & pesaing (ringkas)

- Share-target editor yang kuat: **Picsart, Canva** (full-screen, berat), beberapa "photo resizer" (cuma resize, tanpa floating).
- **Belum ada pemain di ceruk "quick floating editor untuk medsos"** — ini justru celahnya.
- Diferensiasi: kecepatan (<3 detik buka), no-login, tidak perlu keluar app.

## Definisi "sukses" versi awal (biar bisa diukur)

- Waktu dari share → editor tampil: **≤800 ms** (P90) untuk gambar 12 MP.
- Waktu dari "Simpan" → kembali ke app asal: **≤1.5 s**.
- Rasio "share selesai / share dibuka": **≥70%**.
- D7 retention share-target: susah diukur; pakai "jumlah edit ke-2 dalam 7 hari".

## Keputusan yang perlu diambil

1. Level floating: **L1 (rekomendasi)** / L2 / L0?
2. Baseline OS: Android 8 (rekomendasi) / 7 / 10?
3. Single vs multiple share: **dua-duanya (rekomendasi)**.
4. Simpan format default: JPEG 92 (rekomendasi) / WebP lossless / PNG?
