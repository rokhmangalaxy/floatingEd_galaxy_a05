# 01 — Identitas: `com.emrobinso.{namaApp}`

Satu keputusan identitas yang diambil sekarang akan mengunci semua yang lain (Play Store URL, package name, ikon, nama share dialog). Jadi jangan skip.

## Struktur package yang disarankan

```
com.emrobinso.<app>                 → aplikasi (UI, share target)
com.emrobinso.<app>.feature.editor  → layar floating editor + core engine crop/ratio
com.emrobinso.<app>.core.*          → model, renderer (Canvas → Bitmap), persistence
com.emrobinso.<app>.data.*          → Room (ProjectStore), DataStore (settings), preset, history
com.emrobinso.<app>.util.*          → EXIF, bitmap hints, color, gesture helpers
```

> Kenapa `feature.editor` dipisah? Karena kelak kalau mau bikin app "maker" (poster/stamp/IG Story) reuse engine yang sama tanpa copy-paste. Modul terpisah juga bikin Gradle build paralel, enak kalau project mulai besar.

## Nama app — tolong pilih sekarang

Pakai tagline dulu (`emrobinso floatingEd`)? Atau nama produk?

Beberapa opsi (hindari generik seperti "Photo Editor" — susah ditemukan & konflik Play Store):

| Nama | Nuance | Catatan |
|---|---|---|
| **Floaty** | playful, ikon mudah (balon + gambar) | `Float` sudah dipakai beberapa app; kombinasikan `Floaty Edit` |
| **PicFloat** / **FloatPic** | deskriptif: edit gambar secara floating | mudah diingat, keyword ok |
| **FrameFloat** | menonjolkan framing/rasio | agak sempit kalau ke depan bikin maker |
| **Glide** / **GlideEdit** | gesture-heavy, halus | rawan konflik merek |
| **StickerShot** | playful + share/story oriented | bagus untuk segmen medsos |
| **Tumpuk** (lokal) | sangat Indonesia, unik | bagus untuk pasar lokal, jelek untuk global |
| **emro** | singkat, brandable | tidak jelas fitur; ok untuk portfolio |

**Rekomendasi saya:** `FloatPic` (`com.emrobinso.floatpic`) dengan tagline "Edit gambar secara mengambang". Alasan:
1. Keyword "pic/photo" + "float" membantu discovery,
2. tidak menyebut "editor khusus" (bisa grow ke maker/batch),
3. ikon jelas (kartu foto melayang di atas kanvas).

Kalau mau gaya lebih berkarakter → `Tumpuk` (lokal) atau `Floaty`.

## Deliverable identitas yang harus ada hari pertama

- [ ] `applicationId` final + tidak ada `.debug` suffix di release (atau pakai flavor, jangan suffix).
- [ ] `app/icon-playstore-512.png`, `feature-graphic-1024x500.png` (wajib Play), adaptive icon (foreground + background vector).
- [ ] Nama app di `strings.xml` (`app_name`), `activity-alias` share target (**lihat doc 03 — ini sensitif**).
- [ ] `namespace` Kotlin sama dengan `applicationId`.
- [ ] Deep link scheme ringan: `floatpic://` untuk "buka editor" dari notifikasi/share eksternal.
- [ ] `versionCode` discipline: versionCode naik tiap AAB, di-play sudah tidak bisa turun.

## Prinsip identitas yang menentukan UX nanti

1. **Ikon = gesture hint.** Kalau ikon menunjukkan foto yang "ditarik keluar" dari bingkai, user langsung paham ini bukan editor penuh tapi quick-touch.
2. **Nama di share sheet = nama app.** Di Android 10+ share sheet menampilkan label activity (`PhotoEditorActivity` akan jelek). Pakai `<label>` singkat: "FloatPic — Edit".
3. **Satu nama untuk satu janji.** Jangan ada "FloatPic Free" dan "FloatPic Pro" sebagai app terpisah — pakai IAP.

## Pertanyaan yang perlu dijawab sebelum lanjut coding

1. Nama final & tagline?
2. Minimum OS: **Android 8 (API 26)** direkomendasikan (share target stabil, `AdaptiveIconDrawable`, `HEIF` masuk) — atau Android 7 kalau perlu coverage device lama?
3. Target pasar awal: Indonesia saja atau global? (menentukan copywriting & harga IAP)
