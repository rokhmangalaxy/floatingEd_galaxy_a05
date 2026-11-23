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
| Rolling release (stabil, selalu menimpa) | `https://github.com/rokhmangalaxy/floatingEd_galaxy_a05/releases/download/apk-latest/FloatPic-latest.apk` |
| Artifact per-build | tab **Actions** → run terakhir → **Artifacts** → `FloatPic-<version>` |
| Release page | `https://github.com/rokhmangalaxy/floatingEd_galaxy_a05/releases`

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

## Catatan artifact

- APK release di-minify (R8) + shrink resources.
- `debug` build tidak dipublikasikan (suffix `.debug` → tidak bisa menimpa release).
