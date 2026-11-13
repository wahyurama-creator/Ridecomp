# Serah-terima pekerjaan (untuk melanjutkan di device / sesi lain)

**Terakhir diperbarui:** 2026-11-13

## Status
- **Fase 0, 1, 2:** selesai dan di-merge ke `main`. Laporan ada di `docs/reports/T0-…`, `T1-…`, `T2-phase2-sensors-safety-backup.md`.
- **Fase 3 (navigasi rute):**
  - Selesai dan di-merge:
    - T3.1–T3.2: import GPX (parser milik sendiri, D-43), daftar dan detail rute.
    - T3.6–T3.10: route tracker (D-46), off-route, halaman navigasi breadcrumb, profil elevasi (D-47).
  - **Dalam pengerjaan** di branch `feature/T3.11-T3.13-climbs-turns` (commit WIP, belum di-merge):
    - Selesai: `ClimbDetector`, `TurnDetector`, dan `ClimbTurnTracker` (alert belokan 150/30 m, tanjakan 500 m), beserta test; disimpan di DB (`route_climbs`, `route_turns`) saat import/balik arah; diteruskan ke `RideContext`/`RouteProgress`.
    - Belum:
      1. `RecordingService` memuat climbs/turns (`deps.routeRepository.getClimbs/getTurns`) ke `RideSetup`, lalu menggetarkan `RouteProgress.alert`.
      2. UI: banner belokan/tanjakan, field "Belokan berikutnya", info "Tanjakan 2 dari 4", daftar tanjakan di detail rute.
      3. Update test `RouteRepository`/ViewModel bila perlu, verifikasi penuh, D-48 (aturan gabung tanjakan) ke `DECISIONS.md`, centang T3.11–T3.13, merge.
  - **Menunggu keputusan pemilik:** T3.3–T3.5 (MapLibre + sumber tile offline, DEP-02). Opsi:
    - (a) file region PMTiles/MBTiles (saran);
    - (b) penyedia tile yang mengizinkan offline;
    - (c) tunda, pakai breadcrumb.
- Setelah itu: laporan Fase 3, scan OSV (`scripts/osv-scan.ps1`), lalu Fase 4.

## Yang perlu ditinjau pemilik
- D-41: enkripsi backup per segmen di atas AES-GCM.
- D-32: nilai FIT `sub_sport`, cek dengan FitCSVTool.
- **Semua uji perangkat/lapangan belum dilakukan.**

## Konvensi kerja (penting untuk agent berikutnya)
- Baca `CLAUDE.md` dan `docs/` terlebih dulu. Keputusan tercatat di `docs/reports/DECISIONS.md` (D-01…D-47).
- Satu branch per task (`feature/<TaskID>-<slug>`), Conventional Commit + ID task, merge `--no-ff` ke `main`.
- Verifikasi sebelum commit: `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`.
- Daemon Gradle memakai JDK 21. Bila dependency berubah: `--write-locks --write-verification-metadata sha256`.
- Tanpa magic number/string; komponen reusable di `:ui-common`, helper di `core.util`.
- ViewModel layar baru harus ditambahkan ke `AppModulesTest`.
