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
  - **Menunggu keputusan pemilik:** T3.3–T3.5 (MapLibre + sumber tile offline, DEP-02). Opsi:
    - (a) file region PMTiles/MBTiles (saran);
    - (b) penyedia tile yang mengizinkan offline;
    - (c) tunda, pakai breadcrumb.
- **StrictMode startup: diperbaiki** (D-53, branch `fix/T3-strictmode-startup`). Di realme hanya tersisa pelanggaran dari `com.android.server.cabc.CabcService` (vendor). Untuk melihat stack di ROM yang tidak mencetaknya: pasang sementara `penaltyListener` yang me-log `Violation` (jangan di-commit).
- Setelah itu: laporan Fase 3, scan OSV (`scripts/osv-scan.ps1`), lalu Fase 4.

## Yang perlu ditinjau pemilik
- D-41: enkripsi backup per segmen di atas AES-GCM.
- D-32: nilai FIT `sub_sport`, cek dengan FitCSVTool.
- **Uji lapangan belum dilakukan** (hanya uji perangkat singkat dengan GPS simulasi, lihat atas). GPX uji simulasi: segi-7 r=527 m di pusat loop simulator (−7.7829, 110.3671) dengan tanjakan 6 % di km 1,0–1,9.

## Konvensi kerja (penting untuk agent berikutnya)
- Baca `CLAUDE.md` dan `docs/` terlebih dulu. Keputusan tercatat di `docs/reports/DECISIONS.md` (D-01…D-53).
- Satu branch per task (`feature/<TaskID>-<slug>`), Conventional Commit + ID task, merge `--no-ff` ke `main`.
- Verifikasi sebelum commit: `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`.
- Daemon Gradle memakai JDK 21. Bila dependency berubah: `--write-locks --write-verification-metadata sha256`.
- Tanpa magic number/string; komponen reusable di `:ui-common`, helper di `core.util`.
- ViewModel layar baru harus ditambahkan ke `AppModulesTest`.
