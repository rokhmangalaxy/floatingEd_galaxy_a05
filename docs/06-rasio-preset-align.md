# 06 — Rasio Populer, Preset & Align

## Rasio populer

Urutan chip menentukan berapa banyak user menemukan fitur ini tanpa scroll. Rekomendasi urutan (kiri → kanan):

```
[ Asli ] [ 1:1 ] [ 4:5 ] [ 9:16 ] [ 16:9 ] [ 3:2 ] [ 4:3 ] [ 2:3 ] [ 3:4 ] [ 5:4 ] [ + ]
```

Alasan urutan:
1. **Asli** di paling kiri — banyak user hanya ingin mempertahankan rasio (mis. hasil screenshot).
2. **1:1** (feed IG/FB, avatar), **4:5** (feed IG — paling optimal di feed, portrait), **9:16** (story/reels/tiktok/shorts), **16:9** (YouTube/landscape).
3. Sisanya "camera-native" dan jarang tapi harus ada agar terasa lengkap.
4. `+` membuka **custom ratio** (input W:H dengan validasi 1:20..20:1) dan **"Fit"** (tanpa bingkai).

Badge kecil di chip: platform (`IG`, `Story`, `YT`) hanya di onboarding pertama, lalu menghilang — jangan permanen (clutter).

## Alur & transisi antar rasio

1. Tap chip → animasi 180 ms viewport resize (bukan layout jump).
2. **Gambar tidak boleh melompat/terlihat bergeser** — ketika viewport mengecil, gambar boleh "terlepas" di luar bingkai (visual), tapi center harus stabil.
3. Kalau user sudah memindahkan gambar dan ganti rasio → tampilkan chips kecil bawah: **"Pertahankan"** vs **"Reset"** (bukan dialog).
4. **Long-press chip 4:5 → tooltip** "Feed Instagram" — discovery murah.

## Align

Interaksi align yang direkomendasikan: **grid 3×3 kecil** (9 titik) di samping kanan, bukan dropdown. Tap titik → gambar snap ke posisi itu dengan animasi 120 ms. Ini satu-satunya pola yang "instan" untuk align.

Tambahan:
- **Nudge** satu jari untuk geser bebas (sudah dalam transform).
- **Snap guides** saat nudge: ketika center/top/edge gambar mendekati garis tengah bingkai (±4 px), muncul garis tipis + haptic ringan. Wajib — membuat crop manual terasa "magnet".
- **Align + crop** beda: align mengubah `translateX/Y`; crop mengubah geometri sumber. Pastikan align tidak mengubah `scale` (atau boleh jika user set "fill" — lihat bawah).

## Mode "fit" vs "fill"

Dua mode penting dan harus eksplisit (chip kecil di samping rasio):
- **Fill (default):** gambar menutupi bingkai; tepi terpotong bila beda rasio. Cocok untuk feed.
- **Fit:** gambar utuh di dalam bingkai; sisa tepi diisi background. Cocok bila background adalah bagian desain.
- Toggle ini sebenarnya mengubah `scale` awal saja; simpan sebagai preferensi user juga.

## Preset (di luar rasio)

Preset = kombinasi **rasio + background + padding + corner + (opsional overlay)** yang bisa dipakai dengan satu tap. Ini fitur "saran lain" yang paling mudah dimonetisasi.

Contoh preset:
- **IG Feed Portrait** — 4:5, padding 0, gambar fill.
- **Story Clean** — 9:16, background ambient color, padding 64, rounded 0.
- **Screenshot Polish** — asli, padding 48, rounded 24, shadow lembut, background gradient abu.
- **Meme Frame** — 1:1, border hitam 24, gambar berbingkai putih.
- **Wallpaper Phone** — 20:9, gambar fit di bawah, gradient atas, safe area jam notifikasi.
- **Polaroid** — 4:5, border putih 36, area bawah lebih tebal, font caption opsional.

Rekomendasi: 3-4 preset gratis, 8-12 preset Pro. Preset disimpan sebagai JSON kecil (`PresetConfig`) → mudah diedit/di-generate tanpa update app (mirip remote config).

## Struktur data preset

```kotlin
data class PresetConfig(
    val id: String,
    val name: String,
    val aspect: Aspect,
    val scaleMode: ScaleMode,        // FILL / FIT
    val paddingDp: Int,
    val cornerRadiusDp: Int,
    val shadow: Boolean,
    val background: Background,
    val minAppVersion: Int,
)
```

Aktor yang berbeda (kamu sebagai desainer + user) dapat menikmati format yang sama: user bisa save preset pribadi dari hasil editnya (simpan state sebagai preset).

## Keputusan

1. Jumlah chip di layar sebelum scroll: 5 (rekomendasi) atau 7?
2. Custom ratio perlu di MVP?
3. Preset dibikin dari awal atau nanti setelah 3 fitur dasar stabil? (rekomendasi: nanti, tapi struktur data disiapkan sekarang)
