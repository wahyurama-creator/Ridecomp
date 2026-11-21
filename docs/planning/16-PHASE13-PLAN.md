# 16 — Rencana Fase 13: Tampilan Responsif untuk Layar Kecil

**Status:** T13.1–T13.8 selesai 2026-11-21; penutup fase: rilis beta. Disusun 2026-11-19 atas permintaan kolaborator (pembagian tugas dengan pemilik); **disetujui pemilik 2026-11-20**. Dikerjakan **setelah Fase 11 dan 12** (D-124).
**Acuan:** `13-UI-SCREENS.md` (frame acuan 390 dp; wajib benar di 360 dp dan ≥ 412 dp), `02-DESIGN.md`, `08-CODING-RULES.md` §6 (UI-01…UI-09), `10-FEATURE-CHECKLIST.md`, `14-PHASE11-PLAN.md` §8 (anggaran performa).

---

## 1. Permintaan

1. Penyesuaian responsif halaman-halaman untuk **layar yang lebih kecil**.
2. **Tampilan di perangkat yang lebih besar tidak boleh berubah** setelah komponen disesuaikan.

## 2. Kondisi saat ini

- Semua ukuran berasal dari satu objek statis **`RideCompDimens`** (`:ui-common/theme`): spasi, tinggi tombol (hero 88 dp, primer 72 dp), target sentuh (56/72/96 dp), ukuran teks (`RideCompTypography` membaca token yang sama). Satu tempat ini memudahkan versi "ringkas".
- Belum ada mekanisme ukuran layar bersama. Beberapa layar membaca lebar sendiri-sendiri (`LocalConfiguration`/`BoxWithConstraints`): `RideScreen`, `IncidentComponents`, `HistoryScreen`, `OnboardingWelcome`, `ShareLayouts`, `ShareCardTemplates`, `RideChart`, `ProfileLabels`.
- 25 layar `*Screen.kt` di 6 modul `:feature-*`.
- HP uji kolaborator (Sony Xperia XZ) = **360 × 640 dp**: sudah mewakili batas bawah yang didukung sekarang.
- Cek awal 2026-11-19 (Sony, `wm density 540` → **320 dp**): Sambutan onboarding masih bisa di-scroll dan tombol **Mulai** terjangkau, tetapi judul besar memakan ±⅓ layar dan tombol baru terlihat setelah scroll jauh. Layar lain belum diperiksa → audit T13.1.

## 3. Keputusan

| # | Keputusan | Catatan |
|---|---|---|
| R1 | **Target terkecil 320 dp** lebar. Penyesuaian **hanya aktif di bawah 360 dp**; ≥ 360 dp memakai nilai sekarang, persis sama | D-124 |
| R2 | **Bukti "HP besar tidak berubah" = screenshot adb** sebelum/sesudah di 412, 390, dan 360 dp, dibandingkan piksel per piksel dengan skrip; **tanpa dependency baru** (bukan Roborazzi/Paparazzi) | D-124 |
| R3 | Fase 13 **direncanakan sekarang, dikerjakan setelah Fase 11 & 12** | D-124 |
| R4 | Tinggi layar pendek (< 640 dp) dan skala font sistem > 1,0 **di luar cakupan** kecuali ditemukan rusak parah di audit T13.1 (diajukan sebagai pilihan saat itu) | (agent), dapat diubah |

## 4. Prinsip teknis (agar HP besar tidak berubah)

1. **Satu sumber kelas ukuran:** `WindowWidthClass { COMPACT, REGULAR }` dihitung sekali di root (`RideCompTheme`) dari lebar jendela dp: `COMPACT` bila < `COMPACT_MAX_WIDTH_DP` (360), selain itu `REGULAR`. Diberikan lewat `CompositionLocal`. Layar tidak membaca `LocalConfiguration` sendiri lagi.
2. **Token ganda, bukan logika tersebar:** `RideCompDimens` menjadi data class dengan dua instance: `Regular` = **nilai yang ada sekarang tanpa perubahan**, `Compact` = nilai lebih kecil. Diakses lewat `RideCompTheme.dimens`. Konstanta tetap di satu tempat (CLAUDE.md prinsip 7).
3. **Default `Regular`:** Preview, widget, kartu share (ukuran gambar tetap), dan semua kode yang belum dimigrasikan tetap memakai `Regular`.
4. **Batas aman yang tidak boleh turun di `Compact`:** target sentuh ≥ 56 dp (UI-07; tombol utama ride tetap ≥ 72 dp), angka layar ride tetap terbaca dari jarak setang (02-DESIGN), teks minimal 12 sp, kontras tidak berubah.
5. **Tata letak, bukan hanya mengecilkan:** di `COMPACT` boleh mengubah susunan (mis. 2 kolom → 1 kolom, teks dipotong dengan elipsis + `contentDescription` penuh, tombol berdampingan → bertumpuk). Susunan `REGULAR` tidak disentuh.
6. **Tanpa biaya runtime berarti:** kelas ukuran hanya berubah saat konfigurasi berubah; tidak ada pengukuran per frame; layar ride tetap ≤ 1 Hz (anggaran §8 Fase 11).

