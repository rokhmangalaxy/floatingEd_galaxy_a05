# Brainstorming — FloatPic (`com.emrobinso.{namaApp}`)

Ringkasan ide lo: **app jadi share target** — user share gambar dari app mana pun → muncul opsi app ini → floating editor terbuka dengan gambar sebagai main preview → atur **rasio populer, crop, align, background**, plus **pinch-out untuk resize & rotate**, lalu simpan/share balik.

Dokumen ini dipecah per topik. Baca yang relevan, tandai jawabannya di `11-open-questions.md`.

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
| 11 | [Open Questions](11-open-questions.md) | Semua keputusan yang harus dijawab |

## Ringkasan rekomendasi (TL;DR)

1. **Nama:** `FloatPic` — `com.emrobinso.floatpic`.
2. **Posisi:** app ini adalah **"bridge"**, bukan editor penuh. Share → edit cepat → balik ke chat. Jangan bersaing dengan Picsart/Canva di fitur; menangkan di kecepatan & konteks.
3. **Floating:** mulai dari **L1** (activity transparan, editor card melayang + skrim) — 90% efek wow, 10% risiko. Arsitektur siap naik ke **L2** (overlay window sungguhan, bubble) tanpa menulis ulang editor.
4. **Fitur MVP:** rasio populer (Asli/1:1/4:5/9:16/16:9/…), crop + align grid 3×3, background solid/gradient/blur/auto-color, pinch-zoom + rotate + snap + clamp, undo/redo, simpan & share, draft.
5. **Pembeda yang saya sarankan (pilih 1-2):**
   - **Preset kontekstual** — deteksi screenshot/potret/meme lalu tawarkan preset yang pas (murah, terasa pintar).
   - **Batch edit** — share 5–9 gambar, satu rasio/background untuk semua (target jualan online / carousel IG).
   - **Ambient color background** — background gradient otomatis dari warna gambar; efek desainer tanpa effort.
   - **Floating bubble (fase 2)** — jika mau jadi ciri khas teknis yang benar-benar beda.
6. **Satu aturan teknis yang tidak boleh dilanggar:** preview dan export memakai **renderer yang sama**, dan semua tool memodifikasi satu `EditorState` (transform + background + aspect). Ini yang membuat undo, preset, batch, dan overlay-mode jatuh dengan gratis.
7. **Detail kecil yang paling terasa "mahal":** snap rotate dengan haptic, transisi rasio tanpa gambar melompat, blur background pre-render, dan tidak adanya dialog konfirmasi untuk hal yang bisa di-undo.

## Langkah selanjutnya

Buka `11-open-questions.md`, jawab (atau tulis "pakai rekomendasi"), lalu saya bisa langsung:
- menyusun README produk final, atau
- mendirikan skeleton project (Gradle + Compose + manifest share target) di repo ini, atau
- mulai dari renderer + viewport editor (inti yang paling menentukan).
