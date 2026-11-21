# 14 — Rencana Fase 11: Analisa Ride, Usaha Terbaik, Tema & Peta Offline

**Status:** berjalan (7/14: T11.1–T11.7 selesai). Disusun 2026-11-19 atas permintaan pemilik.
**Acuan:** `06-ROADMAP.md` (daftar task), `07-TEST-PLAN.md` §4/§5 (uji), `10-FEATURE-CHECKLIST.md` (alur tiap task), aturan wajib `08`–`11`.
**Referensi visual pemilik:** `~/Downloads/RideComp-Referensi/` (4 screenshot halaman detail aktivitas Strava, 2026-11-18): bagian *Hasil* (Usaha Terbaik · Segmen), *Wawasan Detak Jantung*, grafik *Kecepatan* + 4 angka, grafik *Elevasi* + 2 angka, *Perkiraan Zona Kekuatan* Z1–Z7. Logo, ikon, dan merek Strava **tidak** ditiru: hanya susunan informasinya.

---

## 1. Permintaan pemilik (asli)

1. Ganti tema **Dark & Light**.
2. **Usaha terbaik** dari setiap sesi. Contoh: hari 1 sprint di Jl. Soekarno Hatta selama 3 menit; hari 2 otomatis mendapat usaha terbaik, ditampilkan di **halaman detail setelah ride**.
3. **Analisa detak jantung** berbentuk grafik di halaman detail bila memakai sensor HR.
4. **Analisa kecepatan** berbentuk grafik dengan Kec. rata-rata, Kec. maks, Waktu bergerak, Waktu total.
5. **Rombak grafik elevasi**: grafik di atas, angka bantu (sumbu), lalu Kenaikan elevasi dan Elevasi maks.
6. **Perkiraan power** dengan grafik **Z1–Z7**.
7. **Peta tetap bisa dipakai tanpa internet** saat navigasi.

## 2. Kondisi saat ini (agar tidak dikerjakan ulang)

| Area | Sudah ada | Belum ada |
|---|---|---|
| Data ride | Sampel 1 Hz: kecepatan, jarak, ketinggian, HR, cadence, power asli (`powerW`), **estimasi power** (`estPowerW`) (05-DATA-MODEL) | — |
| Halaman detail (`feature-history/ActivityDetailScreen`) | Peta, kotak statistik, kartu share, **grafik bertab kecil** (Speed/Elevasi/HR/Cadence/Power) tanpa angka sumbu, tabel lap, ekspor | Bagian per analisa dengan angka sumbu & ringkasan |
| Rekor | `BestEffortKey`: tercepat 1/5/10/20/40/100 km; power 5 dtk, 1/5/20/60 mnt; terjauh, tanjakan terbanyak, avg speed tercepat (12 §8); layar "Rekor baru" | Ditampilkan di halaman detail; rekor durasi **kecepatan** |
| Segmen | Dibuat dari ride (`CreateSegmentFromActivityUseCase`), **dicocokkan otomatis** tiap ride disimpan (`SegmentsStep`), PR dihitung, live segmen saat ride | Ditampilkan di halaman detail; cara cepat menandai sprint |
| Zona | HR Z1–Z5 (% HR max), power Z1–Z7 (`MetricsConfig.ZONE_UPPER_PERCENT_FTP`) di layar ride; `ZoneTime.heartRate` untuk kartu share | Waktu di zona power per ride; tampilan di detail |
| Tema | `ThemeMode { DARK, LIGHT, SYSTEM }`, `LightRideCompColors` | Pilihan pengguna; aplikasi selalu gelap |
| Peta | Online OpenFreeMap (D-90); offline **impor `.pmtiles` manual** (D-64); tanpa internet → garis/breadcrumb | Cache otomatis; unduh peta rute/area di dalam aplikasi |
| Navigasi | Aba-aba belokan, tanjakan, off-route dari GPX — **sudah offline** | — |

## 3. Keputusan (dijawab pemilik 2026-11-19)

