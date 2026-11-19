# 14 — Rencana Fase 11: Analisa Ride, Usaha Terbaik, Tema & Peta Offline

**Status:** rencana (belum dikerjakan). Disusun 2026-11-19 atas permintaan pemilik.
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

## 3. Keputusan terbuka (jawab sebelum task terkait dimulai)

| # | Pertanyaan | Pilihan | Rekomendasi agent | Menghalangi |
|---|---|---|---|---|
| K1 | Arti "usaha terbaik otomatis" | (a) **Rekor durasi otomatis**: kecepatan & power terbaik 30 dtk / 1 / 3 / 5 / 20 mnt tiap ride, PR bila melampaui ride sebelumnya. (b) **Tandai sprint sekali**: pengguna menandai jalur di grafik/peta (segmen), ride berikutnya dicocokkan otomatis. (c) Keduanya | **(c)**: (a) tanpa usaha apa pun; (b) untuk jalur favorit seperti contoh Soekarno Hatta (Strava bisa otomatis karena jutaan segmen komunitas; RideComp offline tanpa server) | T11.8 |
| K2 | Pilihan tema di onboarding? | Ya (langkah Bahasa) / Hanya di Setelan | Hanya di Setelan, default **Ikuti HP** | T11.9 |
| K3 | Gaya grafik | Ikuti referensi (area berwarna, angka sumbu, daftar angka di bawah) / gaya kartu gelap RideComp saat ini | Ikuti susunan referensi, warna dari token tema (cocok gelap & terang) | T11.1 |
| K4 | Sumber unduhan peta offline | OpenFreeMap (kebijakan unduh massal **belum ditemukan**; server peta OpenStreetMap sendiri melarangnya) / **Protomaps** (data ODbL, memang disediakan untuk diunduh, potongan wilayah) | Protomaps untuk unduhan; OpenFreeMap tetap untuk tampilan online. Larangan CLAUDE.md: tidak memakai server yang melarang unduh massal | T11.12, T11.13 |
| K5 | Cara memotong wilayah Protomaps | (a) Aplikasi membaca rentang file langsung dari build Protomaps (HTTP range, seperti `pmtiles extract`), (b) file per provinsi/kota disiapkan pemilik (mis. di GitHub Release), (c) pengguna tetap impor manual | (a) bila build Protomaps mengizinkan pemakaian langsung dari aplikasi; jika tidak, (b) | T11.12, T11.13 |

Keputusan yang sudah diambil dicatat sebagai D-1xx di `reports/DECISIONS.md`.

## 4. Urutan & ketergantungan

```
11A (fondasi grafik + tata letak detail)
  ├─ 11B analisa: T11.3 kecepatan · T11.4 elevasi · T11.5 HR · T11.6 zona power
  └─ 11C usaha terbaik: T11.7 bagian Hasil · T11.8 usaha terbaik otomatis (K1)
11D tema (T11.9, T11.10) — boleh paralel, tetapi semua UI baru 11A–11C wajib memakai token warna
11E peta offline: T11.11 cache (tanpa keputusan) → T11.12 rute → T11.13 area (K4, K5) → T11.14
```
Satu task per branch (`feature/T11.x-…`). Uji perangkat oleh subagent Sonnet; logika & kode di model utama (preferensi pemilik).

---

## 5. Spesifikasi per task

### Fase 11A — Fondasi

#### T11.1 Komponen grafik (`:ui-common`)
- `RideChart` (Compose `Canvas`, **tanpa library baru**, DEP-02): grafik area/garis terhadap **jarak** (sumbu X km/mi sesuai satuan), **angka sumbu Y** "rapi" (1-2-5 × 10ⁿ, 3–5 garis), garis bantu tipis, label satuan di pojok sumbu.
- **Lapisan latar opsional** (mis. elevasi abu-abu di belakang kecepatan, seperti referensi).
- **Sentuh & geser** menampilkan penanda vertikal + nilai pada titik itu (opsional fase ini, wajib bila murah).
- Data: maksimal **±300 titik** per seri (diturunkan dari sampel 1 Hz di `:core`, rata-rata per ember jarak) agar ringan di HP low-end; perhitungan di `Dispatchers.Default`.
- Celah data (sensor putus, tanpa GPS) digambar putus, bukan nol.
- Warna dari token tema; teks memakai skala tipografi; aksesibel (`contentDescription` ringkasan: "Kecepatan, rata-rata 20,9 km/j, maks 50,4").
- Preview `@ThemePreviews` gelap & terang.

