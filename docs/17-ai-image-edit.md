# 17 — Analisis Fitur Edit AI (Qwen-Image-Edit-2511 di HF Space)

Dokumen ini hasil riset untuk fitur "edit dengan AI" memakai
[prithivMLmods/Qwen-Image-Edit-2511-LoRAs-Fast](https://huggingface.co/spaces/prithivMLmods/Qwen-Image-Edit-2511-LoRAs-Fast).
Tujuannya **bukan** memutuskan "kita pakai ini", tapi memetakan apa yang mungkin, apa yang
berbahaya, dan mana yang cocok dengan karakter FloatPic (share → edit cepat → balik ke chat).

## 1. Fakta teknis yang sudah pasti

Dari `agents.md` milik Space tersebut:

| Hal | Detail |
|---|---|
| Protokol | Gradio REST: `GET /gradio_api/info` (schema), `POST /gradio_api/call/v2/{endpoint}`, poll `GET /gradio_api/call/{endpoint}/{event_id}` |
| Upload file | `POST /gradio_api/upload` (multipart), lalu kirim `{"path": "...", "meta": {"_type": "gradio.FileData"}, "orig_name": "..."}` |
| Auth | `Authorization: Bearer $HF_TOKEN` |
| Model | Qwen-Image-Edit-2511 + LoRA lazy-load (20+ adapter) |
| Kecepatan | 4 step default, Flash Attention 3, bf16 |
| Batas ukuran | input di-resize ke sisi maks 1024 px, dimensi dibulatkan ke kelipatan 8 |

Implikasi Android: **tidak ada SDK**, jadi klien HTTP ditulis manual (OkHttp atau
`HttpURLConnection` + `kotlinx.serialization`) dengan dua fase submit → poll. Tidak ada progres
streaming selain status antrean, jadi indikator hanya bisa indeterminate atau estimasi waktu.

## 2. Kategori fitur yang bisa dibangun

### A. Edit berbasis prompt
Kekuatan model ini: "ganti langit jadi senja", "hapus orang di belakang", "jadikan anime",
"buat latar jadi studio putih". Untuk FloatPic paling masuk sebagai tool `AI`: satu kolom
prompt, beberapa chip siap pakai, tombol Terapkan.

### B. LoRA sebagai preset sekali klik
Adapter yang tersedia antara lain: `Multiple-Angles`, `Photo-to-Anime`, `Anime-V2`,
`Light-Migration`, `Upscaler`, `Style-Transfer`, `Manga-Tone`, `Anything2Real`,
`Polaroid-Photo`, `Unblur-Anything`, `Midnight-Noir`, `Ultra-Realistic-Portrait`,
`Pixar-Inspired-3D`, `Noir-Comic`, `Any-Light`, `Studio-DeLight`, `Cinematic-FlatLog`.

Yang relevan untuk tugas editor kita:

| LoRA | Nilai untuk FloatPic | Risiko |
|---|---|---|
| **Upscaler** | gambar dari chat buram → tajam sebelum dibagikan. Pembeda nyata. | rendah |
| **Unblur-Anything** | menyelamatkan screenshot/foto blur | rendah |
| **Photo-to-Anime / Anime-V2** | konten sosial, cepat | sedang (selera) |
| **Polaroid-Photo** | melengkapi frame manual yang sudah ada | sedang |
| **Light-Migration / Studio-DeLight / Any-Light** | perbaiki cahaya sebelum background kita | rendah–menengah |
| **Multiple-Angles** | wow, tapi mengubah isi foto | tinggi |
| **Style-Transfer** | butuh gambar referensi kedua | sedang |

**Jangan** dijadikan fitur produk: Anything2Real, Noir-Comic, Manga-Tone, Pixar-3D — semuanya
mengganti foto jadi karya lain, cocok untuk main-main, bukan "kirim ke grup keluarga".

### C. Kombinasi yang murah tapi berkesan
1. **"Rapikan dulu, baru dibingkai"** — satu tombol: unblur/upscale → semua tool lama
   (rasio, background, teks) bekerja seperti biasa. Alur yang tidak dimiliki pesaing ringan.
2. **AI background** — prompt "latar jadi meja kayu", lalu kita otomatis menambah padding,
   blur, dan shadow dengan renderer lama, sehingga hasilnya terlihat seperti desain kita.
3. **Prompt setelah edit kita** — "buat versi malam" diterapkan pada gambar yang sudah
   di-crop/di-rotate, supaya hasil AI tetap mengikuti frame pilihan user.

## 3. Batasan yang harus diakui sebelum coding

1. **Space publik bisa tidur.** Space gratis sleep setelah idle; request pertama bisa 30–60 s
   atau 503. Tanpa token, kena rate limit bersama.
2. **Privasi.** Gambar dikirim ke server pihak ketiga — bertentangan dengan janji "diproses di HP".
   Wajib opt-in eksplisit + tombol batal.
3. **Biaya token.** `agents.md` menyebut `HF_TOKEN`; kuota gratis terbatas. Perlu jalur
   BYO-token atau backend sendiri.
4. **Ukuran.** Output ~1024 px sisi panjang; kurang untuk arsip. Perlu upscale lokal.
5. **Waktu.** Realistis 10–40 s per gambar (antre + upload + inference + download). Di kartu
   floating itu terasa lama → butuh progres yang jujur.
6. **Determinisme.** Prompt/seed beda → hasil beda; user butuh "coba lagi" yang murah.

## 4. Arsitektur yang disarankan (kalau dilanjutkan)

```
feature/ai/
  AiClient.kt          # Gradio: upload -> call -> poll, retry + timeout
  AiRepository.kt      # antrean job, cache per (hash gambar, prompt, lora)
  AiModels.kt          # request/response @Serializable
  AiPanel.kt           # prompt, chip LoRA, "Terapkan", state error
core/model/AiEdit.kt   # data class AiEdit(path, prompt, lora, at) — bagian DraftSnapshot
```

Aturan integrasi:

- **Jangan pernah** menimpa `vm.source`. Hasil AI disimpan sebagai layer di atas
  (`EditorState.aiLayer`), dirender oleh `EditorRenderer` yang sama. Undo, riwayat 10, dan
  preview==export tetap jalan tanpa kode baru.
- Jaringan di `Dispatchers.IO`; hasil di `cacheDir/ai/<sha1>.jpg`.
- Cache key = `sha1(gambar terkirim) + lora + prompt`; tekan Terapkan dua kali tidak memanggil
  server dua kali.
- Gagal jaringan → pesan yang bisa ditindaklanjuti, state sebelumnya tidak disentuh.
- Status UI: `idle → uploading → queued → generating → downloading → done/error` + elapsed time.

## 5. Roadmap bertahap (rekomendasi)

**v0.3a — validasi:** tool `AI` dengan satu aksi **Unblur/Upscale** (paling aman & berguna).
Hasil = layer baru. Tanpa prompt bebas.

**v0.3b — prompt bebas:** kolom prompt + 6 chip contoh, token diisi user (BYO) di
EncryptedSharedPreferences. Tanpa token → fitur nonaktif dengan penjelasan.

**v0.4 — LoRA preset:** chip gaya (Photo-to-Anime, Polaroid-Photo, Studio-DeLight) dengan
estimasi waktu dari job terakhir.

**v0.5 — AI + template:** "Rapikan → frame → teks" satu tombol memakai renderer lama.

## 6. Alternatif yang perlu dipertimbangkan serius

| Pendekatan | Kelebihan | Kekurangan |
|---|---|---|
| Space publik + BYO token | gratis, tanpa server | tidur, rate limit, privasi, UX tidak stabil |
| Backend sendiri (GPU sewa) | kontrol penuh, antre & cache | biaya bulanan, maintenance |
| Replicate / fal.ai | SLA, model Qwen 2511 + LoRA tersedia | bayar per gambar |
| On-device | privat, offline | APK membengkak, kualitas jauh di bawah, HP panas |
| Tanpa AI, perkuat tool lokal | konsisten, cepat, tanpa risiko | kehilangan pembeda "wow" |

Pendapat: **mulai dari BYO token + satu aksi (unblur/upscale)**, ukur pemakaian, baru naik ke
prompt bebas. Mengirim prompt-bebas ke banyak pengguna di atas Space gratis akan berujung
keluhan "gagal terus" — itu lebih merusak reputasi daripada tidak punya fitur AI.

## 7. Pertanyaan terbuka untuk user

1. AI boleh **mengirim gambar ke server** (opt-in) atau harus 100% lokal?
2. Token HF: user menempel sendiri, atau disediakan?
3. Prioritas pertama: **rapikan (unblur/upscale)**, **ganti latar**, atau **gaya foto**?
4. Hasil AI sebagai **layer baru yang bisa dimatikan** (rekomendasi) atau mengganti gambar?