## 5. Daftar centang Fase 13

Centang saat task selesai (juga di `06-ROADMAP.md` dan `docs/STATUS.md`).

**13A — Fondasi & audit**
- [x] T13.1 Audit layar kecil: screenshot semua layar di 320 dp & 360 dp, daftar masalah per layar
- [x] T13.2 Skrip screenshot pembanding (`scripts/screens/`) + baseline 412/390/360 dp
- [x] T13.3 Fondasi: `WindowWidthClass`, token `Regular`/`Compact`, `@SmallScreenPreviews`

**13B — Layar prioritas**
- [x] T13.4 Layar ride & persiapan ride (`RideScreen`, `PreRideScreen`, `SummaryScreen`, insiden)
- [x] T13.5 Beranda, onboarding & sambutan (`HomeScreen`, `OnboardingScreen`)

**13C — Layar lain**
- [x] T13.6 Riwayat & detail ride (`HistoryScreen`, `ActivityDetailScreen`, grafik, zona)
- [x] T13.7 Rute, sensor, latihan (`Routes*`, `SensorsScreen`, layar `feature-training`)
- [x] T13.8 Setelan & sub-layarnya (`SettingsScreen`, Profil, Keselamatan, Peta offline, Backup, Strava, dll.)

**Penutup fase**
- [x] Screenshot pembanding 412/390/360 dp identik dengan baseline untuk semua layar (per task, Feature Report T13.3–T13.8)
- [x] Uji perangkat 320 dp lewat `wm density`/`wm size` di realme RMX3830 (HP kecil asli tidak ada)
- [ ] Rilis beta berikutnya

## 6. Spesifikasi per task

#### T13.1 Audit layar kecil
- [x] **Status:** selesai 2026-11-21 (0 P1, 4 P2, 11 P3; R4 tidak dibuka, D-140) · Hasil: [`small-screen-audit.md`](../testing/small-screen-audit.md) · Feature Report: [`T13.1-small-screen-audit.md`](../reports/features/T13.1-small-screen-audit.md)
- HP uji 360 dp: `adb shell wm density 540` (Sony XZ, 1080 px → 320 dp); kembalikan dengan `wm density reset`. HP lain: `wm density <px_lebar × 160 / 320>`.
- Setiap layar (25 `*Screen.kt` + dialog/kartu penting): screenshot 320 dp & 360 dp, tema gelap (dan terang bila T11.9 sudah ada), bahasa `id` & `en` (teks Inggris/Indonesia beda panjang).
- Catat per layar: teks terpotong/tumpang tindih, elemen keluar layar, tombol tak terjangkau, target sentuh < 56 dp, scroll yang hilang, angka ride yang terlalu besar.
- Hasil: tabel di `docs/testing/small-screen-audit.md` + prioritas (P1 rusak/tak bisa dipakai, P2 jelek tapi bisa dipakai, P3 kosmetik). Menentukan isi T13.4–T13.8 dan apakah R4 perlu dibuka.