#### T11.2 Tata letak halaman detail
- Ganti grafik bertab menjadi **bagian vertikal**: Judul & peta → Ringkasan (kotak statistik ringkas) → **Hasil** (11C) → **Kecepatan** → **Elevasi** → **Detak jantung** (bila ada HR) → **Zona power** → Cadence (bila ada) → Lap → Bagikan & Ekspor → Lainnya.
- Setiap bagian: judul + ikon info (ⓘ membuka penjelasan singkat), grafik, lalu **daftar angka** label-kiri/nilai-kanan (seperti referensi).
- Bagian tanpa data **disembunyikan** (bukan kosong), kecuali HR yang menampilkan kartu ajakan (T11.5).
- Lazy: bagian di bawah layar baru dihitung saat akan tampil; seri grafik dimuat sekali per layar oleh ViewModel (use case `LoadActivityAnalysisUseCase` di `:core`).

### Fase 11B — Analisa

#### T11.3 Kecepatan
- Grafik kecepatan vs jarak (warna aksen/trek) + **elevasi abu-abu di belakang**.
- Angka: **Kec. rata-rata** (berdasarkan waktu bergerak), **Kec. maks** (puncak tersaring, bukan lonjakan GPS: median 3 sampel), **Waktu bergerak**, **Waktu total**.
- Satuan mengikuti Setelan (km/j atau mph).

#### T11.4 Elevasi (rombak)
- Grafik ketinggian vs jarak dengan angka sumbu & garis bantu; skala Y tidak dimulai dari 0 (rentang data + margin) agar bentuk terlihat.
- Angka: **Kenaikan elevasi**, **Elevasi maks**; tambahan bila ada: Penurunan, Elevasi min.
- Tanpa data ketinggian: bagian disembunyikan.

#### T11.5 Detak jantung (bila ada sensor HR)
- Grafik HR vs jarak (atau waktu) + garis rata-rata.
- Angka: **HR rata-rata**, **HR maks**.
- **Waktu di zona Z1–Z5**: batang horizontal per zona (warna zona yang sudah ada), % dan durasi, rentang bpm per zona dari HR max profil. Keterangan "Berdasarkan HR max X bpm" (+ tautan ubah di Profil).
- Tanpa HR: kartu "Pasang sensor detak jantung untuk analisa zona" + tautan ke Sensor (gaya kartu gelap RideComp, bukan banner berwarna).
- Profil HR max belum diisi → zona dari perkiraan umur bila ada, jika tidak tampilkan grafik saja + ajakan isi profil.

#### T11.6 Perkiraan zona power Z1–Z7
- Sumber: **power meter** bila ada (`powerW`); jika tidak, **estimasi** (`estPowerW`) dan judul memuat kata **"Perkiraan"**.
- FTP: dari profil; bila kosong, **perkiraan FTP**. `05-DATA-MODEL` §3.9 baru memuat batas zona, belum ada rumus perkiraan FTP: tetapkan dan tulis di §3.9 (usulan: 95% dari power 20 menit terbaik dalam 6 minggu terakhir; bila belum ada, berat × 2,0 W/kg untuk pemula sesuai level, ditandai "perkiraan"). Teks: "Berdasarkan berat X kg dan perkiraan FTP Y W".
- Batang Z1 (bawah) … Z7 (atas) dengan **% waktu bergerak** dan **rentang watt** per zona (batas `ZONE_UPPER_PERCENT_FTP` × FTP).
- Hanya waktu bergerak yang dihitung; sampel tanpa power diabaikan.
- **Larangan tetap berlaku:** estimasi power tidak pernah ditulis ke field `power` FIT (CLAUDE.md).

### Fase 11C — Usaha terbaik

#### T11.7 Bagian "Hasil" di halaman detail
- Baris angka: **Usaha terbaik** (jumlah rekor/PR ride ini) · **Segmen** (jumlah segmen yang dilewati).
- Daftar: tiap segmen yang dilewati (ikon pin, nama, jarak, waktu, kecepatan, power bila ada) dengan tanda **🏆 PR** / peringkat pribadi (1st/2nd/3rd terbaik).
- Rekor baru ride ini (dari `BestEffortRepository`), mis. "Tercepat 5 km · 9:41 · PR".
- "Lihat semua hasil" bila > 3 baris.
- Data sudah dihitung oleh `SegmentsStep` & best efforts: task ini terutama UI + query ringkas.