| # | Pertanyaan | Keputusan | Catatan |
|---|---|---|---|
| K1 | Arti "usaha terbaik otomatis" | **(c) Keduanya**: rekor durasi otomatis (kecepatan & power 30 dtk / 1 / 3 / 5 / 20 mnt) **dan** "Tandai sprint" sekali, lalu dicocokkan otomatis di ride berikutnya | D-109 |
| K2 | Tema | Tambah **Tema Terang**; pengguna memilih di **Setelan → Tampilan → Tema: Gelap · Terang · Ikuti HP**. Default **Gelap** (tampilan saat ini tidak berubah bagi pengguna lama) | D-110 |
| K3 | Gaya grafik | **Susunan seperti referensi** (judul bagian, grafik dengan angka sumbu & garis bantu, daftar angka label-kiri/nilai-kanan), **warna dari token RideComp** (gelap & terang) | D-111 |
| K4 | Peta saat offline | Diserahkan ke agent → **dua lapis**: (1) cache otomatis peta yang pernah dilihat (tanpa unduhan di muka, aman untuk OpenFreeMap); (2) **peta wilayah** dari data Protomaps/OSM (ODbL) yang dipotong per wilayah dan **di-hosting di GitHub Release proyek** (bukan dari server pihak lain, tidak melanggar larangan unduh massal) | D-112 |
| K5 | Pemotongan wilayah | **Per daerah tempat GPS berada**: aplikasi mengenali wilayah dari lokasi terakhir yang sudah diketahui (ride terakhir/persiapan ride; **tanpa menyalakan GPS tambahan**) dan menawarkan unduhan wilayah itu lewat Wi‑Fi. Satuan wilayah awal: **provinsi** (38 file); kota besar menyusul bila ukuran provinsi terlalu besar | D-113 |

Permintaan tambahan pemilik: **fitur ini tidak boleh mengganggu performa** → §8 Anggaran performa (wajib setiap task).

## 4. Daftar centang Fase 11

Centang saat task selesai (juga di `06-ROADMAP.md` dan `docs/STATUS.md`); ganti "belum dikerjakan" pada status task di §5 dengan tanggal & tautan Feature Report.

**11A Fondasi**
- [x] T11.1 Komponen grafik `RideChart`
- [x] T11.2 Tata letak halaman detail per bagian
- [x] T11.2a Perbaikan StrictMode peta (temuan uji T11.2) · [Feature Report](../reports/features/T11.2a-map-strictmode.md)
- [x] T11.2b Perbaikan peta saat aplikasi dingin (regresi T11.2a) · [Feature Report](../reports/features/T11.2b-map-cold-start.md)

**11B Analisa**
- [x] T11.3 Kecepatan
- [x] T11.4 Elevasi (rombak)
- [x] T11.5 Detak jantung
- [x] T11.5a Perbaikan tautan perkiraan HR max di Profil · [Feature Report](../reports/features/T11.5a-hr-max-estimate-link.md)
- [x] T11.6 Perkiraan zona power Z1–Z7 · [Feature Report](../reports/features/T11.6-power-zones.md)

**11C Usaha terbaik**
- [x] T11.7 Bagian "Hasil" · [Feature Report](../reports/features/T11.7-results.md)
- [x] T11.8 Usaha terbaik otomatis (rekor durasi + tandai sprint)

**11D Tema**
- [x] T11.9 Pilihan tema
- [x] T11.10 Audit tema terang

**11E Peta offline**
- [x] T11.11 Cache peta otomatis
- [x] T11.13 Paket peta per provinsi (skrip pemilik)
- [x] T11.12 Peta wilayah otomatis
- [x] T11.14 Penyimpanan, baterai, keamanan
- [x] T11.15 Redesign halaman Segmen baru (tambahan, permintaan pemilik)

