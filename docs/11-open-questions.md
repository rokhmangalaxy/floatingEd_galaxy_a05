# 11 — Open Questions & Next Actions

Semua pertanyaan dari dokumen lain dikumpulkan di sini. Isi jawabanmu (atau bilang "rekomendasi saja") dan saya lanjut ke langkah berikutnya.

## Blok keputusan

### 1. Identitas (doc 01)
- [ ] Nama final: `FloatPic` / `Floaty` / `Tumpuk` / lain?
- [ ] Tagline: "Editor mengambang" / "Edit tanpa keluar app" / lain?
- [ ] Deep link scheme: `floatpic://`?
- [ ] `minSdk`: 26 (rekomendasi) atau 24?

### 2. Positioning (doc 02)
- [ ] Level floating: **L1** (rekomendasi) / L2 / L0?
- [ ] Pasar: Indonesia dulu, atau global dari awal?
- [ ] Format export default: JPEG 92 (rekomendasi) / PNG / WebP?

### 3. Kanvas (doc 04)
- [ ] Crop default: non-destructive (rekomendasi) atau potong isi?
- [ ] Rasio bebas ("Free") di MVP?
- [ ] Resolusi export: standar 1080 / pilihan HD / ikut asli?

### 4. Background (doc 05)
- [ ] Background transparan perlu?
- [ ] Prioritas Tier 3: ambient color / wallpaper mode / brand kit / duotone?

### 5. Rasio & preset (doc 06)
- [ ] Chip tampil 5 atau 7 sebelum scroll?
- [ ] Custom ratio di MVP?
- [ ] Preset sekarang atau nanti? (rekomendasi: nanti, data disiapkan)

### 6. Gesture (doc 07)
- [ ] "Pinch out": zoom (rekomendasi) atau drag-keluar?
- [ ] Inersia pan: ya/tidak?
- [ ] Rotate bebas + snap (rekomendasi) atau 90° saja?

### 7. Fitur tambahan (doc 08)
- [ ] Ciri khas Kategori B: floating bubble / batch edit / context-aware preset?
- [ ] Batch edit untuk jualan online — target nyata?
- [ ] Pro plan isi apa?

### 8. UX (doc 09)
- [ ] CTA: Share primary + Simpan secondary (rekomendasi)?
- [ ] Bahasa: id + en (rekomendasi)?
- [ ] Editor selalu dark (rekomendasi)?

### 9. Teknis (doc 10)
- [ ] Compose murni (rekomendasi)?
- [ ] 3 modul (rekomendasi) vs single module?
- [ ] DI manual (rekomendasi) vs Hilt?

## Next actions (setelah jawaban)

1. Saya susun **`README` produk** (positioning final + feature list v1).
2. Saya tulis **spesifikasi layar** (wireframe ASCII/figma-light) per dokumen 09.
3. Saya setup **skeleton project** (Gradle, modul, Compose, manifest share target) — di branch ini.
4. Setelah skeleton jalan, saya bangun **renderer + editor viewport** dulu (bagian paling menentukan), baru tool.

## Catatan

- Semua keputusan di atas **bisa diubah nanti**, kecuali: `applicationId`, nama app di Play Store, dan `minSdk` (makin sulit diubah setelah publikasi). Utamakan tiga itu.
- Jangan menulis UI editor sebelum renderer preview/export sepakat dengan model `EditorState` — itu investasi yang paling sering diabaikan dan paling mahal untuk diperbaiki.
