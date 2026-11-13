# Serah-terima pekerjaan (untuk melanjutkan di device / sesi lain)

**Terakhir diperbarui:** 2026-11-13

## Status
- **Fase 0, 1, 2:** selesai dan di-merge ke `main`. Laporan ada di `docs/reports/T0-…`, `T1-…`, `T2-phase2-sensors-safety-backup.md`.
- **Fase 3 (navigasi rute):**
  - Selesai dan di-merge:
    - T3.1–T3.2: import GPX (parser milik sendiri, D-43), daftar dan detail rute.
    - T3.6–T3.10: route tracker (D-46), off-route, halaman navigasi breadcrumb, profil elevasi (D-47).
    - T3.11–T3.13: deteksi tanjakan & belokan, alert getar, banner, field "Belokan berikutnya", panel tanjakan (profil pita grade), daftar tanjakan di detail rute (D-49, D-50); perbaikan hasil review: rute lama dideteksi saat dibaca, elevasi bolong diinterpolasi, balik arah di luar main thread (D-52). Diuji di realme RMX3830 dengan GPS simulasi + GPX uji: banner belokan & tanjakan, panel tanjakan, pola getar, service berhenti setelah simpan, LeakCanary 0 leak.
    - Fix T3.1: picker Import menerima `.gpx` bertipe `application/octet-stream` (D-51; ditemukan saat uji perangkat).
  - T3.3–T3.5: **MapLibre + PMTiles** (D-64), branch `feature/T3.3-T3.5-maplibre`. Impor `.pmtiles` (header divalidasi) ke internal storage, manajer Peta offline (#26), peta di detail rute (#7) dan halaman navigasi ride (#12, follow mode; tanpa peta / mode hemat → breadcrumb). MapLibre `android-sdk-opengl` 13.7.1, tanpa izin INTERNET (dihapus dari merged manifest), `setConnected(false)`. APK release per-ABI: 13,6 MB arm64 / 10,6 MB armv7. File uji: extract kecil Yogyakarta dari build harian Protomaps (bbox 110.33,−7.81 – 110.41,−7.75, zoom 0–15; tidak di-commit).
- **StrictMode startup: diperbaiki** (D-53, branch `fix/T3-strictmode-startup`). Di realme hanya tersisa pelanggaran dari `com.android.server.cabc.CabcService` (vendor). Untuk melihat stack di ROM yang tidak mencetaknya: pasang sementara `penaltyListener` yang me-log `Violation` (jangan di-commit).
- **Implementasi desain canvas (13-UI-SCREENS) — prioritas pemilik sebelum peta offline:**
  - Selesai & di-merge: U0 design system (D-54), U1a layar ride (D-55), U1b Home/Pre-ride/Cari GPS (D-56), U1c Ringkasan/Riwayat/Detail (D-57), U1d onboarding & profil (D-58), U1e Setelan & editor halaman data (D-59), U2a Sensor (D-60), U2b Keselamatan & insiden (D-61), U2c Backup (D-62), U3a tanjakan/belokan/halaman peta (D-63). Diuji di realme RMX3830 kecuali onboarding (butuh hapus data app) dan hitung mundur insiden (akan mengirim SMS nyata).
  - #7/#12/#26 dengan peta bertile: selesai bersama T3.3–T3.5. Layar Fase 4 (#17, #20, #21, kartu PR, tab Rekor/Segmen/Latihan) dikerjakan bersama fiturnya.
- **Akhir Fase 3:** laporan `docs/reports/T3-phase3-route-navigation.md` (menunggu review pemilik); OSV `security/phase3-osv-scan.txt` (0 temuan di APK; 14 High/Critical hanya tool build, sama dengan Fase 2); `org.json` (test) diperbarui ke 20260814; threat model §E ditambah GPX & PMTiles. `pwsh` tidak ada di Mac: scan dijalankan dengan port Python dari logika `osv-scan.ps1` (tidak di-commit).
- **Menunggu keputusan pemilik:** layar "Lisensi open source" (BSD-2 MapLibre + Apache NOTICE) sebelum rilis; MobSF & NVD API key; uji lapangan DoD Fase 3 (L7, peta di mode pesawat).
- **Fase 4 selesai** (T4.0–T4.6, D-65…D-73): lisensi open source, rekor, segmen + live segmen, beban latihan, kartu share. Laporan `docs/reports/T4-phase4-training-analysis.md` (menunggu review pemilik); OSV `security/phase4-osv-scan.txt` (0 temuan di APK). Skema DB v6.
- **Berikutnya: Fase 4G** (permintaan pemilik 2026-11-13): panduan pemakaian untuk pengguna awam + pilihan level pesepeda (pemula/menengah/profesional, lama bersepeda) saat onboarding. Fase 5 (Strava API) opsional dan hanya dengan persetujuan eksplisit.

## Yang perlu ditinjau pemilik
- D-41: enkripsi backup per segmen di atas AES-GCM.
- D-32: nilai FIT `sub_sport`, cek dengan FitCSVTool.
- **Uji lapangan belum dilakukan** (hanya uji perangkat singkat dengan GPS simulasi, lihat atas). GPX uji simulasi: segi-7 r=527 m di pusat loop simulator (−7.7829, 110.3671) dengan tanjakan 6 % di km 1,0–1,9.

## Konvensi kerja (penting untuk agent berikutnya)
- Baca `CLAUDE.md` dan `docs/` terlebih dulu. Keputusan tercatat di `docs/reports/DECISIONS.md` (D-01…D-73).
- Satu branch per task (`feature/<TaskID>-<slug>`), Conventional Commit + ID task, merge `--no-ff` ke `main`.
- Verifikasi sebelum commit: `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`.
- Daemon Gradle memakai JDK 21. Bila dependency berubah: `--write-locks --write-verification-metadata sha256`.
- Tanpa magic number/string; komponen reusable di `:ui-common`, helper di `core.util`.
- ViewModel layar baru harus ditambahkan ke `AppModulesTest`.