#### T13.2 Skrip screenshot pembanding
- [x] **Status:** selesai 2026-11-21 (`scripts/screens/`, rute layar debug, app debug terpisah; D-142, D-144). Baseline `base-id-dark` (412/390/360/320 dp, 25 layar) dari `49f9141` di realme RMX3830 · Cara pakai: [`screen-compare.md`](../testing/screen-compare.md) · Feature Report: [`T13.2-screen-compare.md`](../reports/features/T13.2-screen-compare.md)
- `scripts/screens/capture.sh <label>`: untuk setiap lebar (412, 390, 360 dp lewat `wm density`) buka setiap layar lewat langkah adb terdokumentasi (ketuk per koordinat, dihitung ulang per lebar); bila terlalu rapuh, ajukan rute debug khusus (source set `debug`, SEC-BLD-04), simpan PNG ke folder lokal (tidak di-commit: `.gitignore`).
- `scripts/screens/compare.sh <baseline> <hasil>`: bandingkan per piksel tanpa alat baru (mis. `cmp` atas PNG hasil `screencap` yang deterministik; data dinamis seperti jam/baterai disembunyikan dengan demo mode status bar `adb shell settings put global sysui_demo_allowed 1`).
- Data uji tetap: ride contoh dari menu Developer (debug) agar layar identik antar pengambilan.
- Baseline diambil dari `main` **sebelum** T13.3. Setiap task 13B/13C wajib lulus compare untuk 412/390/360 dp.
- Tanpa dependency Gradle baru (R2). Bila otomatisasi penuh di CI diinginkan nanti → ajukan Roborazzi/Paparazzi terpisah (DEP-02).

#### T13.3 Fondasi
- [x] **Status:** selesai 2026-11-21 (D-143). Compare di realme RMX3830: **25/25 layar identik di 412, 390, 360 dp**; 10 layar berubah di 320 dp sesuai rencana · Feature Report: [`T13.3-size-foundation.md`](../reports/features/T13.3-size-foundation.md)
- `WindowWidthClass` + `COMPACT_MAX_WIDTH_DP = 360` di `:ui-common` (konstanta di satu tempat); `LocalWindowWidthClass` diisi di `RideCompTheme`.
- `RideCompDimens` → `data class` + `Regular` (nilai sekarang, **disalin tanpa perubahan**) + `Compact`; `RideCompTheme.dimens`. Migrasi pemakaian `RideCompDimens.x` → `RideCompTheme.dimens.x` bertahap; selama migrasi keduanya menunjuk nilai `Regular` yang sama.
- Layar yang sekarang membaca `LocalConfiguration`/`BoxWithConstraints` untuk lebar dialihkan ke `LocalWindowWidthClass` (perilaku `REGULAR` tetap).
- `@SmallScreenPreviews`: preview 320 dp (gelap/terang) di samping `@ThemePreviews`.
- Unit test: kelas ukuran di 319/320/359/360/412 dp; `Regular` sama persis dengan nilai lama (test membandingkan setiap token).
- Bukti: compare T13.2 identik di 412/390/360 dp.

#### T13.4 Layar ride & persiapan ride (prioritas tertinggi)
- [x] **Status:** selesai 2026-11-21 (D-145): SELESAI di layar jeda terlihat penuh di 320 × 569 dp; cincin GPS & hitung mundur lebih kecil di `COMPACT` · Feature Report: [`T13.4-ride-screens.md`](../reports/features/T13.4-ride-screens.md)
- Angka utama (`dataValueMaxSize`) diskalakan agar muat 320 dp tanpa terpotong (mis. "188,8" km), label tetap terbaca; grid data tetap 2 kolom bila muat, jika tidak tinggi baris dikurangi, bukan teks.
- Bar PAUSE/LAP tetap ≥ 72 dp tinggi; lebar LAP dikecilkan di `COMPACT` bila perlu.
- Banner peringatan (GPS hilang, dll.) membungkus 2 baris tanpa menutupi angka utama.
- Layar insiden: tombol batal tetap ≥ 96 dp (`touchTargetIncident`), cincin hitung mundur mengecil proporsional.
- Tidak ada perhitungan tambahan saat merekam (anggaran performa).

#### T13.5 Beranda, onboarding & sambutan
- [x] **Status:** selesai 2026-11-21 (D-146): Sambutan di `COMPACT` judul 30 sp + kartu contoh disembunyikan → tombol Mulai terlihat tanpa scroll di 320 × 569 dp; Beranda tidak perlu diubah (audit) · Feature Report: [`T13.5-T13.8-audit-fixes.md`](../reports/features/T13.5-T13.8-audit-fixes.md)
- Sambutan: judul besar lebih kecil di `COMPACT` sehingga tombol **Mulai** terlihat tanpa scroll jauh (temuan cek awal §2).
- Beranda: kartu status (GPS/HR/Cadence/Power) 4 kolom → tetap muat atau 2×2 di `COMPACT`; tombol MULAI RIDE tetap ≥ 88 dp.

