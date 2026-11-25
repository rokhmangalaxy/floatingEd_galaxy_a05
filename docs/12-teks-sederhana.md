# 12 — Teks Sederhana: Atas-Tengah / Bawah-Tengah

Keputusan: teks **sengaja dibatasi** — satu teks, dua posisi, dua gaya. Ini caption/meme text, bukan tool desain. Batasan ini yang menjaga app tetap "cepat".

## Scope yang disepakati

| Ada | Tidak ada (v1) |
|---|---|
| 1 teks per gambar | banyak layer teks |
| Posisi **atas-tengah** / **bawah-tengah** | drag bebas, rotasi, arsir |
| Gaya **putih + stroke hitam** / **hitam + stroke putih** | pilih font bebas, gradient, shadow |
| Slider ukuran (auto-fit) | letter-spacing manual, curve |
| Hapus teks | animasi teks |

Kalau nanti butuh lebih, itu **tool teks v2** dengan layer & drag — jangan tambah satu-satu ke panel ini (jadi Frankenstein).

## Model data

```kotlin
data class TextOverlay(
    val text: String = "",
    val position: TextPosition = TextPosition.BOTTOM_CENTER,
    val style: TextStylePreset = TextStylePreset.WHITE_ON_BLACK,
    val sizeFraction: Float = 0.07f,        // relatif lebar viewport
) {
    val isVisible: Boolean get() = text.isNotBlank()
}

enum class TextPosition { TOP_CENTER, BOTTOM_CENTER }

enum class TextStylePreset { WHITE_ON_BLACK, BLACK_ON_WHITE }
```

`sizeFraction` relatif (bukan sp/dp) supaya preview & export identik di semua resolusi.

## Rendering (inti, ~30 baris)

Kunci: gambar **stroke dulu, fill belakangan**. Kalau dibalik, fill menutup stroke.

```kotlin
fun drawTextOverlay(canvas: Canvas, overlay: TextOverlay, outW: Int, outH: Int) {
    if (!overlay.isVisible) return

    val (fillColor, strokeColor, strokeFrac) = when (overlay.style) {
        WHITE_ON_BLACK -> Triple(Color.WHITE, Color.BLACK, 0.10f)
        BLACK_ON_WHITE -> Triple(Color.BLACK, Color.WHITE, 0.12f)
    }

    val margin = outW * 0.06f
    val maxWidth = (outW - margin * 2).toInt()
    val baseSize = outW * overlay.sizeFraction

    // auto-fit: turunkan ukuran sampai muat (maks 3 baris)
    var textSize = baseSize
    var layout: StaticLayout
    do {
        layout = buildLayout(overlay.text, maxWidth, textSize, fillColor, strokeColor, strokeFrac)
        textSize *= 0.94f
    } while (layout.height > outH * 0.28f && textSize > outW * 0.03f)

    val x = (outW - layout.width) / 2f
    val y = when (overlay.position) {
        TextPosition.TOP_CENTER    -> outH * 0.05f
        TextPosition.BOTTOM_CENTER -> outH - layout.height - outH * 0.06f
    }

    canvas.withTranslation(x, y) { layout.draw(this) }
}

private fun buildLayout(
    text: String, maxWidth: Int, sizePx: Float,
    fill: Int, stroke: Int, strokeFrac: Float,
): StaticLayout {
    val strokePaint = TextPaint(ANTI_ALIAS_FLAG).apply {
        color = stroke
        textSize = sizePx
        style = Paint.Style.STROKE
        strokeWidth = sizePx * strokeFrac
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    val fillPaint = TextPaint(strokePaint).apply {
        color = fill
        style = Paint.Style.FILL
        strokeWidth = 0f
    }
    val builder = StaticLayout.Builder.obtain(
        text, 0, text.length, fillPaint, maxWidth
    ).setAlignment(Layout.Alignment.ALIGN_CENTER)
     .setLineSpacing(0f, 1.05f)
     .setMaxLines(3)

    return object : StaticLayout(builder.build()) { /* wrapper untuk stroke + fill */ }
        .also { /* lihat catatan: gambar dua layout identik */ }
}
```

