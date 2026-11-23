# Brainstorming — FloatPic (`com.emrobinso.{namaApp}`)

Ringkasan ide lo: **app jadi share target** — user share gambar dari app mana pun → muncul opsi app ini → floating editor terbuka dengan gambar sebagai main preview → atur **rasio populer, crop, align, background**, plus **pinch-out untuk resize & rotate** → tambah **teks sederhana**, simpan/share, **minimize ke bubble**, long-press bubble → getar → close. Dua tema: **Neumorphism** & **Pastel Maroon 500% saturasi**.

Dokumen dipecah per topik. Keputusan yang sudah diambil ada di `11-open-questions.md`.

| # | Dokumen | Isi |
|---|---|---|
| 01 | [Identitas](01-branding-identitas.md) | Package `com.emrobinso.*`, opsi nama, ikon, play store |
| 02 | [Visi & Positioning](02-arahan-visi.md) | "Bridge" vs "editor penuh", 3 level floating (L0/L1/L2), batasan share target |
| 03 | [Arsitektur Share Target](03-arsitektur-share-target.md) | Manifest, decode gambar, HEIC/EXIF, L1 vs L2 floating window |
| 04 | [Kanvas & Viewport](04-kanvas-crop-rasio.md) | Model transform, satu renderer preview=export, crop semantics |
| 05 | [Background](05-background.md) | Tier fitur, ambient color otomatis, wallpaper mode |
| 06 | [Rasio & Preset](06-rasio-preset-align.md) | Urutan rasio populer, fit vs fill, align grid, preset |
| 07 | [Gesture](07-gesture-pinch-rotate.md) | Pinch/rotate/pan yang benar, clamp, snap, QA matrix |
| 08 | [Saran Fitur Lain](08-saran-fitur.md) | 10 fitur murah, 6 pembeda, roadmap MVP→v0.3 |
| 09 | [UX Layout](09-ux-layout.md) | Anatomi layar, panel bottom sheet, mikro-interaksi, a11y |
| 10 | [Tech Stack](10-tech-stack.md) | Compose, Room, renderer bersama, struktur modul |
| 11 | [Open Questions](11-open-questions.md) | Keputusan terkunci + pertanyaan tersisa |
| 12 | [Teks Sederhana](12-teks-sederhana.md) | Atas/bawah tengah, putih⇄hitam stroke, auto-fit, render |
| 13 | [Bubble & Close](13-bubble-minimize.md) | Overlay bubble, izin, snap tepi, hold 1.6 s + haptic close |
| 14 | [Tema](14-tema.md) | Neumorphism & Pastel Maroon, token, risiko, toggle |
| 15 | [Preview Visual](15-preview-visual.md) | Spesifikasi tampilan + mockup di `mockups/` |

## Mockup

- [`mockups/01-maroon-editor.png`](mockups/01-maroon-editor.png) — layar editor, tema Pastel Maroon (hero).
- [`mockups/02-neu-editor.png`](mockups/02-neu-editor.png) — layar editor, tema Neumorphism.
- [`mockups/03-bubble-flow.png`](mockups/03-bubble-flow.png) — alur minimize → bubble → hold-close.

## Ringkasan rekomendasi (TL;DR)

1. **Nama:** `FloatPic` — `com.emrobinso.floatpic` (bisa diganti, lihat doc 01).
2. **Posisi:** app ini adalah **"bridge"**, bukan editor penuh. Share → edit cepat → balik ke chat. Menangkan di kecepatan & konteks, bukan jumlah tool.
3. **Floating:** mulai **L1** (kartu mengambang di activity transparan + skrim) — 90% efek wow, 10% risiko. **Bubble** ditambahkan sebagai launcher (bukan editor di overlay), jadi effort sedang dan arsitektur tetap bersih.
4. **MVP:** rasio populer, crop + align grid 3×3, background solid/gradient/blur/auto-color, pinch-zoom + rotate + snap + clamp, **teks satu lapis** (atas/bawah, putih-stroke-hitam ⇄ hitam-stroke-putih), undo/redo, simpan & share, draft, minimize ke bubble.
5. **Pembeda:** **floating bubble + hold-to-close** (khas, mudah di-demo) dan **ambient color background**. Batch edit & preset kontekstual menyusul di v0.2.
6. **Satu aturan teknis yang tidak boleh dilanggar:** preview & export memakai **renderer yang sama**, semua tool memodifikasi satu `EditorState` (transform + background + aspect + text). Undo, preset, batch, restore dari bubble — semuanya gratis dari keputusan ini.
7. **Detail kecil yang paling terasa "mahal":** snap rotate dengan haptic, transisi rasio tanpa gambar melompat, blur background pre-render, teks live-preview saat mengetik, dan tidak ada dialog untuk hal yang bisa di-undo.

## Langkah selanjutnya

Pilih salah satu:
- **Skeleton project Android** (Gradle + Compose + manifest share target + struktur modul) — fondasi langsung jalan.
- **Renderer + editor viewport** — inti paling menentukan (state, render preview=export, gesture).
- **README produk final** — positioning & feature list v1 untuk acuan/desainer/Play Store.
