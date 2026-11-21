# 06 — Roadmap & Task Breakdown

Kerjakan **berurutan**. **Setiap task** mengikuti alur di `10-FEATURE-CHECKLIST.md` (Rencana → persetujuan → implementasi + test → verifikasi → Feature Report). Fase selesai hanya setelah lolos Phase Gate (`10-FEATURE-CHECKLIST.md`) dan acceptance criteria di `07-TEST-PLAN.md`.

| Fase | Nama | Hasil | Status (selesai/total task) |
|---|---|---|---|
| 0 | Fondasi | Proyek Kotlin multi-modul berjalan, tema, DB, lint & test | ✅ Selesai (13/13) |
| 1 | Perekam GPS (MVP-a) | Rekam ride GPS dengan andal, jeda/lanjut otomatis, pengingat, mode baterai kritis, simpan, export FIT | ✅ Selesai (19/20, 1 ditunda: T1.16) |
| 2 | Sensor BLE, keselamatan & backup (MVP-b) | HR, speed/cadence, power; profil aktivitas; deteksi insiden + SMS darurat; backup & restore | ✅ Selesai (17/17) |
| 3 | Navigasi rute | Import GPX, peta offline, off-route, info tanjakan (ClimbPro), peringatan belokan | ✅ Selesai (13/13) |
| 4 | Latihan & analisis | Personal record, segmen pribadi, beban latihan, kartu share | ✅ Selesai (9/9) |
| 5 | Strava API (opsional) | Upload otomatis bila pengguna berlangganan Strava | ✅ Selesai (5/5) |
| 6 | iOS | Port & uji di iPhone | ⬜ Belum mulai (0/6) |
| 7, 9 | Fitur lanjutan, trainer & widget | Suara, kembali ke start, komponen sepeda, rencana latihan, smart trainer, widget | ✅ Selesai (7/7) |
| 10 | Rilis beta & masukan pengguna | Open source, rilis 0.9.0–0.9.3, galeri share, sepeda lipat, cek pembaruan, onboarding baru | ✅ Selesai (7/7) |
| 11 | Analisa ride, usaha terbaik, tema & peta offline | Halaman detail ala analisa (kecepatan, elevasi, HR, zona power), usaha terbaik, tema terang/gelap, peta tanpa internet (`14-PHASE11-PLAN.md`) | ✅ Selesai (15/15; rilis beta & MobSF digabung dengan Fase 12–13) |
| 12 | Detak jantung dari smartwatch | Jam tangan broadcast HR, pemindaian luas, panduan per merek, Wear OS (opsional), Aolon eksperimental (`15-PHASE12-PLAN.md`) | ✅ Selesai (5/7; T12.4 & T12.5c ditunda, D-139; rilis beta & MobSF digabung dengan Fase 13) |
| 13 | Tampilan responsif layar kecil | Semua layar benar di 320 dp; 360 dp ke atas tidak berubah (bukti screenshot) (`16-PHASE13-PLAN.md`) | ⬜ Belum mulai (0/8) |

Spesifikasi detail fitur tambahan: `12-FEATURE-SPECS.md`.

**Status fase:** ✅ selesai · 🟡 berjalan · ⬜ belum mulai (angka = task selesai/total; perbarui saat mencentang).

**Status task:** `- [x]` selesai · `- [ ]` belum dikerjakan · `- [ ] … *(Ditunda: alasan)*` ditunda. Task baru selalu ditulis sebagai `- [ ]` **sebelum** dikerjakan, dan dicentang di file ini, di rencana fasenya (mis. `14-PHASE11-PLAN.md`), dan di `docs/STATUS.md` saat task selesai (`10-FEATURE-CHECKLIST.md`).

---

