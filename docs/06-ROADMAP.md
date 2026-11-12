# 06 — Roadmap & Task Breakdown

Kerjakan **berurutan**. **Setiap task** mengikuti alur di `10-FEATURE-CHECKLIST.md` (Rencana → persetujuan → implementasi + test → verifikasi → Feature Report). Fase selesai hanya setelah lolos Phase Gate (`10-FEATURE-CHECKLIST.md`) dan acceptance criteria di `07-TEST-PLAN.md`.

| Fase | Nama | Hasil |
|---|---|---|
| 0 | Fondasi | Proyek Kotlin multi-modul berjalan, tema, DB, lint & test |
| 1 | Perekam GPS (MVP-a) | Rekam ride GPS dengan andal, jeda/lanjut otomatis, pengingat, mode baterai kritis, simpan, export FIT |
| 2 | Sensor BLE, keselamatan & backup (MVP-b) | HR, speed/cadence, power; profil aktivitas; deteksi insiden + SMS darurat; backup & restore |
| 3 | Navigasi rute | Import GPX, peta offline, off-route, info tanjakan (ClimbPro), peringatan belokan |
| 4 | Latihan & analisis | Personal record, segmen pribadi, beban latihan, kartu share |
| 5 | Strava API (opsional) | Upload otomatis bila pengguna berlangganan Strava |
| 6 | iOS | Port & uji di iPhone |
| 7+ | Peningkatan | Ide lanjutan |

Spesifikasi detail fitur tambahan: `../12-FEATURE-SPECS.md`.

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
- [x] T1.4b Jeda & lanjut otomatis lengkap (`../12-FEATURE-SPECS.md` §1): setting ambang & durasi, tahan GPS drift, indikator "JEDA OTOMATIS", prompt diam lama, event FIT.
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
- [ ] T2.11 Grafik HR/cadence/power di detail aktivitas.
- [ ] T2.12 Kontrol tombol volume (opsional, setting).
- [ ] T2.13 Profil aktivitas Road/Gravel/MTB/Indoor/Commute (§4), termasuk mode Indoor tanpa GPS; pemilih profil di Pre-ride.
- [ ] T2.14 Layar Keselamatan: kontak darurat (contact picker), nama pengendara, izin SMS, SMS uji (§5.1).
- [ ] T2.15 `IncidentDetector` di `:core` + `AccelerometerSource` (§5.2) dengan unit test dari data rekaman/sintetis (jatuh, lubang, HP jatuh saat diam, rem mendadak).
- [ ] T2.16 Alur insiden: hitung mundur layar penuh, SMS + update lokasi, retry, "Saya aman", SOS manual (§5.3).
- [ ] T2.17 Backup & restore (§12): arsip `.rcbackup` streaming, opsi kata sandi (**ajukan pilihan library/parameter kripto**), pengingat backup saat app dibuka, restore atomik dengan backup darurat, validasi zip-slip/ukuran/checksum/versi skema.

**DoD:** ride uji dengan HR strap + sensor cadence (dan power meter bila ada); data muncul benar di Strava; sensor dicabut-pasang di tengah ride tanpa merusak rekaman; uji insiden (simulasi & uji fisik aman, mis. menjatuhkan HP di atas kasur sambil bergerak) memicu hitung mundur, pembatalan berfungsi, SMS uji diterima; backup dari HP A berhasil di-restore di HP B (atau setelah uninstall-install ulang) dengan data identik.