**Penutup fase**
- [x] Anggaran performa §8 terukur (release, 2026-11-21, [hasil](../reports/security/phase11-release-measure.md); memori detail +19 MB pas di batas)
- [x] Uji lapangan A1–A3 **disimulasikan** (pemilik sakit, [hasil](../reports/security/phase11-field-sim.md)); uji di jalan sungguhan tetap disarankan
- [ ] Scan CVE, lisensi, MobSF, threat model — CVE (0 temuan di APK, [laporan](../reports/security/phase11-osv-scan.txt)), lisensi & threat model selesai 2026-11-21; **MobSF menunggu build release**
- [ ] Rilis beta berikutnya — **digabung dengan Fase 12 & 13** (permintaan pemilik); MobSF atas APK gabungan

## 4a. Urutan & ketergantungan

```
11A (fondasi grafik + tata letak detail)
  ├─ 11B analisa: T11.3 kecepatan · T11.4 elevasi · T11.5 HR · T11.6 zona power
  └─ 11C usaha terbaik: T11.7 bagian Hasil · T11.8 usaha terbaik otomatis (K1)
11D tema (T11.9, T11.10) — boleh paralel, tetapi semua UI baru 11A–11C wajib memakai token warna
11E peta offline: T11.11 cache → T11.13 paket peta per provinsi (skrip pemilik) → T11.12 peta wilayah otomatis → T11.14
```
Satu task per branch (`feature/T11.x-…`). Uji perangkat oleh subagent Sonnet; logika & kode di model utama (preferensi pemilik).

---

## 5. Spesifikasi per task

### Fase 11A — Fondasi

#### T11.1 Komponen grafik (`:ui-common`)
- [x] **Status:** selesai 2026-11-19 · Feature Report: [`T11.1-ride-chart.md`](../reports/features/T11.1-ride-chart.md) · D-115
- `RideChart` (Compose `Canvas`, **tanpa library baru**, DEP-02): grafik area/garis terhadap **jarak** (sumbu X km/mi sesuai satuan), **angka sumbu Y** "rapi" (1-2-5 × 10ⁿ, 3–5 garis), garis bantu tipis, label satuan di pojok sumbu.
- **Lapisan latar opsional** (mis. elevasi abu-abu di belakang kecepatan, seperti referensi).
- **Sentuh & geser** menampilkan penanda vertikal + nilai pada titik itu (opsional fase ini, wajib bila murah).
- Data: maksimal **±300 titik** per seri (diturunkan dari sampel 1 Hz di `:core`, rata-rata per ember jarak) agar ringan di HP low-end; perhitungan di `Dispatchers.Default`.
- Celah data (sensor putus, tanpa GPS) digambar putus, bukan nol.
- Warna dari token tema; teks memakai skala tipografi; aksesibel (`contentDescription` ringkasan: "Kecepatan, rata-rata 20,9 km/j, maks 50,4").
- Preview `@ThemePreviews` gelap & terang.

#### T11.2 Tata letak halaman detail
- [x] **Status:** selesai 2026-11-19 · Feature Report: [`T11.2-detail-sections.md`](../reports/features/T11.2-detail-sections.md) · D-116
- Ganti grafik bertab menjadi **bagian vertikal**: Judul & peta → Ringkasan (kotak statistik ringkas) → **Hasil** (11C) → **Kecepatan** → **Elevasi** → **Detak jantung** (bila ada HR) → **Zona power** → Cadence (bila ada) → Lap → Bagikan & Ekspor → Lainnya.
- Setiap bagian: judul + ikon info (ⓘ membuka penjelasan singkat), grafik, lalu **daftar angka** label-kiri/nilai-kanan (seperti referensi).
- Bagian tanpa data **disembunyikan** (bukan kosong), kecuali HR yang menampilkan kartu ajakan (T11.5).
- Lazy: bagian di bawah layar baru dihitung saat akan tampil; seri grafik dimuat sekali per layar oleh ViewModel (use case `LoadActivityAnalysisUseCase` di `:core`).

### Fase 11B — Analisa