## Fase 0 — Fondasi
- [x] T0.1 Buat proyek Android Kotlin + Compose (`com.ridecomp.app` atau sesuai keputusan pemilik), minSdk 26, Gradle Kotlin DSL + version catalog, R8 di release, ABI splits.
- [x] T0.2 Struktur multi-modul sesuai `03-ARCHITECTURE.md` §3; pastikan `:core` adalah modul Kotlin/JVM murni (build gagal bila mengimpor `android.*`).
- [x] T0.3 Tema gelap + design tokens (`02-DESIGN.md` §4), komponen `BigButton`, `DataField` (dengan angka tabular & auto-fit).
- [x] T0.4 Navigation Compose & kerangka layar: Home, Riwayat, Sensor, Rute, Pengaturan (placeholder).
- [x] T0.5 Room DB + skema awal (`profile`, `settings`, `activities`, `track_points`, `events`, `laps`) + migrasi.
- [x] T0.6 i18n via string resources (`values/` en, `values-in/` id) + Koin DI root.
- [x] T0.7 Tooling kualitas: `.editorconfig` (sudah ada), ktlint, detekt, Android Lint, Kover (coverage `:core` ≥ 80%), satu perintah verifikasi (`./gradlew ktlintCheck detekt lint test koverVerify`). Sesuaikan nama task bila plugin berbeda, dengan persetujuan.
- [x] T0.8 Usulkan daftar dependency + versi → tunggu persetujuan.
- [x] T0.9 Fondasi reliabilitas: LeakCanary (`debugImplementation`), StrictMode `detectAll` di build debug, uncaught exception handler → crash log lokal (ERR-06).
- [x] T0.10 Abstraksi dasar di `:core`: `Clock`, `DispatcherProvider`, `Logger`, ring buffer generik + implementasi Android & fake untuk test.
- [x] T0.11 Penjaga arsitektur: build gagal bila `:core` mengimpor `android.*`/`androidx.*`, atau `:feature-*` bergantung pada `:data`/`:platform` (mis. aturan detekt/Konsist atau pemeriksaan Gradle; ajukan pilihan).
- [x] T0.12 Folder `docs/reports/` + Feature Report pertama (untuk Fase 0).
- [x] T0.13 Fondasi keamanan (`11-SECURITY-RULES.md`): repository terbatas, dependency locking, `verification-metadata.xml`, validasi Gradle Wrapper, scan CVE (ajukan pilihan: OWASP Dependency-Check / OSV-Scanner), laporan lisensi, gitleaks pre-commit, Android Lint Security = error, Network Security Config (tanpa cleartext), aturan backup, semua komponen `exported=false`, `.gitignore` rahasia (sudah ada).

**DoD:** app terpasang di HP fisik, navigasi antar layar jalan, perintah verifikasi lulus tanpa warning, LeakCanary & StrictMode aktif di debug, APK release ter-build.

## Fase 1 — Perekam GPS (MVP-a)
- [x] T1.1 Onboarding izin (lokasi, notifikasi, optimasi baterai) + panduan per merek HP.
- [x] T1.2 Profil (berat, sepeda, keliling roda, HR max, FTP, umur, jenis kelamin opsional).
- [x] T1.3 Foreground service + RideEngine state machine (start/pause/resume/stop/discard).
- [x] T1.4 Stream GPS 1 Hz + filter akurasi + jarak + speed.
- [x] T1.4b Jeda & lanjut otomatis lengkap (`12-FEATURE-SPECS.md` §1): setting ambang & durasi, tahan GPS drift, indikator "JEDA OTOMATIS", prompt diam lama, event FIT.
- [x] T1.5 SampleMerger 1 record/detik + DbWriter batch 5 detik + checkpoint.
- [x] T1.6 Recovery setelah crash/kill (banner di Home).
- [x] T1.7 Elevasi (GPS/barometer), total tanjakan, grade.
- [x] T1.8 Layar ride: halaman data (konfigurasi default), status bar, kontrol pause/lap, layar pause.
- [x] T1.9 Lap manual + auto-lap jarak.
- [x] T1.10 Notifikasi foreground menampilkan waktu, jarak, speed (update ≤ 1 Hz) + aksi Pause.
- [x] T1.11 Mode layar: selalu nyala / redup otomatis / mati.
- [x] T1.12 Ringkasan setelah ride + simpan.
- [x] T1.13 Riwayat + detail (grafik speed & elevasi, downsampled).
- [x] T1.14 FIT writer + export (share / simpan) + GPX export.
- [x] T1.15 Layar bantuan "Upload ke Strava manual".
- [ ] T1.16 Baseline Profile untuk cold start. *(Ditunda: butuh perangkat/emulator + dependency macrobenchmark — D-23.)*
- [x] T1.17 Unit test: haversine, filter GPS, auto-pause (termasuk GPS drift & tanjakan pelan), elevasi histeresis, FIT writer.
- [x] T1.18 Pengingat minum & makan (§2).
- [x] T1.19 Mode baterai kritis otomatis 20/10/5% + FIT darurat (§3), dengan `BatteryLevelSource` fake untuk test.