## Fase 3 — Navigasi rute (GPX)
- [ ] T3.1 Import GPX (file picker + intent "Buka dengan"), parsing aman (SEC-IN-01..04: tanpa DOCTYPE/entity, batas ukuran & titik, validasi nilai), simplifikasi, simpan ke DB.
- [ ] T3.2 Daftar & detail rute (statistik, profil elevasi), "balik arah".
- [ ] T3.3 **Ajukan pilihan sumber tile offline** (`04-INTEGRATIONS.md` §D) ke pemilik → implementasi MapLibre + atribusi.
- [ ] T3.4 Manajer peta offline (unduh/impor area, hapus, ukuran).
- [ ] T3.5 Halaman peta di layar ride: rute, posisi, jejak, north-up, follow mode.
- [ ] T3.6 Route tracker: proyeksi ke rute, jarak tersisa, ETA, sisa tanjakan.
- [ ] T3.7 Off-route & back-on-route alert (getar + suara + banner).
- [ ] T3.8 Mode breadcrumb tanpa tile.
- [ ] T3.9 Profil elevasi dengan penanda posisi di halaman tanjakan.
- [ ] T3.10 Unit test: proyeksi titik ke polyline, rute bersilangan/berputar, off-route timing.
- [ ] T3.11 `ClimbDetector` saat import + daftar tanjakan + halaman tanjakan ala ClimbPro + peringatan 500 m (§6).
- [ ] T3.12 `TurnDetector` saat import + peringatan belokan 150/30 m + field "Belokan berikutnya" (§7).
- [ ] T3.13 Unit test tanjakan & belokan dengan GPX referensi (belok 90°, hairpin, tikungan landai tanpa alert, noise GPS, rute tanpa elevasi).

**DoD:** ride uji mengikuti GPX ≥ 20 km dengan sengaja keluar jalur 2x; alert benar; peta tampil tanpa internet (mode pesawat + GPS); peringatan tanjakan & belokan muncul tepat waktu pada rute uji.

## Fase 4 — Latihan & analisis
- [ ] T4.1 `BestEffortCalculator` (jarak tercepat, power terbaik, rekor lain) dijalankan setelah ride disimpan + layar Rekor + badge PR di ringkasan (§8).
- [ ] T4.2 Segmen pribadi: buat dari aktivitas/tanjakan, `SegmentMatcher` setelah ride, layar Segmen (§9.1–9.2).
- [ ] T4.3 Live segment saat ride: deteksi start, field Segmen, selisih vs PR, banner hasil (§9.3).
- [ ] T4.4 `TrainingLoadCalculator` (power/HR), tabel `daily_training` inkremental, layar Latihan dengan grafik & ringkasan mingguan (§10).
- [ ] T4.5 Kartu share Story/Kotak dengan opsi privasi, render offline, share via FileProvider (§11).
- [ ] T4.6 Unit test: best efforts dengan data sintetis (termasuk pause di tengah), pencocokan segmen (lewat sebagian, arah terbalik, GPS noise), nilai referensi TSS/TRIMP, CTL/ATL inkremental saat ride dihapus.

**DoD:** PR dan segmen terdeteksi benar pada 3 ride uji di rute yang sama; grafik latihan konsisten setelah menambah/menghapus ride; kartu share tidak memuat awal/akhir rute saat opsi privasi aktif.

## Fase 5 — Strava API (opsional; hanya bila disetujui)
- [ ] T5.0 Baca dokumentasi & changelog Strava terbaru; laporkan perbedaan dengan `04-INTEGRATIONS.md` §E.2.
- [ ] T5.1 Layar setup: input Client ID/Secret + panduan membuat app API.
- [ ] T5.2 OAuth via Custom Tabs (scope `activity:write`, `state` acak, PKCE bila didukung, redirect dicocokkan persis), token di Keystore & dikecualikan dari backup, refresh token (SEC-AUTH-*).
- [ ] T5.3 Upload FIT + polling status + antrian retry + penanganan duplikat.
- [ ] T5.4 Disconnect (`/oauth/revoke`), base URL dapat dikonfigurasi (migrasi `api-v3` 2027).

**DoD:** ride tersimpan otomatis ter-upload ke Strava; mode offline mengantrikan upload.

## Fase 6 — iOS
Ikuti `03-ARCHITECTURE.md` §9: jadikan `:core` modul KMP, tulis UI (SwiftUI atau Compose Multiplatform, ajukan pilihan) dan lapisan platform iOS (lokasi, BLE, background). Memerlukan Mac + Apple Developer Program untuk instalasi permanen.

## Fase 7+ — Ide lanjutan (belum dijadwalkan)
Workout/interval terstruktur, target zona dengan alert, pengumuman suara (TTS), kembali ke titik start, pelacakan komponen sepeda, ghost/virtual partner, backup ke file/Google Drive, widget layar, dukungan smart trainer (FTMS), radar belakang BLE, export ke platform lain (intervals.icu dll. via file).
