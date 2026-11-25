# 10 — Rekomendasi Tech Stack & Struktur Modul

## Stack yang saya rekomendasikan

| Lapisan | Pilihan | Alasan |
|---|---|---|
| Bahasa | Kotlin | standar modern |
| UI | **Jetpack Compose** (BOM stabil) | gesture `pointerInput`/`transformable` jauh lebih nyaman; preview tool apa pun mudah |
| Navigasi | **tanpa navigation-compose untuk editor** — satu Activity + state | editor adalah satu layar; navigasi menambah delay & fragility |
| Gambar | `BitmapFactory` + `ImageDecoder` (API 28+) | tanpa dependency tambahan; kontrol penuh |
| Image loading thumbnail | Coil | ringan, dukung EXIF, MVVM compose |
| State | `ViewModel` + `StateFlow`/`MutableState` + immutable `EditorState` + undo stack | mudah dites |
| Persistence | **Room** (draft & preset) + **DataStore** (preferensi) | Room untuk list draft; DataStore untuk setting ringan |
| DI | Hilt atau manual factory | untuk project tunggal, manual + `viewModel(factory=...)` cukup; Hilt kalau modul banyak |
| Gesture | Compose `pointerInput` manual | kontrol snap/clamp |
| IAP | Play Billing (nanti) | jangan di MVP |
| Analytics | (opsional) Firebase/Play Analytics minimal — **privasi mode**, matikan oleh default | doc 08 B3 |
| Blur | pre-blur bitmap (StackBlur) | API konsisten, tidak tergantung RenderEffect |
| EXIF | `androidx.exifinterface` | standar |

**Yang saya hindari:** library image-editor siap pakai yang monolithic (mis. fork "android-image-crop" yang tidak bisa menyatukan state), OpenGL untuk v1 (lebih cepat tapi memperlambat iterasi), Compose Canvas + `drawImage` langsung bitmap original (boros memori).

## Struktur modul Gradle (pragmatis, bukan over-engineer)

```
app/                                  ← activity, manifest, share target
core/                                 ← model(EditorState, Transform), renderer, utils(exif, bitmap)
feature/editor/                       ← EditorScreen, tool panels, gestures
data/                                 ← Room, DataStore, MediaStore writer, preset repo (opsional: gabung ke app dulu)
```

Untuk tim satu orang, 3 modul (`app`, `core`, `feature:editor`) cukup; modul memakan build time. Naik ke lebih modular hanya bila build >60s.

## Struktur paket

```
com.emrobinso.floatpic
├── app/            (MainActivity, EditorActivity, ShareIntentHandler, di)
├── core/
│   ├── model/      EditorState, Transform, Background, Aspect
│   ├── render/     EditorRenderer (preview & export satu fungsi)
│   ├── image/      ImageLoader(Exif, sampling, heic), BitmapUtils
│   └── ui/         theme, dimens
├── feature/editor/ EditorViewModel, EditorScreen, panels/*, gesture/*, EditorHost/*
├── data/
│   ├── local/      Room: DraftEntity, DraftDao, PresetEntity
│   ├── prefs/      DataStore
│   └── export/     MediaStoreExporter, ShareHelper, ClipboardHelper
└── ...
```

## Renderer bersama (kunci konsistensi)

```kotlin
object EditorRenderer {
    fun draw(
        canvas: Canvas,
        state: EditorState,
        targetSize: IntSize,      // ukuran output
        bitmap: Bitmap,
        quality: FilterQuality,
    )
}
```
- Preview: `targetSize` = ukuran viewport (px), bitmap = previewBitmap.
- Export: `targetSize` = output, bitmap = fullBitmap.
- **Satu fungsi**, diuji dengan golden test (render 100 px & bandingkan hash) — ini mencegah drift preview/export.

## Testing yang layak (jangan berlebihan)

- **Unit:** clamp transform, mapping rasio→output size, sampling calculation, EXIF matrix.
- **Golden test renderer:** gambar kecil 32×32, state kombinasi (rotasi 45 + gradient + padding) → bandingkan pixel sample.
- **Instrumented:** share intent masuk dengan Uri fixture → editor tampil.
- **Manual matrix device:** Samsung (One UI share sheet), Xiaomi (MIUI permission agresif), Pixel (AOSP), Oppo (ColorOS). Ini realistis & wajib untuk pasar ID.

## Performa

- Target pertama frame editor < **800 ms P90** untuk 12 MP.
- Decode di `Dispatchers.Default`, ukur dengan Macrobenchmark (opsional di awal).
- `bitmap.prepareToDraw()`, hindari `Bitmap.createBitmap` berulang.
- Jangan jalankan blur di main thread.
- Recompose: `Canvas` dengan `graphicsLayer` + `drawWithCache`; state transform disimpan sebagai `mutableFloatStateOf` untuk menghindari recompose seluruh layar saat gesture.

## Konfigurasi build

- `compileSdk 35`, `minSdk 26`, `targetSdk 35`.
- `android.nonTransitiveRClass=true` (bila multi-modul), `viewBinding` off (Compose).
- Signing: keystore jangan di repo (lewat env/CI); `.gitignore` sudah harus mencakup `*.jks`, `local.properties`, `.idea/`.
- R8 aktif; simpan rules untuk `androidx` (default cukup).
- `README.md` diisi cara build + screenshot (nanti).

## Yang perlu diputuskan sebelum setup

1. Compose murni atau View + custom composable? → **rekomendasi Compose murni**.
2. Hilt sekarang atau manual DI? → **manual dulu**, Hilt saat modul >4.
3. Modul tunggal dulu vs 3 modul? → **3 modul sejak awal** (`app`, `core`, `feature:editor`) karena pemisahan renderer mencegah coupling; masih cepat build.
4. Package final name → doc 01.

## Kalau pakai Compose — contoh skeleton layar

```kotlin
@Composable
fun EditorScreen(vm: EditorViewModel, onExit: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar(onExit)
        EditorViewport(state = s, onTransform = vm::transform, modifier = Modifier.weight(1f))
        RatioChips(s.aspect, vm::setAspect)
        ToolRow(onCrop = vm::crop, onAlign = vm::align, onBg = vm::background, onRotate = vm::rotate)
        ActionBar(onSave = vm::save, onShare = vm::share)
    }
}
```
Ringkas, dan semua logika di VM (mudah upgrade ke overlay host).