**DoD:** 3 ride uji di jalan (≥ 30 menit) berhasil, file FIT diterima Strava, recovery teruji (force stop app di tengah ride).

## Fase 2 — Sensor BLE (MVP-b)
- [x] T2.1 Layar Sensor: scan (filter service), pairing, simpan, lupakan, baterai sensor.
- [x] T2.2 Parser HR, CSC, CP + unit test lengkap (`04-INTEGRATIONS.md` §A.6).
- [x] T2.3 Sensor manager: auto-connect, reconnect backoff, status per sensor.
- [x] T2.4 Integrasi ke SampleMerger (prioritas sumber speed/cadence/power).
- [x] T2.5 Metrik: HR avg/max/zona, cadence avg, power 3s/10s/avg/max/NP, W/kg.
- [x] T2.6 Estimated power (label EST, tidak ke FIT).
- [x] T2.7 Kalori (urutan prioritas `05-DATA-MODEL.md` §3.10).
- [x] T2.8 Editor halaman data (pilih jumlah field & metrik).
- [x] T2.9 Peringatan sensor putus/kembali/baterai rendah.
- [x] T2.10 FIT: tambah HR, cadence, power di record/lap/session + device_info sensor.
- [x] T2.11 Grafik HR/cadence/power di detail aktivitas.
- [x] T2.12 Kontrol tombol volume (opsional, setting).
- [x] T2.13 Profil aktivitas Road/Gravel/MTB/Indoor/Commute (§4), termasuk mode Indoor tanpa GPS; pemilih profil di Pre-ride.
- [x] T2.14 Layar Keselamatan: kontak darurat (contact picker), nama pengendara, izin SMS, SMS uji (§5.1).
- [x] T2.15 `IncidentDetector` di `:core` + `AccelerometerSource` (§5.2) dengan unit test dari data rekaman/sintetis (jatuh, lubang, HP jatuh saat diam, rem mendadak).
- [x] T2.16 Alur insiden: hitung mundur layar penuh, SMS + update lokasi, retry, "Saya aman", SOS manual (§5.3).
- [x] T2.17 Backup & restore (§12): arsip `.rcbackup` streaming, opsi kata sandi (**ajukan pilihan library/parameter kripto**), pengingat backup saat app dibuka, restore atomik dengan backup darurat, validasi zip-slip/ukuran/checksum/versi skema.

**DoD:** ride uji dengan HR strap + sensor cadence (dan power meter bila ada); data muncul benar di Strava; sensor dicabut-pasang di tengah ride tanpa merusak rekaman; uji insiden (simulasi & uji fisik aman, mis. menjatuhkan HP di atas kasur sambil bergerak) memicu hitung mundur, pembatalan berfungsi, SMS uji diterima; backup dari HP A berhasil di-restore di HP B (atau setelah uninstall-install ulang) dengan data identik.

