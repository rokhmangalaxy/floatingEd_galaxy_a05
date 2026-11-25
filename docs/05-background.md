# 05 — Background: dari "warna" ke "brand kit pribadi"

Background terlihat paling sederhana, tapi justru di sini app bisa terasa *beda* dari 100 editor lain. Ini bagian "saran lain" yang paling berpotensi menjadi ciri khas.

## Filosofi

Gambar yang di-share dari chat biasanya bukan komposisi final — screenshot, meme, foto temu. Background adalah alat untuk **menyelamatkan gambar** (letterbox, bingkai, dsb.) tanpa memotong isi. Jadi background bukan dekorasi; ini fitur penyelamat.

## Tangga fitur (dari wajib ke pembeda)

### Tier 1 — wajib (MVP)
- **Solid color** + palet: hitam, putih, abu, + palet 12 warna harmonis.
- **Auto color from image** (lihat di bawah).
- **Gradient** dua warna (linear 45°/90°/135°).

### Tier 2 — cepat & berkesan
- **Blurred image** (gambar yang sama, di-blur & di-zoom, di belakang) — *paling populer di dunia story*. Implementasi: downscale 1/8, `RenderScript`/`RenderEffect` (API 31) atau stackview blur library; jangan blur full-res.
- **Pattern**: dot grid, garis diagonal, noise, kertas. SVG/vector, tileable.
- **Padding / border**: bingkai luar putih/hitam (instant "frame"), lebar bisa digeser.
- **Rounded corners** pada gambar inti (slider radius + toggle "shadow").

### Tier 3 — pembeda / "saran lain" saya
1. **Ambient color background** — ekstrak 3 warna dominan dari gambar (median cut / Material palette), jadikan background gradient halus. Satu tap, hasil selalu terlihat *designed*. Ini fitur murah, efek wow tinggi.
2. **Wallpaper mode** — bikin wallpaper HP (rasio 20:9/19.5:9) dengan blur + safe area notifikasi (dedicated untuk segmen screenshot/wa). Termasuk template "Lock screen" (jam di atas, gambar di bawah).
3. **Blur "kontemporer"** — `Modifier.blur` Compose di layer gambar sendiri, bukan background: bikin versi gambar yang tidak tajam di tepi (duotone + blur).
4. **Duotone** overlay (dua warna dari gambar asli) — bisa dipakai sebagai background *dan* filter gambar.
5. **Sticker/emoji watermark** ringan (1-3 sticker) — kalau mau main ke maker style.
6. **Brand kit pribadi** — simpan palet, logo (PNG transparan), font, signature. Bisa dipakai otomatis di semua share. Ini fitur kelas Pro yang bikin orang betah.
7. **Template "chat bubble"** — background transparan dengan tepi bubble ala WA/IG DM. Cocok buat konten "share meme dalam bentuk chat". Cek legal harfiahnya: jangan pakai aset WhatsApp, buat bentuk generik.
8. **Auto letterbox "art direction"** — kalau gambar 4:3 di bingkai 9:16, taruh gambar di tengah dengan gradient atas/bawah, dan sediakan "slot" teks kosong kecil opsional.

## Auto color — resep implementasi

1. Ambil thumbnail 64×64.
2. Median-cut ke 5 warna atau `androidx.palette` (kualitas ok, gratis).
3. Filter warna terlalu pucat/gelap (`hsl.saturation`, `luminance`).
4. Buat gradient: warna dominan terang → dominan gelap, atau dua warna paling bertolak.
5. Simpan `Background.Gradient` ke state (jadi tetap bisa diedit).

Catatan: jangan ambil warna dari area yang kemungkinan kulit/wajah (bisa menghasilkan kuning aneh); prefer area tepi gambar.

## UX detail yang bikin beda

- **Chip background berurutan**: `[Auto] [Warna] [Gradient] [Blur] [Pattern] [Border]` — horizontal scroll, selebar layar, bukan tersembunyi di tab.
- **Live preview saat memilih warna**: tap chip → langsung terlihat tanpa "Apply". (Pasti gampang karena render dari state.)
- **Ripple/animasi transisi halus** ketika ganti background — 150 ms crossfade.
- **Tema otomatis**: kalau background gelap, chip bar jadi versi dark; ini detail yang sering dilupakan.
- **Save last background** sebagai default untuk sesi berikutnya (DataStore).

## Teknis: hati-hati

- **Blur**: di API < 31 `RenderEffect` tidak ada. Pilihan: (a) `StackBlur`/`RenderScript` (deprecated API 31), (b) pre-blur bitmap sekali saat background dipilih (paling aman & cepat): downscale 1/8 → blur 20 → upscale. Preview dan export pakai bitmap hasil pre-blur yang sama → konsisten. **Rekomendasi: pre-blur bitmap.**
- **Gradient angle**: simpan derajat, bukan enum (supaya future slider).
- **Transparan (PNG)**: kalau user pilih background transparan, format export **otomatis PNG** (bukan JPEG). Beri badge kecil "PNG".
- **Warna background harus di-render di export** pakai renderer yang sama (lihat doc 04).

## Pertanyaan

1. Background transparan perlu? (kalau iya, export PNG; lebih besar file)
2. Fitur prioritas Tier 3 mana yang paling menarik: ambient color / wallpaper mode / brand kit / duotone?
3. Perlu preset background dari template (paket gratis + paket Pro)?
