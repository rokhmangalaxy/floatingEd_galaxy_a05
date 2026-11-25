# 03 — Arsitektur Share Target & "Floating" Editor

Ini dokumen paling teknis: bagaimana share dari app lain bisa mendarat di editor mengambang, dan di mana floating "boleh" berada.

## 1. Deklarasi manifest (bagian yang biasanya bikin gagal)

```xml
<activity
    android:name=".feature.editor.EditorActivity"
    android:exported="true"
    android:label="FloatPic"
    android:theme="@style/Theme.FloatPic.Translucent"
    android:launchMode="singleTop"
    android:windowSoftInputMode="adjustResize">

    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
        <data android:mimeType="image/jpeg" />
        <data android:mimeType="image/png" />
        <data android:mimeType="image/webp" />
        <data android:mimeType="image/heic" />
        <data android:mimeType="image/heif" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.SEND_MULTIPLE" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.EDIT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>
</activity>
```

**Poin yang sering salah:**
- **`exported="true"` wajib** (Android 12+ mewajibkan eksplisit). Kalau lupa, filter diabaikan dan share sheet tidak menampilkan app.
- **`theme` transparan**: `windowIsTranslucent=true`, `windowBackground=@android:color/transparent`, `windowNoTitle` → supaya editor "mengambang" di atas app asal terlihat.
- **Multi `<data>` dalam satu filter**: beberapa OEM (terutama versi lama) hanya membaca wildcard `image/*`; itulah kenapa MIME spesifik didaftarkan juga.
- **`launchMode="singleTop"`**: user share dua kali beruntun dari chat — jangan spawn dua editor.
- **`persistableUriPermission`** tidak wajib untuk share (grant bersifat sementara) — tapi jika kamu menyimpan state/draft, salin bitmap/copy ke `cacheDir` sebelum proses berakhir.

## 2. Menerima intent

```kotlin
val uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
val uris: List<Uri> = when {
    intent.action == Intent.ACTION_SEND_MULTIPLE ->
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
    else -> listOfNotNull(uri)
}
```

- `EXTRA_STREAM` bisa berupa `Uri` pointer dengan grant sementara → **buka segera** dan decode.
- MIME mungkin tidak akurat (`application/octet-stream` dari app aneh) → andalkan `contentResolver.getType(uri)` + sniffing magic bytes.
- File bisa ada di `content://com.whatsapp.provider.media/...` (temporary); jangan simpan URI jangka panjang — salin.

## 3. Decoding gambar dengan aman

```kotlin
val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }

val sample = calculateInSampleSize(opts, targetW = 2048, targetH = 2048)

BitmapFactory.decodeStream(resolver.openInputStream(uri)!!, null,
    BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = ARGB_8888 })
```

- `inSampleSize` ke ~2048 px untuk preview; **full-res hanya saat export** (buka ulang stream, ini bisa 2x decode — biarkan, tapi ukur).
- `ExifInterface(inputStream)` → `ORIENTATION_ROTATE_90/180/270`, `FLIP_HORIZONTAL/VERTICAL`. Terapkan dengan `Matrix` + `Bitmap.createBitmap` **sebelum** masuk state (atau simpan seperti itu di state transform awal).
- **HEIC**: API 28+ via `ImageDecoder` (mendukung orientation otomatis) — kalau gagal, fallback `BitmapFactory`; bila tetap gagal → snackbar "Format HEIC tidak didukung di perangkat ini" + tawarkan konversi eksternal. Jangan crash.
- **OOM guard**: `ActivityManager.memoryClass` < 256 MB → target preview 1280 px; `Bitmap.recycle()` preview lama setelah ganti gambar.

## 4. L1 — "Mengambang secara visual" (rekomendasi awal)

Semua gesture editor menempel pada satu `Card` (`Surface` + `shadowElevation`, `shape = RoundedCornerShape(28.dp)`) yang diposisikan di tengah dengan padding aman; skrim `Modifier.background(Color.Black.copy(alpha = .32f))` + tap di luar = tutup.

```kotlin
Box(Modifier.fillMaxSize().background(Black.copy(.32f))) {
  EditorCard(
    Modifier
      .align(Alignment.Center)
      .padding(bottom = 24.dp, top = 24.dp)
      .fillMaxWidth(.94f)
      .heightIn(max = 560.dp)
      .graphicsLayer {
          scaleX = enterAnim; scaleY = enterAnim; alpha = enterAnim
      }
  ) { EditorScreen(state) }
}
```