## Fase 3 — Navigasi rute (GPX)
- [x] T3.1 Import GPX (file picker + intent "Buka dengan"), parsing aman (SEC-IN-01..04: tanpa DOCTYPE/entity, batas ukuran & titik, validasi nilai), simplifikasi, simpan ke DB.
- [x] T3.2 Daftar & detail rute (statistik, profil elevasi), "balik arah".
- [x] T3.3 **Ajukan pilihan sumber tile offline** (`04-INTEGRATIONS.md` §D) ke pemilik → implementasi MapLibre + atribusi. (Dipilih: MapLibre + PMTiles, D-64.)
- [x] T3.4 Manajer peta offline (unduh/impor area, hapus, ukuran). (Impor file `.pmtiles` saja; app tanpa izin internet, D-64.)
- [x] T3.5 Halaman peta di layar ride: rute, posisi, jejak, north-up, follow mode.
- [x] T3.6 Route tracker: proyeksi ke rute, jarak tersisa, ETA, sisa tanjakan.
- [x] T3.7 Off-route & back-on-route alert (getar + suara + banner).
- [x] T3.8 Mode breadcrumb tanpa tile.
- [x] T3.9 Profil elevasi dengan penanda posisi di halaman tanjakan.
- [x] T3.10 Unit test: proyeksi titik ke polyline, rute bersilangan/berputar, off-route timing.
- [x] T3.11 `ClimbDetector` saat import + daftar tanjakan + halaman tanjakan ala ClimbPro + peringatan 500 m (§6).
- [x] T3.12 `TurnDetector` saat import + peringatan belokan 150/30 m + field "Belokan berikutnya" (§7).
- [x] T3.13 Unit test tanjakan & belokan dengan GPX referensi (belok 90°, hairpin, tikungan landai tanpa alert, noise GPS, rute tanpa elevasi).

**DoD:** ride uji mengikuti GPX ≥ 20 km dengan sengaja keluar jalur 2x; alert benar; peta tampil tanpa internet (mode pesawat + GPS); peringatan tanjakan & belokan muncul tepat waktu pada rute uji.

## Fase 4 — Latihan & analisis
- [x] T4.1 `BestEffortCalculator` (jarak tercepat, power terbaik, rekor lain) dijalankan setelah ride disimpan + layar Rekor + badge PR di ringkasan (§8).
- [x] T4.2 Segmen pribadi: buat dari aktivitas/tanjakan, `SegmentMatcher` setelah ride, layar Segmen (§9.1–9.2).
- [x] T4.3 Live segment saat ride: deteksi start, field Segmen, selisih vs PR, banner hasil (§9.3).
- [x] T4.4 `TrainingLoadCalculator` (power/HR), tabel `daily_training` inkremental, layar Latihan dengan grafik & ringkasan mingguan (§10).
- [x] T4.5 Kartu share Story/Kotak dengan opsi privasi, render offline, share via FileProvider (§11).
- [x] T4.6 Unit test: best efforts dengan data sintetis (termasuk pause di tengah), pencocokan segmen (lewat sebagian, arah terbalik, GPS noise), nilai referensi TSS/TRIMP, CTL/ATL inkremental saat ride dihapus.

**DoD:** PR dan segmen terdeteksi benar pada 3 ride uji di rute yang sama; grafik latihan konsisten setelah menambah/menghapus ride; kartu share tidak memuat awal/akhir rute saat opsi privasi aktif.

## Fase 4G — Panduan pengguna (permintaan pemilik 2026-11-13; dikerjakan setelah fitur inti selesai)
- [x] TG.1 Pilihan level saat onboarding: **pemula / menengah / profesional**, dengan keterangan lama bersepeda; level menentukan seberapa banyak penjelasan yang ditampilkan.
- [x] TG.2 Panduan "cara pakai" untuk pengguna awam: apa itu & cara import GPX, menyambungkan sensor, backup & restore, export FIT/upload Strava, peta offline, segmen, rekor, beban latihan, keselamatan.
- [x] TG.3 Petunjuk kontekstual di layar terkait (kartu/tips yang bisa ditutup) + halaman Bantuan di Setelan; tanpa internet.

**DoD:** pengguna baru (pemula) bisa menjelaskan & melakukan import GPX, sambung sensor, backup, dan export tanpa bantuan.

