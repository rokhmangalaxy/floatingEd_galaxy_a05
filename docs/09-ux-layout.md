# 09 — UX/UI: Prinsip & Layout Layar Editor

## Prinsip

1. **Satu layar, satu tujuan.** Tidak ada navigasi di dalam editor. Semua tool adalah panel yang naik/turun dari bawah.
2. **Preview = kebenaran.** Apa yang terlihat = apa yang diekspor (WYSIWYG yang sama renderer-nya).
3. **Tidak ada modal dialog untuk hal destruktif; gunakan snackbar dengan Undo.** Dialog hanya untuk "keluar tanpa menyimpan".
4. **Chip > dropdown > dialog.** Urutan efektivitas.
5. **Gesture dulu, tombol kedua.** Tapi tombol tetap ada (aksesibilitas).
6. **Selalu dark mode.** Kanvas gelap membuat warna gambar lebih akurat.
7. **Tidak ada iklan di area preview.** Iklan (bila ada) hanya di exit/antar sesi.

## Anatomi layar editor (portrait)

```
┌─────────────────────────────────────┐
│  [X]   Edit                          │  ← top bar 48dp, X untuk tutup
│                                      │
│   ╭──────────────────────────────╮   │
│   │                              │   │
│   │        VIEWPORT              │   │  ← rasio aktif, padding aman
│   │      (gambar + bg)           │   │     max 55% tinggi layar
│   │                              │   │
│   ╰──────────────────────────────╯   │
│                                      │
│  [Auto] [1:1] [4:5] [9:16] [16:9] [>]│  ← chips rasio, scroll horizontal
│                                      │
│  [ ⎘ Crop ] [ ⬚ Align ] [ ◐ BG ] [ ↻ ]│  ← tool row 4 item, 56dp
│                                      │
│   [   Simpan   ]  [   Share   ]      │  ← primary CTA, bottom inset
└─────────────────────────────────────┘
```

Portrait layout urutan dari atas: preview → rasio → tools → CTA. **Jangan** taruh CTA di atas (thumb zone).

Landscape/tablet: preview kiri (60%), controls kanan (40%) dalam kolom yang bisa scroll — semua kontrol di satu sisi, jauh lebih nyaman.

## Panel bawah (bottom sheet) per tool

| Tool | Isi panel | Tinggi |
|---|---|---|
| Crop | toolbar grid toggle, aspect lock, reset, "terapkan" | ~30% |
| Align | grid 3×3, nudge slider | ~24% |
| Background | chip tipe + palet + slider (padding/corner) | ~40%, bisa expand |
| Rotate | tombol ±90°, slider -45..45°, reset | ~20% |
| Ratio (custom) | input W:H, preset tambahan | ~30% |

Bottomsheet pakai `ModalBottomSheet` dengan `dragHandle`, bukan `Dialog`.

## Tipografi & state

- Label chip: `labelSmall` (11-12 sp) kapital? — jangan kapital penuh, "4:5" numerik lebih enak.
- Warna aktif chip: `primaryContainer` terang; tidak aktif: `surfaceVariant`.
- **Disabled state** jelas: background transparan, alpha .38, tanpa ripple.
- **Loading**: skeleton shimmer pada viewport saat decode (jangan spinner penuh layar).
- **Error**: inline card di viewport, tombol "Coba lagi" + "Pilih gambar lain".

## Onboarding minimalis

1. Pertama kali dibuka via share: cukup 1 hint context ("Tarik untuk geser · Cubit untuk zoom · Dua jari untuk putar"), fade out setelah gesture pertama. Jangan carousel 5 layar.
2. Izin overlay (bila L2) baru diminta saat user menekan tombol "Mode mengambang" — dengan penjelasan visual 10 detik.

## Mikro-interaksi yang layak

- Chip rasio: animasi indicator sliding (bukan instant swap).
- Tombol Share: press scale .96, release dengan ease.
- Setelah simpan: snackbar "Tersimpan di Galeri · Lihat" (tap → buka gambar).
- Saat ganti background: crossfade 150 ms.
- Rotate snap: haptic + animasi 90 ms.
- "Hide UI" (double tap viewport): toolbar turun + fade, tap lagi balik.

## Aksesibilitas (wajib, bukan opsional)

- Semua tombol ikon punya `contentDescription`; chip rasio punya label "Rasio 4:5".
- Target sentuh ≥ 48 dp; jarak antar chip ≥ 8 dp.
- Teks putih di atas gambar gelap: gunakan scrim gradient di belakang top bar.
- Mendukung font scale besar: chip wrap; toolbar bisa jadi 2 baris.
- Alternatif gesture: zoom +/−, rotate ±90° tombol (lihat doc 07).

## Strings & localization

- Siapkan `values/` (Indonesia) & `values-en/` (Inggris). Indonesia dulu, tapi ingat bahasa app share-target mengikuti locale device — jangan hardcode.
- Copywriting singkat: "Simpan", "Share", "Crop", "Rasio"— konsisten. Hindari "Export" (terdengar pro-tool berat).

## Pertanyaan untukmu

1. CTA utama: **Share** (kiri/kanan?) atau **Simpan**? Rekomendasi: dua tombol sejajar — Share (primary), Simpan (secondary).
2. Bahasa default app: Indonesia saja, atau ikut locale (id + en)? Rekomendasi: id + en.
3. Perlu tema terang di editor? Rekomendasi: tidak; kanvas selalu gelap.