Transisi masuk: `scale .92 → 1`, `alpha 0 → 1`, 220 ms `FastOutSlowIn`. Ini satu-satunya "magic" yang dibutuhkan untuk terasa mengambang.

Di device **tablet/freeform** (Android 12L+), activity dengan `windowMode` sudah otomatis bisa di-resize oleh user → editor harus responsif (`BoxWithConstraints`, bukan hard size). Bonus: bisa ditambahkan ke "Split screen".

## 5. L2 — floating window sungguhan (fase 2)

Dua jalur, keduanya pernah dipakai app populer:

**Jalur A — overlay service.** `Service` foreground (`FOREGROUND_SERVICE_SPECIAL_USE` + tipe yang benar di Android 14), `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`, permission `SYSTEM_ALERT_WINDOW` (`Settings.canDrawOverlays`). Kelebihan: benar-benar di atas semua app; bisa di-drag bebas; bisa minimize ke bubble. Kekurangan: izin khusus (onboarding "Beri izin melayang"), beberapa OEM kill service, keyboard untuk input bisa rewel, tidak bisa `startActivityForResult`.

**Jalur B — Activity freeform / picture-in-picture.** PiP hanya untuk video/mirroring (batasan platform) → bukan pilihan. Freeform hanya di device tertentu. Jadi jalur realistis = overlay service.

**Rekomendasi:** arsitekturkan editor sebagai `EditorHost`:
```kotlin
interface EditorHost {
    fun render(state: EditorState)   // Compose content di dalam host
    fun onDismiss()
}
```
`ActivityHost` (L1) dan `OverlayServiceHost` (L2) memungkus konten yang sama. Jangan tulis editor dua kali.

## 6. Returning result ke app asal

- Dari share target: `finish()` setelah share/save → user kembali ke chat **otomatis** (karena app asal masih di back stack). Ini alasan L1 sudah cukup "cepat kembali".
- Kalau pakai `ActivityResult` (share via `ACTION_SEND` lagi) — **jangan**; membuat loop. Cukup share sendiri + finish.
- `setResult(RESULT_OK, intentWithUri)` hanya relevan untuk `ACTION_EDIT` (app galeri expect hasil edit). Dukung ini untuk VIEW/EDIT handler.
- **Jangan finish otomatis setelah share sheet tampil** kalau share gagal — verifikasi `chooser` tidak `null`.

## 7. Theme & look "mengambang"

```xml
<style name="Theme.FloatPic.Translucent" parent="android:Theme.Material.Light.NoActionBar">
  <item name="android:windowIsTranslucent">true</item>
  <item name="android:windowBackground">@android:color/transparent</item>
  <item name="android:backgroundDimEnabled">false</item>
  <item name="android:windowNoTitle">true</item>
  <item name="android:statusBarColor">@android:color/transparent</item>
  <item name="android:navigationBarColor">@android:color/transparent</item>
  <item name="android:windowLightStatusBar">false</item>
</style>
```
(Compose theme: `MaterialTheme(colorScheme = darkColorScheme(...))` — editor sebaiknya selalu dark agar konten gambar menonjol.)

## 8. Risiko teknis yang harus dijawab sebelum mulai

| Risiko | Dampak | Mitigasi |
|---|---|---|
| OOM pada gambar 50 MP | crash di share | downscale + full-res lazy |
| OEM share sheet tidak menampilkan app | tidak ada pengguna | daftar MIME spesifik + test di Xiaomi/Samsung/Oppo |
| URI provider temporer | gambar hitam setelah beberapa saat | salin ke cache saat masuk |
| HEIC | error decode | fallback + pesan jelas |
| Gesture bar vs handle crop | crop tidak bisa di-tap | padding safe area + hit target 48dp |
| Izin overlay (L2) | user bingung | onboarding bertahap + "coba tanpa izin dulu" |

## Keputusan

1. L1 atau L2 untuk v1? (rekomendasi L1, arsitektur siap L2)
2. Dukung VIEW (default image viewer) di v1? ini menambah eksposur tapi menambah jalur kode. Rekomendasi: ya, header saja.
3. Simpan hasil share ke cache (rekomendasi) atau langsung MediaStore?