#### T11.3 Kecepatan
- [x] **Status:** selesai 2026-11-19 · Feature Report: [`T11.3-speed-analysis.md`](../reports/features/T11.3-speed-analysis.md) · D-118
- Grafik kecepatan vs jarak (warna aksen/trek) + **elevasi abu-abu di belakang**.
- Angka: **Kec. rata-rata** (berdasarkan waktu bergerak), **Kec. maks** (puncak tersaring, bukan lonjakan GPS: median 3 sampel), **Waktu bergerak**, **Waktu total**.
- Satuan mengikuti Setelan (km/j atau mph).

#### T11.4 Elevasi (rombak)
- [x] **Status:** selesai 2026-11-19 · Feature Report: [`T11.4-elevation.md`](../reports/features/T11.4-elevation.md)
- Grafik ketinggian vs jarak dengan angka sumbu & garis bantu; skala Y tidak dimulai dari 0 (rentang data + margin) agar bentuk terlihat.
- Angka: **Kenaikan elevasi**, **Elevasi maks**; tambahan bila ada: Penurunan, Elevasi min.
- Tanpa data ketinggian: bagian disembunyikan.

#### T11.5 Detak jantung (bila ada sensor HR)
- [x] **Status:** selesai 2026-11-19 · Feature Report: [`T11.5-heart-rate.md`](../reports/features/T11.5-heart-rate.md) · D-120
- Grafik HR vs jarak (atau waktu) + garis rata-rata.
- Angka: **HR rata-rata**, **HR maks**.
- **Waktu di zona Z1–Z5**: batang horizontal per zona (warna zona yang sudah ada), % dan durasi, rentang bpm per zona dari HR max profil. Keterangan "Berdasarkan HR max X bpm" (+ tautan ubah di Profil).
- Tanpa HR: kartu "Pasang sensor detak jantung untuk analisa zona" + tautan ke Sensor (gaya kartu gelap RideComp, bukan banner berwarna).
- Profil HR max belum diisi → zona dari perkiraan umur bila ada, jika tidak tampilkan grafik saja + ajakan isi profil.

#### T11.6 Perkiraan zona power Z1–Z7
- [x] **Status:** selesai 2026-11-19 · Feature Report: [`T11.6-power-zones.md`](../reports/features/T11.6-power-zones.md) · D-121
- Sumber: **power meter** bila ada (`powerW`); jika tidak, **estimasi** (`estPowerW`) dan judul memuat kata **"Perkiraan"**.
- FTP: dari profil; bila kosong, **perkiraan FTP**. `05-DATA-MODEL` §3.9 baru memuat batas zona, belum ada rumus perkiraan FTP: tetapkan dan tulis di §3.9 (usulan: 95% dari power 20 menit terbaik dalam 6 minggu terakhir; bila belum ada, berat × 2,0 W/kg untuk pemula sesuai level, ditandai "perkiraan"). Teks: "Berdasarkan berat X kg dan perkiraan FTP Y W".
- Batang Z1 (bawah) … Z7 (atas) dengan **% waktu bergerak** dan **rentang watt** per zona (batas `ZONE_UPPER_PERCENT_FTP` × FTP).
- Hanya waktu bergerak yang dihitung; sampel tanpa power diabaikan.
- **Larangan tetap berlaku:** estimasi power tidak pernah ditulis ke field `power` FIT (CLAUDE.md).

### Fase 11C — Usaha terbaik

#### T11.7 Bagian "Hasil" di halaman detail
- [x] **Status:** selesai 2026-11-19 · Feature Report: [T11.7](../reports/features/T11.7-results.md) · D-122
- Baris angka: **Usaha terbaik** (jumlah rekor/PR ride ini) · **Segmen** (jumlah segmen yang dilewati).
- Daftar: tiap segmen yang dilewati (ikon pin, nama, jarak, waktu, kecepatan, power bila ada) dengan tanda **🏆 PR** / peringkat pribadi (1st/2nd/3rd terbaik).
- Rekor baru ride ini (dari `BestEffortRepository`), mis. "Tercepat 5 km · 9:41 · PR".
- "Lihat semua hasil" bila > 3 baris.
- Data sudah dihitung oleh `SegmentsStep` & best efforts: task ini terutama UI + query ringkas.

