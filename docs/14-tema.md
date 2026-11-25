# 14 — Tema: Neumorphism vs Pastel Maroon 500%

Dua tema, satu tata letak. Tema **tidak boleh** mengubah kanvas, posisi tool, atau warna export — hanya chrome UI. Ini aturan pertama.

## Model tema

```kotlin
enum class AppTheme { NEU_SOFT, PASTEL_MAROON }
```

Tema disimpan di DataStore (default: ikuti preferensi pertama kali = NEU_SOFT untuk kesan "baru", PASTEL_MAROON untuk kesan "berani"). Toggle tersedia di top bar editor (ikon palet) **dan** Settings.

> Catatan: **kanvas editor selalu gelap** (netral #111). Neumorphism membuat warna kanvas "terlihat berbeda" dari yang dipersepsikan; kanvas netral mencegahnya. Tema hanya mengubah bar, chip, sheet, dan tombol.

## A. Neumorphism (soft UI)

### Karakter
- Latar monokrom lembut (`#E9ECF2` terang atau `#22262E` gelap) dengan tombol "timbul" dari latar.
- Dua bayangan: terang dari kiri-atas, gelap dari kanan-bawah.
- Radius besar (24–32 dp), tanpa border, tanpa stroke.
- Aksen tunggal halus (indigo dusk `#5B6CFF` atau maroon redup `#8A4B5A`).

### Token warna (Compose)

```kotlin
data class NeuTokens(
    val base: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val accent: Color,
    val onBase: Color,
)
```

Light: base `#E9ECF2`, lightShadow `#FFFFFF` 90%, darkShadow `#C3C8D4`, accent `#5B6CFF`, onBase `#2A2F3A`.
Dark: base `#22262E`, lightShadow `#2E333D`, darkShadow `#171A20`, accent `#8FA0FF`, onBase `#E6E9F0`.

### Modifier neumorphism (inti)

```kotlin
fun Modifier.neuRaised(tokens: NeuTokens, radius: Dp = 24.dp) = this
    .shadow(elevation = 8.dp, shape = RoundedCornerShape(radius), ambientColor = tokens.darkShadow)
    .background(tokens.base, RoundedCornerShape(radius))
```
Versi tajam (dua bayangan arah berlawanan) butuh `drawBehind` dengan `BlurMaskFilter`:

```kotlin
fun Modifier.neuSoft(tokens: NeuTokens, radius: Dp = 24.dp) = drawBehind {
    val r = radius.toPx()
    // bayangan gelap kanan-bawah
    drawRoundRect(
        color = tokens.darkShadow.copy(alpha = .8f),
        topLeft = Offset(r * .18f, r * .18f),
        size = size, cornerRadius = CornerRadius(r),
        style = Fill,
        blendMode = BlendMode.SrcOver,
        // blur via graphicsLayer pada container (lihat catatan)
    )
}
```
> Compose tidak mendukung blur bayangan langsung di `drawBehind`; cara yang konsisten: bungkus konten dengan `Modifier.graphicsLayer { }` + dua `Box` di belakang dengan `Modifier.blur(6.dp)` (API 31+) atau `shadow` ganda. Untuk API 26–30, gunakan 2 layer `Box` dengan gradient radial lembut (cukup) — **jangan** bikin bitmap blur manual untuk setiap tombol.

### Risiko neumorphism (jujur)

1. **Aksesibilitas kontras jelek.** Tombol hampir menyatu dengan latar → sulit dilihat. Mitigasi: selalu ada label/ikon dengan kontras ≥ 4.5:1; state aktif gunakan aksen.
2. **Performa** jika blur dipakai di setiap tombol. Mitigasi: batasi blur pada 4 elemen besar (sheet, CTA, viewport card), tombol kecil pakai bayangan biasa.
3. **Huruf stroke** di chip teks harus tetap pakai warna murni — jangan diberi bayangan.

## B. Pastel Maroon 500% Saturasi

### Karakter
- Aksen maroon yang **sangat pekat** (satura si tinggi): `H ≈ 348°`, `S ≈ 100%`, `L ≈ 28–35%`.
- Pendamping: krem pastel `#F6E7E4`, plum lembut `#5C2B37`, gula `#FFF9F7`.
- Kontras tinggi pada CTA; chips memakai versi 12% alpha dari maroon.
- Tekstur halus: grain/noise opacity 3% pada latar (opsional).

### Token warna

```kotlin
data class MaroonTokens(
    val maroon: Color,        // #7A0C2E (S~100, L~26)
    val maroonBright: Color,  // #C1121F (untuk pressed/active)
    val cream: Color,         // #F6E7E4
    val plum: Color,          // #3E101B (dark bg)
    val sugar: Color,         // #FFF9F7
    val onMaroon: Color,      // #FFF9F7
)
```

Light: background `cream`, surface `sugar`, primary `maroon`, secondary `plum`.
Dark: background `plum`, surface `#2A0B12`, primary `maroonBright`, onPrimary `sugar`.

"500%" berarti saturasi dinaikkan hingga mentok (S=100) dan lightness dijaga rendah (L≈26–32) agar tidak jadi merah cerah norak. Ini kunci: **maroon pekat pada latar pastel** — kontras dramatis, tetap elegan.

### Yang perlu dijaga
- **Kanvas tetap netral**: jangan pakai cream/plum sebagai latar kanvas — warna gambar akan terlihat "kotor".
- Chip aktif maroon, chip tidak aktif transparan dengan border 1 dp maroon 30%.
- Ikon pada CTA putih gading, bukan putih murni (#FFF akan terlalu keras di maroon).
- Jangan gunakan merah maroon untuk indikator error (barengan). Error pakai oranye `#D97706`.

## Implementasi toggle (Compose)

```kotlin
@Composable
fun FloatPicTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = when (theme) {
        AppTheme.NEU_SOFT      -> neuColorScheme(isDark)
        AppTheme.PASTEL_MAROON -> maroonColorScheme(isDark)
    }
    CompositionLocalProvider(LocalNeu provides neuTokensFor(theme)) {
        MaterialTheme(colorScheme = colors, typography = FloatTypography, content = content)
    }
}
```

- Simpan pilihan di DataStore; terapkan sebelum konten pertama (hindari flash tema salah: baca dengan runBlocking singkat atau `SplashScreen`).
- Animasi transisi tema: `animateColorAsState` 250 ms untuk semua token warna.

## Perbandingan singkat

| Aspek | Neumorphism | Pastel Maroon |
|---|---|---|
| Nuansa | tenang, lembut, "app modern" | berani, editorial, berkarakter |
| Risiko | kontras rendah, blur mahal | terlalu "ramai" jika saturasi salah |
| Siapa cocok | mayoritas, screenshot marketing | anak muda kreatif, demo yang langsung menarik |
| Pemasaran | screenshot bersih | screenshot kontras (banyak like di Play Store) |

**Rekomendasi strategis:** default **Neumorphism** (aman untuk pasar luas), dan **Pastel Maroon** sebagai "signature theme" yang ditonjolkan di Play Store screenshot & onboarding — user bisa memilih. Jangan buat dua tema dengan layout berbeda; dua tema, satu layout.

## Checklist tema

- [ ] Semua warna diambil dari token (tidak ada `Color.Red` di kode).
- [ ] Kedua tema punya light & dark.
- [ ] Kontras teks pada CTA ≥ 4.5:1 di kedua tema.
- [ ] Blur hanya pada ≤4 komponen (performa).
- [ ] Kanvas editor netral di kedua tema.
- [ ] Toggle di top bar + settings, dengan preview mini (dua kotak mungil).

## Pertanyaan

1. Default tema: Neumorphism (rekomendasi) atau Pastel Maroon (lebih berani)?
2. Perlu kedua tema tersedia di v1, atau satu dulu lalu tambah? (rekomendasi: dua-duanya, token disiapkan sekarang)
3. Neumorphism: versi light atau dark sebagai default? (rekomendasi light — senada "soft")
