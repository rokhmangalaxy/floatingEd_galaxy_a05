# 11 — Keputusan Terkunci & Pertanyaan Tersisa

## ✅ Sudah diputuskan

| # | Keputusan | Nilai |
|---|---|---|
| 1 | Level floating | **L1** — kartu melayang di activity transparan + skrim (arsitektur siap L2) |
| 2 | Ciri khas | **Floating bubble**: minimize → bubble di tepi layar; long-press 1.6 s → getar → close app |
| 3 | Teks | Satu lapis, **atas-tengah / bawah-tengah**, **putih+stroke hitam** / **hitam+stroke putih** |
| 4 | Aksi utama | Simpan, Share, Minimize (bubble), Close (dari bubble) |
| 5 | Tema | **Neumorphism** & **Pastel Maroon 500% saturasi**, bisa di-toggle |
| 6 | Snap haptic | Limit pinch, snap rotate (±3°), tap posisi 9 titik, double-tap zoom |
| 7 | Mirror | Tool **Cermin** (horizontal/vertikal) di tool row; teks tidak ikut terbalik |
| 8 | Align → **Posisi** | Label & ikon diganti: panah menempelkan gambar relatif terhadap bingkai (dulu "align"/frame) |
| 9 | Ikon app | **Wadah cat + kuas + awan kinton** (digambar via `tools/gen_art.py`) |
| 10 | Build & distribusi | GitHub Actions → rolling release `apk-latest`; kunci tetap → update menimpa |
| 11 | Kartu editor | Lebar **80%** × tinggi **70%** layar (bukan full-screen) |
| 12 | Container preview | **Kotak 1:1** (bingkai), gambar mengikuti rasio di dalamnya |
| 13 | Riwayat | **10 edit terakhir** bisa dibuka & diedit ulang (long-press = hapus) |

Konsekuensi dari keputusan di atas sudah dijabarkan di dokumen 12 (teks), 13 (bubble), dan 14 (tema).

## ⚠️ Satu ketegangan yang perlu disadari

"Bubble" secara teknis butuh **L2** (overlay window + izin `SYSTEM_ALERT_WINDOW`), sedangkan editor tetap L1. Ini sudah diselesaikan dengan arsitektur di doc 13: **editor = Activity, bubble = launcher**. Jadi tidak perlu menulis editor di dalam overlay. Rekomendasi urutan: core editor dulu → bubble di v1.1 (2–4 hari).

## ❓ Pertanyaan tersisa (jawab saat mau mulai kode)

### Identitas
- [ ] Nama final: `FloatPic` / `Floaty` / `Tumpuk` / lain?
- [ ] `minSdk` 26 (rekomendasi) atau 24?
- [ ] Bahasa: id + en (rekomendasi) atau id saja?

### Editor
- [ ] Crop default non-destructive (rekomendasi) atau potong isi?
- [ ] "Pinch out" = zoom (rekomendasi) atau drag-keluar?
- [ ] Resolusi export: 1080 standar (rekomendasi) / pilihan HD / ikut asli?
- [ ] Background transparan (export PNG) perlu?
- [ ] Font teks: Roboto Black saja (rekomendasi) atau + 1 display font (Anton)?

### Bubble
- [ ] Bubble masuk **v1** (langsung, effort naik) atau **v1.1** (rekomendasi)?
- [ ] Close: hold **1.6 s + ring** (rekomendasi) atau 600 ms saja?
- [ ] Setelah close: hapus sesi (rekomendasi, privasi) atau simpan Riwayat?

### Tema
- [ ] Default: Neumorphism (rekomendasi) atau Pastel Maroon?
- [ ] Dua tema langsung di v1, atau satu dulu? (rekomendasi: dua, token disiapkan sekarang)

### Teknis
- [ ] Compose murni (rekomendasi)?
- [ ] 3 modul (`app`, `core`, `feature:editor`) — rekomendasi?
- [ ] DI manual dulu (rekomendasi) vs Hilt?

## Status implementasi (v0.1 — sudah di APK)

- [x] Skeleton Gradle + Compose, `minSdk 26`, manifest share target + launcher
- [x] `EditorState` + renderer preview=export (satu fungsi), EXIF, sampling
- [x] Viewport L1, rasio, crop (fill/fit), posisi 9 titik, background (solid/gradasi/blur/pattern/ambient), padding/sudut/shadow
- [x] Gesture pinch/pan/rotate + snap + clamp haptic, double-tap zoom
- [x] Teks satu lapis (atas/bawah, putih⇄hitam stroke, auto-fit 3 baris, live preview)
- [x] Cermin H/V, rotate ±90°, undo/redo
- [x] Simpan galeri (incl. izin legacy API ≤28), share, minimize ke bubble, restore, hold-close
- [x] Tema Neumorphism & Pastel Maroon + toggle persisten
- [x] CI: build release bertanda tangan, verifikasi fingerprint kunci, rilis `apk-latest`

## Perbaikan v0.1.1 (dari laporan perangkat)

- [x] **Force close saat minta izin overlay** → semua `startActivity` izin dibungkus `runCatching` + fallback ke Settings global + toast (tidak pernah crash).
- [x] **`cannot open content://…`** → stream share dibaca **sekali** lalu disalin ke cache; decode & EXIF hanya dari file lokal (chat app sering memberi URI sekali-pakai).
- [x] Kartu editor **80% × 70%**, container preview **kotak 1:1**.
- [x] **Riwayat 10 edit** (foto + state) yang bisa dibuka lagi tanpa kehilangan setting.
- [x] Thumbnail riwayat didecode di background thread (tidak nge-freeze UI).

## v0.2 — tata letak baru

- [x] Container preview **kotak 1:1**; isi mengikuti **rasio asli** atau rasio pilihan
- [x] **Hanya icon row** yang tampil permanen (7 ikon: Rasio, Crop, Posisi, Latar, Teks, Cermin, Riwayat)
- [x] Panel menu **overlay** di atas preview → preview/icon row/CTA tidak bergeser
- [x] Ikon **Rasio** & **Riwayat** ditambahkan ke icon row
- [x] Toggle **Maroon ⇄ Dark** (matahari/bulan), default Dark, tersimpan
- [x] Tap area preview menutup panel

## Berikutnya (v0.3)

1. Preset kontekstual (screenshot/portrait/meme) + preset simpan sendiri.
2. Batch edit (share banyak gambar → satu rasio/background).
3. Semua-permukaan blur: buram dari gambar yang sama untuk area tepi (saat ini vignette).
4. Teks: 1 font display tambahan, opsi scrim.
5. Uji perangkat nyata (Samsung/Xiaomi/Oppo) untuk share sheet & izin overlay.