#### T11.8 Usaha terbaik otomatis (bergantung K1)
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.8-duration-efforts.md`](../reports/features/T11.8-duration-efforts.md) · D-125 (sprint dari baris rekor, nama "Sprint 20 Nov")
- **(a) Rekor durasi:** tambah `BestEffortKey` untuk **kecepatan terbaik 30 dtk, 1, 3, 5, 20 mnt** (dan power 30 dtk, 3 mnt bila belum ada). Algoritma jendela geser O(n) di `:core`, hanya waktu bergerak. `storageKey` baru; **re-analisa** ride lama lewat mekanisme versi analisa yang ada (`AnalysisConfig`), tanpa migrasi destruktif.
- **(b) Tandai sprint:** di halaman detail, aksi "Tandai sebagai sprint/segmen" langsung dari grafik kecepatan (seret rentang) atau dari "Usaha terbaik" (mis. "simpan jalur 3 menit tercepat sebagai segmen"). Nama default dari nama jalan bila tersedia, jika tidak "Sprint 18 Nov". Ride berikutnya dicocokkan otomatis (sudah ada).
- Tampil di bagian Hasil (T11.7) dan layar "Rekor baru".

### Fase 11D — Tema

#### T11.9 Pilihan tema
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.9-theme-choice.md`](../reports/features/T11.9-theme-choice.md) · D-126 (kunci `app.theme`, salinan cepat untuk frame pertama)
- Setelan → Tampilan → **Tema: Gelap · Terang · Ikuti HP** (default **Gelap**, K2). Disimpan di tabel `settings` (`ui.theme`).
- Diterapkan di root (`RideCompTheme(themeMode)`), berlaku langsung tanpa restart; status bar & navigation bar ikut.
- Pengecualian yang tetap gelap: kartu share bergaya gelap, layar hitung mundur insiden.

#### T11.10 Audit tampilan terang
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.10-light-audit.md`](../reports/features/T11.10-light-audit.md) · D-127 (`ThemeContrastTest`, token terang pekat, widget ikut HP)
- Semua layar diperiksa di tema terang (kontras WCAG 4.5:1 teks, 3:1 elemen besar), terutama: **layar ride di bawah matahari** (tema terang mungkin lebih terbaca), peta (gaya terang sudah ada), grafik baru, galeri share, widget (ikuti tema sistem), onboarding.
- Uji perangkat kedua tema; screenshot sebelum/sesudah di Feature Report.

### Fase 11E — Peta offline

#### T11.11 Cache peta otomatis
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.11-map-cache.md`](../reports/features/T11.11-map-cache.md) · D-128 (batas 50/150/300 MB pilihan pemilik; "Mati" tetap memakai cache)
- Potongan peta online yang pernah dilihat disimpan di cache MapLibre (batas ukuran default **150 MB**, LRU), lalu dipakai saat sinyal hilang; area yang belum pernah dimuat tetap jatuh ke garis rute (D-90).
- Ubah perilaku D-90: tanpa internet, **tampilkan peta bila ada cache/region yang menutupi area**, bukan langsung garis.
- Tidak ada unduhan di muka (bukan *bulk download*): aman untuk OpenFreeMap.
- Setelan → Peta: ukuran cache + "Hapus cache peta".

