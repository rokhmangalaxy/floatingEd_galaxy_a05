# 11 — Keputusan Terkunci & Pertanyaan Tersisa

## ✅ Sudah diputuskan

| # | Keputusan | Nilai |
|---|---|---|
| 1 | Level floating | **L1** — kartu melayang di activity transparan + skrim (arsitektur siap L2) |
| 2 | Ciri khas | **Floating bubble**: minimize → bubble di tepi layar; long-press 1.6 s → getar → close app |
| 3 | Teks | Satu lapis, **atas-tengah / bawah-tengah**, **putih+stroke hitam** / **hitam+stroke putih** |
| 4 | Aksi utama | Simpan, Share, Minimize (bubble), Close (dari bubble) |
| 5 | Tema | **Neumorphism** & **Pastel Maroon 500% saturasi**, bisa di-toggle |

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

## Next actions

1. Kunci jawaban di atas (bisa bilang "pakai rekomendasi semua").
2. Saya setup skeleton project (Gradle, Compose, manifest share target).
3. Bangun `EditorState` + renderer preview=export (inti).
4. Layar editor L1: viewport, rasio, crop/align, background, teks.
5. Bubble service: izin, drag+snap, hold-to-close, restore.
6. Tema: token Neumorphism & Maroon + toggle.