**Catatan implementasi:** `StaticLayout` hanya menerima satu `TextPaint`. Solusi yang bersih: buat **dua** `StaticLayout` identik (satu pakai `strokePaint`, satu `fillPaint`), gambar berurutan:

```kotlin
canvas.withTranslation(x, y) {
    strokeLayout.draw(this)
    fillLayout.draw(this)
}
```

Alternatif yang lebih hemat: `canvas.drawText()` per baris dengan `paint.style = STROKE`, lalu `FILL` — cukup untuk maksimal 3 baris dan centang per baris manual. Untuk konsistensi, pilih **dua StaticLayout** (lebih sederhana kodenya, tetap cepat, hanya 2 objek).

## Gaya (persis "meme style")

| Preset | Fill | Stroke | Lebar stroke | Contoh |
|---|---|---|---|---|
| `WHITE_ON_BLACK` | #FFFFFF | #000000 | 10% em | teks putih tebal, garis hitam |
| `BLACK_ON_WHITE` | #000000 | #FFFFFF | 12% em | teks hitam, garis putih |

- Stroke 8–14% em adalah rentang yang terbaca di semua ukuran; simpan sebagai konstanta.
- Font default: **Roboto Black** (`sans-serif-black`) — gratis, tebal, tidak butuh aset. Opsional nanti: Anton (OFL), Bebas Neue.
- `lineSpacing` 1.05; tanpa letter-spacing; center align.
- Teks maksimal **80 karakter** (bukan teknis, tapi batas UX agar tetap 1–3 baris).

## UI flow (harus ≤2 tap ke ketik)

```
Toolbar [Teks] → sheet naik (60%) + keyboard otomatis
  ┌─────────────────────────────────┐
  │  [ Ketik teks… ]        80      │  ← BasicTextField, auto-focus
  │  Posisi:  ( Atas )  ( Bawah )   │  ← segmented
  │  Gaya:    ( A▮ )    ( ▮A )      │  ← dua chip preview, A besar
  │  Ukuran:  ───●──── 100%         │
  │  [ Hapus teks ]                 │
  └─────────────────────────────────┘
```

- Preview **live** saat mengetik (tanpa tombol Apply).
- Chip gaya menampilkan huruf "A" dengan warna/stroke sesungguhnya — user langsung paham.
- Kalau keyboard menutupi preview, viewport otomatis naik (imePadding).
- Mengubah posisi = crossfade 120 ms, bukan lompat.

## Interaksi dengan fitur lain

- **Rasio berubah:** posisi teks ikut (fraction), ukuran ikut lebar baru. Jangan diam di koordinat lama.
- **Background transparan** (PNG): teks tetap dirender; tidak ada scrim.
- **Export:** renderer yang sama; tidak ada "teks hilang saat export" — pastikan teks digambar di atas gambar & background, di bawah overlay UI (grid tidak ikut).
- **Undo:** debounce 400 ms per perubahan teks; satu langkah undo menghapus satu edit yang "selesai", bukan per huruf.
- **Align gambar:** teks **tidak** ikut bergeser (dia menempel ke viewport, bukan ke gambar). Ini keputusan penting — tulis di QA.

## Edge case & QA

| No | Kasus | Harapan |
|---|---|---|
| T1 | Teks kosong | Tidak ada yang digambar, tombol simpan normal |
| T2 | 80 karakter tanpa spasi | Auto-shrink, tetap di dalam margin |
| T3 | Emoji | Tampil; ukuran tidak merusak layout |
| T4 | Teks Arab/India | Center align benar; shape otomatis |
| T5 | Ganti rasio 16:9 → 9:16 | Teks menyesuaikan, tidak terpotong |
| T6 | Rotasi gambar | Teks **tidak** ikut rotasi (menempel viewport) |
| T7 | Gaya putih/hitam di background putih | Stroke memastikan tetap terbaca |
| T8 | Font scale sistem 1.5× | Tidak memengaruhi export; UI editor menyesuaikan |

## Pertanyaan

1. Font: Roboto Black saja (rekomendasi) atau tambah satu font display (Anton/Bebas)?
2. Batas 80 karakter cukup? (lebih = baris ke-4, mulai kecil)
3. Perlu scrim tipis di belakang teks sebagai opsi? (rekomendasi: tidak, stroke sudah cukup)
