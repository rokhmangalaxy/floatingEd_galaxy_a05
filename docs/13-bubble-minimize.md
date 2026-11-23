# 13 — Minimize ke Floating Bubble + Long-Press Haptic Close

Kamu pilih **bubble** sebagai ciri khas, tapi level floating sebelumnya **L1** (kartu mengambang di dalam activity). Dua hal ini bertabrakan secara teknis: bubble yang benar-benar melayang di atas app lain **wajib** memakai overlay window (level L2) + izin `SYSTEM_ALERT_WINDOW`. Dokumen ini menyelesaikan ketegangan itu dengan arsitektur bertahap.

## Keputusan arsitektur (rekomendasi)

**Bubble = launcher, editor tetap di Activity.**

- Jangan render editor penuh di dalam overlay window. Keyboard, IME, ukuran dinamis, dan OEM quirks akan memakan waktu berbulan-bulan.
- Saat **minimize**: simpan `EditorState` + salinan gambar ke cache → tutup activity (`finishAndRemoveTask`) → service menampilkan bubble kecil di tepi layar.
- Saat **tap bubble**: `startActivity` editor dengan `EXTRA_RESTORE_DRAFT` → state kembali persis.
- Saat **long-press bubble**: haptic + ring progres → close app (service + task + cache).
- Editor tetap L1: cepat dibangun, dan bubble hanya menambah "cara kembali" yang unik.

Ini memberi 95% pengalaman "minimize ke bubble" dengan ~30% effort dari editor-di-overlay.

## Izin & onboarding

1. Tombol **Minimize** pertama kali → bottomsheet penjelasan:
   - "Izinkan tampil di atas aplikasi lain agar sesi edit bisa disimpan sebagai bubble."
   - Tombol "Beri izin" → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` dengan `package:` URI.
2. Setelah kembali, cek `Settings.canDrawOverlays(context)`:
   - `true` → lanjut minimize.
   - `false` → tampilkan toast singkat + jangan paksa; tombol minimize tetap ada, tap berikutnya menawarkan lagi.
3. **OEM (Xiaomi/Oppo/Vivo):** izin tambahan "Popup window while running in background" kadang diperlukan. Deteksi brand, tampilkan help card dengan langkah; jangan auto-redirect ke Settings OEM (rapuh).
4. **Android 14+:** foreground service wajib deklarasi tipe. Pakai `specialUse`:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/> <!-- notif FGS -->
<uses-permission android:name="android.permission.VIBRATE"/>           <!-- getar close -->

<service
    android:name=".feature.bubble.BubbleService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="floating editor session bubble" />
</service>
```

> Play Console akan meminta penjelasan penggunaan `SYSTEM_ALERT_WINDOW` saat review. Jawab: "menampilkan bubble untuk melanjutkan sesi edit gambar setelah diminimize". Ini penggunaan sah; banyak app serupa lolos.

## Bubble window

```kotlin
val params = WindowManager.LayoutParams(
    dp(56), dp(56),
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = screenW - dp(72); y = screenH / 2
}
```

- Ukuran 56 dp, ikon thumbnail gambar yang sedang diedit (bukan logo) — user tahu bubble itu milik sesi mana.
- Drag bebas; saat dilepas, **snap ke tepi kiri/kanan** (animasi 180 ms + haptic ringan).
- Diam 3 detik → bubble menyusut jadi 60% & opacity 0.7 (tidak menutupi konten).
- `FLAG_NOT_FOCUSABLE` wajib; bubble tidak boleh mencuri keyboard.
- Klik = restore; long-press = close (lihat bawah).
- **Jangan** buat bubble full-screen atau menutupi area besar: risiko ditolak user + Play policy.

## Close app: long-press 1.6 s + getar

Desain yang aman (mencegah salah tutup):

```
0 ms      : tekan lama mulai → ring progres (merah maroon) mengelilingi bubble
600 ms    : haptic tick ringan (HapticFeedbackConstants.CLOCK_TICK)
1200 ms   : haptic tick kedua, ring 75%
1600 ms   : getar kuat (LONG_PRESS) → bubble scale 1.15 → fade out → app close
```

