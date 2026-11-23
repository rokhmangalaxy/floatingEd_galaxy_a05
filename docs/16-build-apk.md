# 16 — Build APK via GitHub Actions & Update Tanpa Uninstall

## Kenapa bisa install menimpa tanpa uninstall

Android hanya mengizinkan `UPDATE` sebuah aplikasi bila **tiga hal sama**:

1. `applicationId` sama (`com.emrobinso.floatpic`) — build `debug` memakai `.debug`, jadi debug dan release **tidak** bisa saling menimpa. Selalu pakai APK release dari Actions.
2. **Kunci tanda tangan (certificate) sama.** Sertifikat berbeda → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
3. `versionCode` **lebih tinggi atau sama** dari terpasang.

Repositori ini memenuhi ketiganya:

- `app/keystore/floatpic.p12` — kunci rilis, **di-commit** supaya tidak berubah antar build/runner. Password dev: `floatpic2026`, alias `floatpic`.
- `signingConfig` release selalu dipakai (bukan `debug` signing).
- Workflow menghitung `versionCode = 10000 + jumlah commit`, jadi naik terus.

> ⚠️ Kunci ini adalah kunci **development** (tidak untuk Play Store). Untuk Play Store, buat kunci baru, simpan sebagai secret (`FLOATPIC_KEY_BASE64`), dan jangan pernah ganti setelah rilis pertama — Play Store menolak update dengan kunci berbeda. Lihat bagian "Produksi".

## Cara build

Workflow: [`.github/workflows/build.yml`](../.github/workflows/build.yml)

- Trigger: setiap push ke branch mana pun, PR, tag `v*`, dan manual **Run workflow**.
- Runner: Ubuntu + JDK 17 + Android SDK (bawaan runner), Gradle wrapper 8.10.2.
- Output: `dist/FloatPic-latest.apk` + `dist/FloatPic-<version>.apk` + `SHA256SUMS.txt`.

### Mengambil link APK

| Cara | Link |
|---|---|
| **Rolling release (link tetap, rekomendasi)** | https://github.com/rokhmangalaxy/floatingEd_galaxy_a05/releases/download/apk-latest/FloatPic-latest.apk |
| Artifact per-build | tab **Actions** → run terakhir → **Artifacts** → `FloatPic-<version>` |
| Halaman rilis (menampilkan versionName/versionCode terbaru) | https://github.com/rokhmangalaxy/floatingEd_galaxy_a05/releases/tag/apk-latest |

## Instalasi pertama & seterusnya

1. Unduh `FloatPic-latest.apk` di HP → buka → izinkan "Install unknown apps" untuk browser.
2. Setelah itu, cukup unduh versi baru dan install — **tanpa uninstall**, semua izin & sesi tetap.

## Produksi (Play Store / distribusi serius)

1. Buat keystore baru:
   ```
   keytool -genkeypair -v -keystore release.p12 -storetype PKCS12 \
     -alias floatpic -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Simpan ke GitHub Secrets (butuh token dengan scope `secrets`):
   - `FLOATPIC_KEY_BASE64` = `base64 -w0 release.p12`
   - `FLOATPIC_STORE_PASSWORD`, `FLOATPIC_KEY_PASSWORD`, `FLOATPIC_KEY_ALIAS`
3. Hapus `app/keystore/floatpic.p12` dari repo. Workflow otomatis memakai secret bila ada.
4. Simpan cadangan kunci — bila hilang, aplikasi tidak bisa di-update sama sekali.

## Verifikasi sidik jari kunci (penting!)

Agar APK baru **pasti** bisa menimpa yang lama, sertifikat harus identik:

```
SHA-256: BD:E2:3A:AC:99:C0:1B:DD:73:E4:4D:35:3D:E8:E0:60:7F:FE:46:8A:15:A5:B4:7F:B7:78:48:50:50:94:01:0D
```

- Sertifikat publik: [`docs/signing-cert.der`](signing-cert.der) / [`docs/signing-cert.pem.b64`](signing-cert.pem.b64).
- Cek APK yang kamu unduh: `apksigner verify --print-certs FloatPic-latest.apk` → harus sama.
- CI memasang pemeriksaan ini otomatis setelah build (langkah "Verify signature") dan gagal bila berbeda.

## Catatan artifact

- APK release di-minify (R8) + shrink resources.
- `debug` build tidak dipublikasikan (suffix `.debug` → tidak bisa menimpa release).