#### T11.12 Peta wilayah otomatis (K4, K5)
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.12-region-maps.md`](../reports/features/T11.12-region-maps.md) · D-130 (dijeda saat aplikasi ke latar; daftar manual di Setelan → Peta)
- **Indeks wilayah** kecil (JSON di dalam APK, ±20 KB): nama provinsi, kotak batas (bbox), nama file, ukuran, versi, SHA-256. Dibuat oleh skrip pemilik (lihat T11.13).
- **Deteksi wilayah:** dari lokasi terakhir yang sudah ada (titik akhir ride terakhir atau posisi di persiapan ride) dicocokkan ke bbox; **tidak ada permintaan GPS baru** (BAT-01).
- **Tawaran:** kartu di Beranda / Setelan → Peta offline: "Unduh peta wilayahmu: Jawa Tengah · ± 120 MB · Wi‑Fi" (Unduh / Nanti); muncul lagi bila pengguna berada di wilayah lain yang belum diunduh. Tidak pernah saat ride berjalan.
- **Unduhan:** HTTPS dari GitHub Release proyek, bisa dilanjutkan (HTTP Range), progres, dibatalkan; **hanya Wi‑Fi** (default, bisa diubah); hanya saat aplikasi terbuka (tanpa kerja latar baru; bila ternyata wajib kerja latar → ajukan dependency/izin dulu). Verifikasi SHA-256 + header PMTiles (yang sudah ada) sebelum dipakai; file sementara dihapus bila gagal.
- **Pemakaian:** file masuk mekanisme peta offline yang ada (`.pmtiles` di internal storage); saat tanpa internet peta wilayah dipakai otomatis, baik ride bebas maupun navigasi rute.
- **Pembaruan peta:** indeks berisi versi; tawaran "Perbarui peta (data OSM lebih baru)" paling cepat tiap 3 bulan, hanya lewat Wi‑Fi.

#### T11.13 Paket peta per provinsi (pekerjaan pemilik + skrip)
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.13-region-packs.md`](../reports/features/T11.13-region-packs.md) · D-129 (zoom 0–14; DI Yogyakarta di rilis `maps-2026-11`; 37 provinsi lain dijalankan pemilik dengan skrip)
- Skrip `scripts/maps/build-regions` (di Mac pemilik, **bukan** bagian aplikasi): memotong build Protomaps per provinsi (`pmtiles extract --bbox`/batas wilayah, zoom 0–15), menghitung SHA-256, menulis indeks JSON, mengunggah ke GitHub Release `maps-YYYY-MM`.
- Butuh alat `pmtiles` (CLI resmi Protomaps) di Mac pemilik: unduhan alat baru → **minta izin** saat task dimulai.
- Atribusi "© OpenStreetMap contributors, Protomaps" di peta & catatan rilis peta; lisensi ODbL dicantumkan di Lisensi open source.
- Bila satu provinsi > 300 MB, pecah menjadi kota/kabupaten besar (indeks mendukung beberapa file per wilayah).

#### T11.14 Penyimpanan, baterai, keamanan
- [x] **Status:** selesai 2026-11-20 · Feature Report: [`T11.14-map-storage.md`](../reports/features/T11.14-map-storage.md) · D-131 (batas total 500 MB/1 GB/2 GB, default 1 GB)
- Batas total peta offline (mis. 1 GB, bisa diubah), peringatan ruang < 500 MB, validasi header PMTiles (sudah ada), HTTPS saja, ukuran unduhan dicek dulu (SEC-IN), unduhan dapat dibatalkan, file sementara dibersihkan.
- Atribusi OpenStreetMap/Protomaps tampil di peta offline.


#### T11.15 Redesign halaman Segmen baru (tambahan, permintaan pemilik)
- [x] **Status:** selesai 2026-11-21 · Feature Report: [`T11.15-segment-editor.md`](../reports/features/T11.15-segment-editor.md) · D-133
- Tiga langkah: ① pilih bagian (peta, pintasan Seluruh ride/tanjakan, grafik + slider, −/+ 50 m), ② periksa (panjang, kenaikan, kemiringan, waktu, kecepatan, elevasi), ③ beri nama & simpan.
---

## 6. Data & skema

- 11A–11C: tidak ada tabel baru untuk analisa (dihitung dari sampel saat membuka detail; cache di memori ViewModel). Rekor durasi baru memakai tabel `best_efforts` yang ada (kunci baru). Waktu di zona power boleh disimpan di analisa per aktivitas bila perhitungannya mahal (ajukan dulu).
- 11D: satu baris `settings`.
- 11E: peta offline tetap berupa file `.pmtiles` di internal storage (mekanisme `OfflineMapRepository` yang ada, tanpa tabel); file hasil unduhan diberi nama aplikasi + metadata wilayah. Cache MapLibre dikelola MapLibre di cache dir.
- Setiap perubahan skema: migrasi eksplisit, tanpa `fallbackToDestructiveMigration`, data ride tidak pernah dihapus.