## Fase 5 — Strava API (opsional; hanya bila disetujui)
- [x] T5.0 Baca dokumentasi & changelog Strava terbaru; laporkan perbedaan dengan `04-INTEGRATIONS.md` §E.2.
- [x] T5.1 Layar setup: input Client ID/Secret + panduan membuat app API.
- [x] T5.2 OAuth via Custom Tabs (scope `activity:write`, `state` acak, PKCE bila didukung, redirect dicocokkan persis), token di Keystore & dikecualikan dari backup, refresh token (SEC-AUTH-*).
- [x] T5.3 Upload FIT + polling status + antrian retry + penanganan duplikat.
- [x] T5.4 Disconnect (`/oauth/revoke`), base URL dapat dikonfigurasi (migrasi `api-v3` 2027).

**DoD:** ride tersimpan otomatis ter-upload ke Strava; mode offline mengantrikan upload.

## Fase 6 — iOS (belum dijadwalkan)
Ikuti `03-ARCHITECTURE.md` §9. Memerlukan Mac + Apple Developer Program untuk instalasi permanen.
- [ ] T6.1 Jadikan `:core` modul Kotlin Multiplatform (KMP).
- [ ] T6.2 Pilih & siapkan UI iOS (SwiftUI atau Compose Multiplatform; ajukan pilihan ke pemilik).
- [ ] T6.3 Lapisan platform iOS: lokasi & rekaman di latar.
- [ ] T6.4 Sensor BLE di iOS (HR, speed/cadence, power, smart trainer).
- [ ] T6.5 Export FIT/GPX & berbagi di iOS.
- [ ] T6.6 Uji di iPhone & cara distribusi (TestFlight/App Store).

## Fase 7 — Fitur lanjutan (dipilih pemilik 2026-11-17)
- [x] T7.1 Pengumuman suara (TTS bawaan HP): tiap 1/5/10 km, lap, pengingat minum/makan (D-91).
- [x] T7.2 Kembali ke titik start (D-92).
- [x] T7.3 Pelacakan komponen sepeda (km otomatis per sepeda, pengingat servis) (D-93).
- [x] T7.4a Rencana latihan: siap pakai (Base Zona 2, Persiapan lomba, Gran Fondo 100 km) + pembuat rencana sendiri, rencana aktif & latihan hari ini di tab Latihan (D-94).
- [x] T7.4b Sesi hari ini di Beranda & persiapan ride, zona target di layar ride dengan alert getar + suara (D-94).

## Fase 9 — Trainer & widget (dipilih pemilik 2026-11-17)
- [x] T9.1 Smart trainer (BLE FTMS): data power/cadence/kecepatan + ERG terhubung rencana latihan (D-95).
- [x] T9.2 Widget layar utama: sesi hari ini, minggu ini, tombol mulai; jarak & waktu saat ride (D-96).

## Fase 8 / 10 — Rilis beta & masukan pengguna (selesai 2026-11-18)
- [x] T8.1 Pemeriksaan akhir Fase 7; T8.2 rilis awal **0.9.0** (APK sideload per ABI, GitHub Release) (D-97).
- [x] T10.1 Open source: audit secret seluruh riwayat (bersih), lisensi Apache-2.0, README publik (D-98); T10.3 bersih-bersih dokumen (D-99); T10.4–T10.5 gambar pratinjau sosial & poster di README.
- [x] T10.6 Galeri share 21 desain (stiker transparan, tiket tanggal, kartu peta), tombol Instagram/WhatsApp (D-100).
- [x] T10.7 Tombol PAUSE/LAP selalu tampil (D-101); T10.8 + T10.11 sepeda lipat & roda 16"/20", skema v10 (D-102).
- [x] T10.9 Cek pembaruan dalam aplikasi dari GitHub Releases (D-103); T10.14 kartu pembaruan didesain ulang (D-107).
- [x] T10.12 Onboarding baru: layar sambutan, langkah bisa dilewati, pengingat profil (D-105).
- [x] Rilis **0.9.1** (D-104), **0.9.2** (D-106, uji ujung-ke-ujung pembaruan), **0.9.3** (D-108). Catatan rilis: `docs/releases/`.