- **Lepas sebelum 1600 ms** → ring reset, haptic "cancel", tidak terjadi apa-apa.
- Teks kecil muncul di bawah bubble saat menahan: "Lepas untuk batal · Tahan untuk tutup".
- Close app = `stopService` + `finishAndRemoveTask` (bila activity masih hidup) + hapus cache sesi + hapus notifikasi + hapus bubble.
- **Draft:** karena user menutup app secara sengaja, hapus cache share. Riwayat draft (fitur A1 doc 08) opsional dan **terpisah** — jangan diam-diam menyimpan gambar dari chat (privasi).

Haptik tanpa izin: `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)`. Getaran kustom (pola) butuh `Vibrator` + `VIBRATE` (normal permission, tanpa dialog). Rekomendasi: pakai `performHapticFeedback` untuk semua, hindari `Vibrator` — lebih hemat dan tidak butuh izin.

## State & restore

```kotlin
// Minimize
draftStore.save(EditorStateSnapshot(
    sourceUri = cachedCopyUri,      // salin dulu! URI share bisa kedaluwarsa
    state = stateJson,
    savedAt = now,
))
activity.finishAndRemoveTask()
BubbleService.start(context, previewBitmap, sessionId)

// Restore (tap bubble)
Intent(context, EditorActivity::class.java)
    .putExtra(EXTRA_RESTORE_SESSION, sessionId)
    .addFlags(FLAG_ACTIVITY_NEW_TASK)
```

- Simpan `state` sebagai JSON (kotlinx.serialization) — bukan Parcelable (supaya tahan restart).
- Salin gambar sumber ke `cacheDir/session/` saat minimize; hapus saat close atau setelah 24 jam (WorkManager cleanup).
- Background activity start: app dengan `SYSTEM_ALERT_WINDOW` dan overlay yang **terlihat** dikecualikan dari larangan. Bubble adalah overlay yang terlihat → `startActivity` saat tap diizinkan. Tetap siapkan fallback notification "Lanjutkan edit" bila gagal di OEM tertentu.

## Lifecycle & edge case

| Kasus | Perilaku |
|---|---|
| Layar mati | Hapus window bubble (`ACTION_SCREEN_OFF`), service tetap; tampilkan lagi saat `SCREEN_ON` |
| Rotasi | Re-add bubble dengan posisi tepi yang sama |
| Izin overlay dicabut saat service jalan | Tangkap `BadTokenException` → stop service + toast |
| Service dibunuh sistem | `START_STICKY`, restore bubble dari `sessionId` di `SharedPreferences` |
| Bubble saat editor dibuka | Sembunyikan/stop bubble, tampilkan lagi saat minimize |
| Dua sesi | Satu bubble saja per app; sesi baru menggantikan sesi lama (konfirmasi kecil) |
| Baterai | Service tanpa wakelock, tanpa polling. Bubble idle = 0 CPU |
| Notifikasi FGS | "FloatPic sedang melayang · Ketuk untuk buka" + aksi "Tutup" |

## Roadmap fitur ini (jujur soal effort)

| Tahap | Isi | Effort |
|---|---|---|
| v1 (MVP) | L1 + tombol Minimize (simpan draft, keluar) tanpa overlay | 0.3 hari |
| v1.1 | Bubble overlay + izin + snap + notifikasi FGS | 2–4 hari |
| v1.2 | Long-press close dengan ring & haptik + cleanup cache | 0.5 hari |
| v2 | Multi-bubble / drag sambil tetap aktif | jangan dulu |

## QA matrix (device nyata wajib)

- Pixel (AOSP, Android 14/15): izin + startActivity dari bubble.
- Samsung One UI: bubble + FGS notification + rotation.
- Xiaomi HyperOS/MIUI: popup permission, service killed behavior.
- Oppo ColorOS: autostart + overlay behavior.
- Android 10 (API 29): tanpa ketat Android 14, tapi `specialUse` ignored.

## Keputusan yang perlu diambil

1. Bubble masuk **v1** (langsung, effort naik) atau **v1.1** (rekomendasi: v1.1, core dulu)?
2. Close: hold **1.6 s** dengan ring (rekomendasi) atau cukup 600 ms + getar?
3. Setelah close: hapus sesi (rekomendasi, privasi) atau simpan di Riwayat?