#### T13.6 Riwayat & detail ride
- [x] **Status:** selesai 2026-11-21 (D-146): jarak antar kolom kartu bulan di `COMPACT`; Detail ride tanpa masalah di audit; galeri share/editor segmen belum diaudit (Feature Report §9) · Feature Report: [`T13.5-T13.8-audit-fixes.md`](../reports/features/T13.5-T13.8-audit-fixes.md)
- Kotak statistik 3 kolom → 2 kolom di `COMPACT`; label sumbu `RideChart` tidak bertabrakan (gutter minimum D-115 tetap untuk `REGULAR`); kolom angka zona (`zonePercentWidth`, `zoneDurationWidth`) muat.
- Galeri share 2 kolom tetap; ukuran gambar kartu share **tidak berubah** (gambar ekspor harus sama di semua HP).

#### T13.7 Rute, sensor, latihan
- [x] **Status:** selesai 2026-11-21 (D-146): Rute kosong diperbaiki di T13.3; Sensor/Latihan bisa di-scroll (P3, tidak diubah); Detail rute belum diaudit (Feature Report §9) · Feature Report: [`T13.5-T13.8-audit-fixes.md`](../reports/features/T13.5-T13.8-audit-fixes.md)
- Baris daftar (nama panjang rute/sensor) dipotong dengan elipsis; tombol aksi tetap ≥ 56 dp; editor halaman data dan rencana latihan muat 320 dp.

#### T13.8 Setelan & sub-layar
- [x] **Status:** selesai 2026-11-21 (D-146): kolom kontak Keselamatan bertumpuk di `COMPACT`; baris Setelan membaik sejak T13.3 · Feature Report: [`T13.5-T13.8-audit-fixes.md`](../reports/features/T13.5-T13.8-audit-fixes.md)
- Baris setelan dengan nilai di kanan → nilai pindah ke bawah label di `COMPACT` bila tidak muat; formulir Profil/Keselamatan muat tanpa scroll horizontal; dialog tidak lebih lebar dari layar.

---

## 7. Cara uji (setiap task 13B/13C)

1. Baseline dari `main` (T13.2) sudah ada.
2. Pasang build task, jalankan `capture.sh` di 412/390/360 dp → `compare.sh` harus **identik**. Beda 1 piksel pun = gagal; dicatat dan diperbaiki, kecuali disetujui sebagai perubahan disengaja (tidak diharapkan di fase ini).
3. Screenshot 320 dp sebelum/sesudah dimasukkan ke Feature Report.
4. Bahasa `id` & `en`; tema gelap (dan terang bila tersedia).
5. `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`.
6. Kembalikan HP: `adb shell wm density reset`, `adb shell wm size reset`, matikan demo mode.

## 8. Risiko

| Risiko | Mitigasi |
|---|---|
| Perubahan tak sengaja di HP besar | Token `Regular` = salinan nilai lama + unit test per token + compare screenshot 412/390/360 dp setiap task |
| Bentrok dengan Fase 11/12 (layar yang sama) | Dikerjakan setelah Fase 11 & 12 (R3); migrasi token bertahap per modul |
| `wm density` ≠ HP kecil asli (font/inset beda) | Uji akhir di HP kecil asli bila tersedia; hasil `wm density` hanya indikasi |
| Angka ride terlalu kecil di 320 dp | Batas minimum §4.4; uji baca dari jarak setang |
| Screenshot tidak deterministik (jam, baterai, animasi) | Demo mode status bar, data uji tetap, tunggu layar stabil sebelum `screencap` |
| Mengecilkan target sentuh | Dilarang turun di bawah UI-07; dicek di audit |

## 9. Anggaran performa

Sama dengan `14-PHASE11-PLAN.md` §8, ditambah: penentuan kelas ukuran hanya saat konfigurasi berubah (bukan per frame); ukuran APK naik < 50 KB (tanpa library baru); layar ride tetap ≤ 1 Hz tanpa kerja tambahan.

## 10. Definition of Done Fase 13

- T13.1–T13.8 selesai dengan Feature Report; audit T13.1 tidak menyisakan masalah P1/P2 di 320 dp.
- Screenshot 412/390/360 dp **identik** dengan baseline untuk semua layar yang diaudit.
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug` lulus; uji perangkat 320 dp tercatat.
- `13-UI-SCREENS.md` diperbarui: "Layout harus benar di 320 dp; 360 dp ke atas memakai ukuran normal."
