# floatingEd_galaxy_a05

**FloatPic** — editor gambar "mengambang": share gambar dari aplikasi mana pun, atur rasio, crop, align, background, dan teks di jendela kecil yang melayang — lalu simpan, share, atau minimize ke bubble.

> Status: **v0.1 jalan di perangkat.** APK dibangun otomatis di GitHub Actions.

## Unduh APK (selalu versi terbaru, link tetap)

**[⬇ FloatPic-latest.apk](https://github.com/rokhmangalaxy/floatingEd_galaxy_a05/releases/download/apk-latest/FloatPic-latest.apk)**

- Build berikutnya bisa **dipasang menimpa** tanpa uninstall (kunci & `applicationId` sama, `versionCode` selalu naik).
- Halaman rilis: [`releases/tag/apk-latest`](https://github.com/rokhmangalaxy/floatingEd_galaxy_a05/releases/tag/apk-latest) — berisi `versionName`, `versionCode`, dan `SHA256SUMS.txt` terbaru.
- Detail tanda tangan & CI: [`docs/16-build-apk.md`](docs/16-build-apk.md).

## Fitur yang sudah ada di v0.1

Share target (single + multiple) · launcher biasa input galeri · floating editor L1 · rasio populer (Asli, 1:1, 4:5, 9:16, 16:9, 3:2, 4:3, 2:3, 3:4, 5:4, 9:20) · **Crop** (fill/fit, reset) · **Posisi** (9 titik + geser bebas) · latar solid/gradasi/blur/pattern + ambient color · padding, sudut, shadow · pinch-zoom, rotate + **snap haptic**, limit haptic · **Cermin** horizontal/vertikal · teks satu lapis (atas/bawah, putih⇄hitam stroke) · undo/redo · simpan ke galeri · share · **minimize ke bubble** + hold **1,6 s** → ring + haptic tick → getar → close app · tema **Neumorphism** / **Pastel Maroon**.

## Mulai dari sini

- [`docs/README.md`](docs/README.md) — indeks + ringkasan rekomendasi.
- [`docs/01-branding-identitas.md`](docs/01-branding-identitas.md) — nama & package `com.emrobinso.{namaApp}`.
- [`docs/03-arsitektur-share-target.md`](docs/03-arsitektur-share-target.md) — cara share masuk ke editor.
- [`docs/11-open-questions.md`](docs/11-open-questions.md) — keputusan terkunci & pertanyaan tersisa.
- [`docs/15-preview-visual.md`](docs/15-preview-visual.md) + [`docs/mockups/`](docs/mockups) — arah visual.

## Ringkas fitur v1

Share target (single & multiple) · floating editor (L1) · rasio populer (Asli, 1:1, 4:5, 9:16, 16:9, 3:2, 4:3, …) · crop + align 3×3 · background solid/gradient/blur/auto-color · pinch-zoom, rotate + snap · teks satu lapis (atas/bawah, putih⇄hitam stroke) · undo/redo · simpan & share · minimize ke bubble · hold bubble → getar → close · tema Neumorphism & Pastel Maroon.