## Fase 11 — Analisa ride, usaha terbaik, tema & peta offline (direncanakan 2026-11-19)
Spesifikasi, keputusan K1–K5 (dijawab 2026-11-19, D-109…D-113), urutan, risiko, **anggaran performa §8**, dan DoD: **`14-PHASE11-PLAN.md`**.

**11A — Fondasi**
- [x] T11.1 Komponen grafik `RideChart` di `:ui-common` (angka sumbu, garis bantu, lapisan latar, sentuh-geser; tanpa library) (K3).
- [x] T11.2 Halaman detail menjadi bagian vertikal: Ringkasan → Hasil → Kecepatan → Elevasi → Detak jantung → Zona power → Lap → Ekspor.
- [x] T11.2a Perbaikan: library & opsi peta MapLibre dimuat di luar thread utama (StrictMode, temuan uji T11.2).
- [x] T11.2b Perbaikan: peta tidak tampil saat detail dibuka dari aplikasi dingin (regresi T11.2a).

**11B — Analisa**
- [x] T11.3 Analisa kecepatan: grafik + elevasi latar; Kec. rata-rata, Kec. maks, Waktu bergerak, Waktu total.
- [x] T11.4 Rombak elevasi: grafik dengan angka bantu; Kenaikan elevasi, Elevasi maks.
- [x] T11.5 Analisa detak jantung (bila ada sensor HR): grafik, rata-rata/maks, waktu di zona Z1–Z5; kartu ajakan bila tanpa HR.
- [x] T11.5a Perbaikan: tautan perkiraan HR max di Profil hanya tampil bila tahun lahir terisi (temuan uji T11.5).
- [x] T11.6 Perkiraan zona power Z1–Z7: % waktu & rentang watt; power meter atau estimasi (berlabel "Perkiraan"), FTP profil atau perkiraan.

**11C — Usaha terbaik**
- [x] T11.7 Bagian "Hasil" di detail: jumlah usaha terbaik & segmen, daftar segmen dengan PR, rekor baru.
- [x] T11.8 Usaha terbaik otomatis (K1): rekor durasi kecepatan/power dan "Simpan sebagai segmen" dari baris rekor (D-125).

**11D — Tema**
- [x] T11.9 Setelan → Tema: Gelap · Terang · Ikuti HP, default Gelap (K2). (D-126)
- [x] T11.10 Audit & uji tampilan terang di semua layar (ride di bawah matahari, peta, grafik, share, widget). (D-127)

**11E — Peta offline**
- [x] T11.11 Cache peta otomatis (yang pernah dilihat) dipakai saat sinyal hilang; Setelan ukuran & hapus cache. (D-128)
- [x] T11.13 Paket peta per provinsi dari data Protomaps/OSM, di-hosting di GitHub Release proyek (skrip pemilik) (K4). (D-129; skrip + DI Yogyakarta di `maps-2026-11`, 37 provinsi oleh pemilik)
- [x] T11.12 Peta wilayah otomatis: deteksi wilayah dari lokasi terakhir, tawaran unduh lewat Wi‑Fi, verifikasi, dipakai otomatis saat offline (K5). (D-130)
- [x] T11.14 Batas penyimpanan, keamanan unduhan, atribusi. (D-131)
- [x] T11.15 Redesign halaman Segmen baru: 3 langkah, peta, pintasan, −/+ 50 m, statistik bagian terpilih (permintaan pemilik, D-133).

**DoD:** lihat `14-PHASE11-PLAN.md` §9.

## Fase 12 — Detak jantung dari smartwatch (direncanakan 2026-11-19, setelah Fase 11)
Rencana, keputusan S1–S4, risiko, dan DoD: **`15-PHASE12-PLAN.md`** (D-114).

