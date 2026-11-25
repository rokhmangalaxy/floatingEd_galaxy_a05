# 15 — Arah Visual Preview (untuk dirender ulang)

Dokumen ini menjelaskan **apa yang harus terlihat** pada arah desain. Saya sertakan juga mockup terang & gelap di `docs/mockups/` sebagai ilustrasi. Sebaiknya dijadikan acuan saat membangun UI (atau dirender ulang desainer).

## Layar editor (L1)

```
┌──────────────────────────────────────────┐
│ ░░ skrim gelap 40% (app asal terlihat) ░ │
│   ╭──────────────────────────────────╮   │
│   │  [×]  FloatPic          [◐] [⌄] │   │ ← top bar 56dp
│   │                                  │   │
│   │   ╭────────────────────────────╮ │   │
│   │   │                            │ │   │
│   │   │      4:5 VIEWPORT          │ │   │
│   │   │   gradient ambient bg      │ │   │
│   │   │   gambar + "teks caption"  │ │   │
│   │   │                            │ │   │
│   │   ╰────────────────────────────╯ │   │
│   │                                  │   │
│   │  [Asli][1:1][4:5●][9:16][16:9][+]│   │ ← chips ratio
│   │                                  │   │
│   │  [✂ Crop] [▦ Align] [◐ BG] [T Text]│ ← tool row
│   │                                  │   │
│   │   [ Simpan ]        [ Share  → ] │   │ ← CTA
│   ╰──────────────────────────────────╯   │
└──────────────────────────────────────────┘
```

Kunci visual:
- Kartu editor radius 28 dp, shadow besar (elevation 24 dp), lebar 94%.
- Selalu ada tombol minimize `[⌄]` (bila bubble aktif) di kanan atas.
- Tool row 4 item — **Teks** ditambahkan, jadi total 4 tool + tombol rotate `[↻]` di top bar (atau 5 item scroll).
- CTA: Share primary (kanan) karena tujuan utamanya mengirim, Simpan secondary (kiri).

## Bubble

```
    ╭──────╮
    │ [🖼] │  ← 56dp, thumbnail gambar, ring progres maroon saat ditahan
    ╰──────╯
      ▁▁▁▁     ← 3 titik grip kecil (opsional, menandakan bisa digeser)
```

- Menempel ke tepi layar, sedikit keluar (offset -8 dp) agar terasa "terselip".
- Saat idle: opacity 0.7, ukuran 60%.
- Saat ditahan: ring progres 1.6 s + teks "Lepas untuk batal".

## State kosong / loading / error

| State | Tampilan |
|---|---|
| Decoding | skeleton shimmer pada viewport, chips disabled |
| HEIC gagal | card di viewport: ikon + "Format ini belum didukung" + tombol "Pilih gambar lain" |
| Tanpa sesi | tampilan "Bagikan gambar ke FloatPic dari aplikasi mana pun" + tombol "Buka Galeri" |
| Sesi dipulihkan | banner kecil di atas viewport: "Sesi dipulihkan · Mulai baru" |

## Arah desain — spesifikasi untuk dirender ulang

Kalau ingin mockup yang lebih "jadi" (di Figma / image gen), pakai brief berikut:

**Tema Pastel Maroon (hero shot):**
- Latar: cream `#F6E7E4` dengan blob maroon besar blur di kiri atas.
- Kartu editor: sugar `#FFF9F7`, radius 28, shadow lembut hangat.
- Chip aktif: maroon `#7A0C2E` solid, teks sugar.
- Chip tidak aktif: transparan, border 1 dp maroon 25%, teks plum.
- Viewport: background gradient maroon gelap → plum; gambar mobil/kopi (karena ini konteks share dari chat).
- Teks caption: "akhir pekan di Pekalongan" putih stroke hitam, atas-tengah.
- Bubble: pojok kanan tengah, thumbnail sama, ring maroon.
- Skrim di belakang menampilkan app chat samar (WA-like), untuk menunjukkan konteks "tidak keluar app".

**Tema Neumorphism (hero shot 2):**
- Latar `#E9ECF2`, viewport card timbul dengan bayangan ganda.
- Chip rasio seperti tombol bulat kecil "pressed" saat aktif.
- Aksen indigo `#5B6CFF` hanya di CTA Share.
- Gambar sama, untuk membandingkan keterbacaan.

## Mockup yang sudah dibuat

Lihat folder `docs/mockups/`:
- `01-maroon-editor.png` — layar editor, tema pastel maroon (hero).
- `02-neu-editor.png` — layar editor, tema neumorphism.
- `03-bubble-flow.png` — tiga frame: minimize → bubble → ditahan untuk close.

> Anggap mockup sebagai arah komposisi & nuansa warna, bukan pixel-perfect. Detail ikon, spasi, dan tipografi tetap mengikuti dokumen 09.