#### T11.8 Usaha terbaik otomatis (bergantung K1)
- **(a) Rekor durasi:** tambah `BestEffortKey` untuk **kecepatan terbaik 30 dtk, 1, 3, 5, 20 mnt** (dan power 30 dtk, 3 mnt bila belum ada). Algoritma jendela geser O(n) di `:core`, hanya waktu bergerak. `storageKey` baru; **re-analisa** ride lama lewat mekanisme versi analisa yang ada (`AnalysisConfig`), tanpa migrasi destruktif.
- **(b) Tandai sprint:** di halaman detail, aksi "Tandai sebagai sprint/segmen" langsung dari grafik kecepatan (seret rentang) atau dari "Usaha terbaik" (mis. "simpan jalur 3 menit tercepat sebagai segmen"). Nama default dari nama jalan bila tersedia, jika tidak "Sprint 18 Nov". Ride berikutnya dicocokkan otomatis (sudah ada).
- Tampil di bagian Hasil (T11.7) dan layar "Rekor baru".

### Fase 11D — Tema

#### T11.9 Pilihan tema
- Setelan → Tampilan → **Tema: Ikuti HP · Gelap · Terang** (default Ikuti HP, K2). Disimpan di tabel `settings` (`ui.theme`).
- Diterapkan di root (`RideCompTheme(themeMode)`), berlaku langsung tanpa restart; status bar & navigation bar ikut.
- Pengecualian yang tetap gelap: kartu share bergaya gelap, layar hitung mundur insiden.

#### T11.10 Audit tampilan terang
- Semua layar diperiksa di tema terang (kontras WCAG 4.5:1 teks, 3:1 elemen besar), terutama: **layar ride di bawah matahari** (tema terang mungkin lebih terbaca), peta (gaya terang sudah ada), grafik baru, galeri share, widget (ikuti tema sistem), onboarding.
- Uji perangkat kedua tema; screenshot sebelum/sesudah di Feature Report.

### Fase 11E — Peta offline

#### T11.11 Cache peta otomatis
- Potongan peta online yang pernah dilihat disimpan di cache MapLibre (batas ukuran default **150 MB**, LRU), lalu dipakai saat sinyal hilang; area yang belum pernah dimuat tetap jatuh ke garis rute (D-90).
- Ubah perilaku D-90: tanpa internet, **tampilkan peta bila ada cache/region yang menutupi area**, bukan langsung garis.
- Tidak ada unduhan di muka (bukan *bulk download*): aman untuk OpenFreeMap.
- Setelan → Peta: ukuran cache + "Hapus cache peta".

#### T11.12 "Unduh peta untuk rute ini" (K4, K5)
- Di detail rute: tombol **Unduh peta offline** → perkiraan ukuran ("± 15 MB") → progres → selesai; koridor ±2 km di sepanjang rute, zoom sampai 15.
- Disimpan sebagai file `.pmtiles` di internal storage seperti peta offline yang ada (SEC-STO-01), tercantum di Setelan → Peta offline (layar yang ada), bisa dihapus.
- Default **hanya Wi‑Fi** (bisa diubah); jeda/lanjut bila koneksi putus; tidak berjalan saat ride (tanpa foreground service tambahan; bila perlu `WorkManager` → ajukan dependency).
- Saat ride memakai rute tersebut tanpa internet: peta tampil penuh.

#### T11.13 "Unduh peta area" (opsional)
- Pilih kota/provinsi dari daftar, atau kotak di peta → ukuran → unduh. Sumber per K4/K5.

#### T11.14 Penyimpanan, baterai, keamanan
- Batas total peta offline (mis. 1 GB, bisa diubah), peringatan ruang < 500 MB, validasi header PMTiles (sudah ada), HTTPS saja, ukuran unduhan dicek dulu (SEC-IN), unduhan dapat dibatalkan, file sementara dibersihkan.
- Atribusi OpenStreetMap/Protomaps tampil di peta offline.

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
| Ukuran unduhan peta | Perkiraan ukuran sebelum unduh, Wi‑Fi default, batas total |
| Tema terang di layar ride | Audit kontras & uji di bawah matahari (T11.10) |

## 8. Definition of Done Fase 11

- Semua task 11A–11E selesai dengan Feature Report; keputusan K1–K5 tercatat sebagai D-1xx.
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug` lulus; uji perangkat (subagent Sonnet) untuk tiap task UI di **tema gelap dan terang**.
- Uji lapangan: ride ≥ 30 menit dengan HR (analisa HR & zona), ride rute yang sama 2× (usaha terbaik/segmen PR muncul di hari ke-2), navigasi rute dengan **mode pesawat** setelah "Unduh peta untuk rute ini" (peta tetap tampil).
- Akhir fase: scan CVE, lisensi, MobSF, threat model diperbarui (peta offline & unduhan).
- Rilis beta berikutnya dengan catatan rilis & cek pembaruan dalam aplikasi.