## 7. Risiko

| Risiko | Mitigasi |
|---|---|
| Grafik berat di HP 2 GB | ≤ 300 titik per seri, hitung di Default dispatcher, Canvas tanpa library, lazy per bagian |
| Kec. maks palsu dari lonjakan GPS | Penyaringan median, batas percepatan (sudah ada di pipeline) |
| Estimasi power dianggap akurat | Label "Perkiraan", tidak pernah masuk FIT `power` |
| Kebijakan unduh peta | K4: Protomaps; OpenFreeMap hanya cache pasif |
| Ukuran unduhan peta | Ukuran ditampilkan sebelum unduh, Wi‑Fi default, batas total, provinsi besar dipecah |
| Biaya/beban hosting peta | Hosting di GitHub Release proyek (gratis); pembaruan peta paling cepat tiap 3 bulan |
| Tema terang di layar ride | Audit kontras & uji di bawah matahari (T11.10) |

## 8. Anggaran performa (wajib setiap task, permintaan pemilik)

Diukur dengan **build rilis** (R8) di HP uji low-end (realme RMX3830; target HP RAM 2–3 GB), dibandingkan dengan sebelum task. Hasil masuk Feature Report.

| Ukuran | Batas |
|---|---|
| Buka halaman detail ride 1 jam (dari ketuk sampai grafik pertama tampil) | **< 1 dtk**; tidak ada frame > 700 ms (`adb shell dumpsys gfxinfo`) |
| Titik per grafik | **≤ 300**; diturunkan di `Dispatchers.Default`, sekali per layar |
| Scroll halaman detail | Tanpa jank berarti (≥ 90% frame < 16 ms di gfxinfo) |
| Memori saat detail terbuka | Naik **< 20 MB** dibanding sebelum Fase 11 (`dumpsys meminfo`); dilepas saat keluar (LeakCanary bersih) |
| Layar ride | **Tidak berubah**: tidak ada perhitungan analisa baru saat merekam; update UI tetap ≤ 1 Hz |
| Kerja latar & GPS | **Tidak ada** WorkManager/alarm/service baru; **tidak ada** permintaan GPS baru (deteksi wilayah memakai lokasi terakhir yang sudah ada) |
| Jaringan | Unduhan peta hanya saat aplikasi terbuka, default Wi‑Fi, tidak pernah saat ride |
| Penyimpanan | Cache peta ≤ 150 MB (LRU); total peta offline dibatasi (default 1 GB) |
| Ukuran APK | Naik **< 1 MB** per ABI (indeks wilayah ±20 KB; tanpa library baru) |
| Rekor durasi baru | Dihitung di analisa setelah ride disimpan (yang sudah ada), O(n), < 300 ms untuk ride 3 jam |

Melampaui batas = bug yang diperbaiki sebelum task selesai.

## 9. Definition of Done Fase 11

- Semua task 11A–11E selesai dengan Feature Report; keputusan K1–K5 tercatat (D-109 … D-113); **anggaran performa §8 terpenuhi** dengan angka terukur.
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug` lulus; uji perangkat (subagent Sonnet) untuk tiap task UI di **tema gelap dan terang**.
- Uji lapangan: ride ≥ 30 menit dengan HR (analisa HR & zona), ride rute yang sama 2× (usaha terbaik/segmen PR muncul di hari ke-2), navigasi rute dengan **mode pesawat** setelah mengunduh peta wilayah (peta tetap tampil).
- Akhir fase: scan CVE, lisensi, MobSF, threat model diperbarui (peta offline & unduhan).
- Rilis beta berikutnya dengan catatan rilis & cek pembaruan dalam aplikasi.
