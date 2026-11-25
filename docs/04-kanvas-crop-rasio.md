# 04 — Kanvas & Viewport (geometri editor)

Ini fondasi teknis. Kalau salah, semua tool di atasnya ikut "goyang". Baca sekali, sepakati, baru coding.

## Konsep: satu kanvas, satu transform

Model mental yang paling tahan lama:

```
EditorCanvas (Compose, fixed aspect ratio)
 ├─ CanvasViewport   → mengisi seluruh kanvas, ukuran = rasio output
 │   ├─ BackgroundLayer   (warna / gradient / blur / wallpaper)
 │   └─ ContentLayer      (gambar + transform)
 │        └─ Transform = { scale, translateX, translateY, rotation }
 └─ Overlay            → grid, crop handle, guides (tidak ikut export)
```

**Aturan emas:** `Transform` hanya perlu menyimpan 4 field (scale, tx, ty, rot). Semua tool (crop, align, pinch, preset) mengubah *field yang sama*. Ini mencegah "state per-tool" yang bikin bug.

## Rasio output vs rasio layar

Tiga kotak yang harus dibedakan, jangan dicampur:

| Kotak | Isi | Dipengaruhi |
|---|---|---|
| **Screen** | area layar (px device) | device |
| **Viewport** | kotak rasio output di dalam layar, dengan padding aman | pilihan rasio |
| **Output** | bitmap final (mis. 1080×1350 / 1920×1080) | pilihan rasio + resolusi export |

Mapping: satu `scaleFactor = min(viewportW/outputW, viewportH/outputH)`. Semua koordinat gesture (layar) dikonversi ke koordinat output dengan matriks ini.

## Alurnya

1. User pilih rasio (1:1, 4:5, 9:16, 16:9, 4:3, 3:2, 2:3, 5:4, Free?).
2. Viewport + output diambil dari rasio tersebut: ideal output = **resolusi standar**, bukan bebas:
   - 1:1 → 1080×1080
   - 4:5 → 1080×1350
   - 9:16 → 1080×1920 (story/reels)
   - 16:9 → 1920×1080
   - 3:2 / 2:3 → 1920×1280 / 1280×1920
   - 4:3 / 3:4 → 1440×1080 / 1080×1440
   - 5:4 → 1350×1080
   - 20:9? (wallpaper HP) → opsional
   Biarkan user pilih "kualitas export: standar (1080) / HD (1440) / Full (resolusi asli)" — tapi **default standar**, jangan bikin output 6000px cuma untuk IG.
3. Saat rasio diganti: **jangan reset transform.** Simpan gambar di tengah dengan *fit cover* ke viewport baru, pertahankan `scale` relatif + skala ulang seperlunya supaya gambar tidak "melompat". Ini detail kecil yang membuat app terasa mahal.
4. Kalau ada gambar yang sebelumnya sudah di-crop manual, ganti rasio → tanya sekali (bottomsheet): "Pertahankan posisi" / "Reset center".

## Crop

Ada dua arti "crop" di brief — harus dipisah:

- **Crop (content)** = memotong isi gambar (mengubah bitmap sumber / mengubah area `srcRect`). Output ikut memotong — user kehilangan piksel.
- **Ratio viewport** = mengubah bingkai output, gambar tetap utuh (atau discale) di dalamnya. User bisa lihat background di tepi.

**Rekomendasi:** default = **non-destructive crop viewport** (gambar tidak dipotong, background mengisi tepi). Tombol "Crop isi" tersedia bila user memang mau memotong — dengan konfirmasi karena irreversible setelah disimpan.

Tool crop UI:
- Grid 3×3, garis tepi + sudut (dua jari atau handle).
- Aspect lock saat crop manual (chip rasio bisa juga berfungsi sebagai "crop lock").
- **Crop align:** setelah crop, user bisa geser gambar di dalam bingkai (bukan crop bingkai) — ini sering tertukar dengan "align". Align di brief = posisi gambar dalam bingkai (kiri/kanan/tengah, atas/bawah/tengah). Jadi urutannya: *bingkai dulu (ratio), lalu gambar di dalamnya (align + nudge + scale)*.

## Render pipeline (dari gestures ke export)

```
Preview (Compose Canvas):
   drawBackground()
   drawImage(bitmap, transform)   // hardware accelerated, bitmap asli downscaled ke ~2x viewport
Export (Bitmap / Canvas):
   Bitmap(outputW, outputH)
   drawBackground(canvas)
   drawImage(canvas, bitmap, transform * scaleFactor)
   → compress JPEG 92 (atau WebP)
```

**Gunakan renderer yang sama (fungsi murni) untuk preview dan export** — parameter `scaleFactor`. Kalau kode preview dan export terpisah, pasti ada bug "preview bagus, hasil beda". Ini prinsip yang tidak boleh dilanggar.

**Performa preview:** bitmap full-res (50 MP) tidak boleh di-draw langsung tiap frame di beberapa device low-end. Simpan `previewBitmap` ~ 2× viewport (maks 2048 px di sisi terpanjang), `fullBitmap` hanya dipakai saat export. Filtering: `FilterQuality.Medium` saat gesture, `High` saat idle.

## Kelas data (draft Kotlin, biar konkret)

```kotlin
data class EditorState(
    val source: Bitmap,           // full-res (lazy)
    val aspect: Aspect,           // enum + custom
    val transform: Transform,
    val background: Background,
    val cropLocked: Boolean = false,
)

data class Transform(
    val scale: Float = 1f,        // 1f = fit
    val offsetX: Float = 0f,      // px output
    val offsetY: Float = 0f,
    val rotation: Float = 0f,     // derajat
)

sealed interface Background {
    data object Transparent : Background
    data class Solid(val color: Long) : Background
    data class Gradient(val colors: List<Long>, val angle: Float) : Background
    data class Blurred(val fromImage: Boolean, val radius: Float) : Background
    data class Pattern(val id: String) : Background
}
```

Semua aksi (rotate, pin, align, chip) cukup memodifikasi `EditorState` → satu `undoStack: List<EditorState>` (maks 50) → undo/redo gratis.

## Checklist teknis kanvas

- [ ] EXIF orientation dibaca & diterapkan ke bitmap sebelum masuk state.
- [ ] Warna: semua warna background disimpan ARGB long; gelap/terang otomatis kontras label chip.
- [ ] Safe area: viewport tidak menempel ke tepi layar (min 24dp) supaya handle pinch tidak tertutup gesture navigasi.
- [ ] Rotasi: simpan rotasi dalam derajat (-180..180), snap ke 0/90/180/270 bila ±3° saat release.
- [ ] Memory: `Bitmap.Config.ARGB_8888` untuk export, `RGB_565` tidak dipakai (kualitas). Cek `ActivityManager.memoryClass` sebelum decode full.
- [ ] Edge case: gambar 1×1 px, gambar ekstrem panorama (8000×600), gambar dengan alpha.

## Pertanyaan

1. Crop default = non-destructive (rekomendasi) atau potong isi?
2. "Free ratio" (tidak terkunci) perlu? Kalau ya, apakah export size = ikut rasio layar?
3. Resolusi export: standar 1080 / bisa pilih HD / ikut asli?
