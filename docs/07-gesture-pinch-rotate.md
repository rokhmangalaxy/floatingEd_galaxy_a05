# 07 — Gesture: Pinch, Out (Zoom), Rotate, Pan

Bagian yang paling menentukan "terasa mahal atau tidak". Semua tool berbagi satu `Transform` (lihat doc 04), jadi gesture hanya perlu mengubah 4 angka dengan benar.

## Model gesture

Gunakan `detectTransformGestures` (Compose) sebagai basis + tracker sendiri:

| Gesture | Aksi | Catatan |
|---|---|---|
| 1 jari drag | Pan (geser gambar di bawah bingkai) | Langsung; jangan pakai threshold besar |
| 2 jari pinch (in/out) | Scale (zoom) | Batas min = fit-cover penuh, max = 8× (resolusi) |
| 2 jari rotate | Rotate | Snap ke 90° saat lepas bila ±3° |
| Tap (1x) | "Sentuh untuk fokus" → sembunyikan UI sejenak (mode preview) | Bonus: double-tap untuk zoom 2× dan kembali |
| Double tap | Zoom toggle 1× ↔ 2× | Jangan untuk fit; pakai current scale sebagai basis |
| 1 jari dari tepi bingkai | Crop handle (bila crop mode) | Hanya aktif di crop mode |

## Matematika yang benar (ringkas)

Masalah klasik: pinch terasa "melayang" karena rotasi dihitung sumbu yang salah. Resep yang aman:

1. Hitung di **koordinat viewport** (bukan koordinat gambar).
2. Ambil centroid gesture (`pointer[0] + pointer[1]) / 2`).
3. Terapkan animasi rotasi + scale **sekeliling centroid**:
   ```
   newOffset = centroid + (oldOffset - centroid) * (newScale / oldScale)
               + translation
   ```
   Rotasi: `newRotation = oldRotation + delta`, dan offset dikoreksi dengan rotasi *sekeliling centroid* juga:
   ```
   p' = rotate(centroid, p - centroid, Δrot) + centroid
   ```
4. **Transform origin di tengah gambar**, bukan pojok — semua perhitungan translate pakai center.
5. Pakai `rememberTransformableState` bila cukup; untuk kontrol penuh (snap, bounds) gunakan `pointerInput` manual.

## Batas (clamp) yang harus ada

Tanpa clamp, user bisa kehilangan gambar ke luar layar dan panik:

- **Scale min:** cukup agar gambar menutupi viewport (fill). Jika scaleMode = FIT, min = fit.
- **Scale max:** `8f` absolut, atau dinamis = `1.5f * (fullRes / previewRes)` supaya tidak pecah saat export.
- **Offset clamp:** gambar harus selalu menyentuh/menutupi viewport (tidak ada celah kosong) bila scaleMode = FILL. Pengecualian: mode FIT atau saat gambar lebih kecil dari viewport — offset dikunci ke tengah.
- **Rotation:** bebas, tidak perlu clamp (rotate sekitar center menyebabkan korner ikut keluar; clamp offset setelah rotasi).

## "Pinch out" — maksudnya apa?

Di brief, "pinch out untuk resize rotate" bisa berarti dua hal:
1. **Zoom-in** dengan pinch-out (dua jari menjauh) → scale naik. Ini standar.
2. **Drag keluar dari bingkai** seperti "menarik gambar keluar" (drag-to-out) → lebih ekspresif di layar besar (tablet/foldable).

Rekomendasi: implementasi #1 di semua device; pertimbangkan #2 sebagai gesture opsional (long-press lalu drag keluar) di tablet. Jangan jadikan #2 jalur utama — bertentangan dengan pan biasa dan sulit ditemukan.

## Detail UX yang membuatnya terasa premium

1. **Inersia** saat pan berhenti (velocity decay 180 ms) — tetapi **jangan** jika mode FIT/clamped; biar natural.
2. **Rotasi + scale simultan** harus stabil: kalikan matrix dengan urutan T → R → S (bukan S → R → T).
3. **Snap rotation** dengan animasi 90 ms (bukan langsung) + haptic `HapticFeedbackType.LongPress` (atau `TextHandleMove`).
4. **Indikator rotasi**: tampilkan derajat kecil mengambang dekat gambar selama gesture.
5. **Reset view** (double-tap chip rasio / tombol "reset") → animasi 250 ms ke state awal.
6. **Gesture conflict**: horizontal scroll chips rasio **jangan** menangkap drag vertikal gambar. Pakai `nestedScroll` atau batasi chips agar hanya bisa di-drag di area chip bar.
7. **Aksesibilitas**: semua gesture punya pasangan tombol (rotate ±90°, reset, zoom +/−). Ini juga wajib untuk Play Store quality.
8. **Haptic** di setiap snap/limit — sesuatu yang paling terasa "mahal" di demo.

## Interaksi dengan crop

Saat crop mode aktif:
- Drag tepi → crop handle (bukan pan).
- Drag tengah → pan tetap.
- Pinch → scale gambar (bukan zoom bingkai), kecuali user menahan handle sudut dengan dua jari (jarang; abaikan).
- Rotate di crop mode: **nonaktifkan** (rotasi setelah crop bikin bingkai tidak sinkron) atau restart crop setelah rotasi. Rekomendasi: disable dengan toast kecil "Rotasi dinonaktifkan selama crop".

## Spesifikasi gesture yang perlu ditulis ke QA

| No | Langkah | Harapan |
|---|---|---|
| G1 | Buka gambar 4:3, pinch-out 2× | Gambar membesar di sekitar dua jari, tidak melompat |
| G2 | Rotate 45°, lepas | Tetap 45° (tidak snap karena >3°) |
| G3 | Rotate 88°, lepas | Snap ke 90° dengan animasi halus |
| G4 | Pan ke tepi atas sampai habis | Berhenti di tepi, tidak ada celah |
| G5 | Ganti rasio saat sudah di-zoom | Zoom relatif dipertahankan, center sama |
| G6 | Pinch sampai min | Berhenti di fit/cover, tidak negative scale |
| G7 | Drag keluar saat mode FIT | Tetap di tengah (tidak bisa digeser bebas) |
| G8 | Rotate 90° pada gambar lebar di bingkai sempit | Otomatis scale agar tetap menutupi |

## Keputusan

1. "Pinch out" yang dimaksud: zoom (#1) atau drag-keluar (#2)?
2. Inersia pan: ya (rekomendasi) atau tidak (lebih presisi)?
3. Rotate bebas (slider) atau hanya 90° step? Rekomendasi: bebas + snap.