**12A — Jam tangan dengan broadcast HR standar**
- [x] T12.1 Uji kompatibilitas jam tangan sungguhan & tabel kompatibilitas. (D-123)
- [x] T12.2 Pemindaian luas "Tampilkan semua perangkat Bluetooth" (untuk jam tangan yang tidak mengiklankan layanan HR). (D-134)
- [x] T12.3 UX jam tangan: label/ikon & panduan menyalakan broadcast HR per merek. · [Feature Report](../reports/features/T12.3-watch-guide.md)

**12B — Wear OS (opsional)**
- [ ] ⏸ T12.4 Aplikasi pendamping Wear OS (butuh persetujuan dependency). Ditunda (D-139)

**12C — Jam tangan tanpa broadcast (eksperimental, Aolon milik pemilik)**
- [x] T12.5a Identifikasi protokol Aolon (go/no-go: HR real-time ≥ 1 nilai per 1–2 dtk). Lolos bersyarat (D-135)
- [x] T12.5b Implementasi sensor "Jam tangan (eksperimental)" (hanya bila T12.5a lolos). (D-136)
- [ ] ⏸ T12.5c Uji ride sungguhan ±30 menit. Ditunda sampai pemilik sehat (D-139)

**DoD:** lihat `15-PHASE12-PLAN.md` §8.

## Fase 13 — Tampilan responsif layar kecil (direncanakan 2026-11-19, setelah Fase 11 & 12)
Rencana, keputusan R1–R4, prinsip teknis, risiko, dan DoD: **`16-PHASE13-PLAN.md`** (D-124).

**13A — Fondasi & audit**
- [x] T13.1 Audit layar kecil (320 dp & 360 dp) + daftar masalah per layar. 0 P1, 4 P2, 11 P3 · [Audit](../testing/small-screen-audit.md) · [Feature Report](../reports/features/T13.1-small-screen-audit.md)
- [x] T13.2 Skrip screenshot pembanding + baseline 412/390/360 dp. Baseline 25 layar (D-142, D-144) · [Feature Report](../reports/features/T13.2-screen-compare.md)
- [x] T13.3 Fondasi: `WindowWidthClass`, token `Regular`/`Compact`, preview layar kecil. 412/390/360 dp identik (D-143) · [Feature Report](../reports/features/T13.3-size-foundation.md)

**13B — Layar prioritas**
- [x] T13.4 Layar ride & persiapan ride. · [Feature Report](../reports/features/T13.4-ride-screens.md)
- [x] T13.5 Beranda, onboarding & sambutan. · [Feature Report](../reports/features/T13.5-T13.8-audit-fixes.md)

**13C — Layar lain**
- [x] T13.6 Riwayat & detail ride. · [Feature Report](../reports/features/T13.5-T13.8-audit-fixes.md)
- [x] T13.7 Rute, sensor, latihan. · [Feature Report](../reports/features/T13.5-T13.8-audit-fixes.md)
- [x] T13.8 Setelan & sub-layar. · [Feature Report](../reports/features/T13.5-T13.8-audit-fixes.md)

## Ide lanjutan (bank ide, belum dijadwalkan)
Ide yang sudah terwujud dicentang beserta task-nya; sisanya dipindah ke fase bila dipilih pemilik.
- [x] Target zona dengan alert (T7.4b).
- [x] Pengumuman suara / TTS (T7.1).
- [x] Kembali ke titik start (T7.2).
- [x] Pelacakan komponen sepeda (T7.3).
- [x] Backup ke file (T2, backup terenkripsi).
- [x] Export file untuk platform lain (FIT/GPX, T1/T3).
- [ ] Workout/interval terstruktur langkah demi langkah (pemanasan → interval → pendinginan).
- [ ] Ghost / virtual partner (lawan ride terbaik sendiri di rute/segmen).
- [ ] Backup otomatis ke Google Drive.
- [ ] Radar belakang BLE.
- [ ] Unggah langsung ke intervals.icu dan platform lain.
