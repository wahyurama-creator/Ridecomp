# Log Keputusan (Decision Log)

Keputusan yang diambil selama implementasi. Yang bertanda **(agent)** diambil oleh Claude Code atas delegasi pemilik proyek (2026-11-12): "kalau butuh keputusan, ambil keputusan terbaik". Semua bisa ditinjau dan dibalik.

| # | Tanggal | Task | Keputusan | Alternatif | Alasan | Oleh |
|---|---|---|---|---|---|---|
| D-01 | 2026-11-12 | T0.1 | Package/applicationId `com.ridecomp.app`; modul `com.ridecomp.*` | `com.ways.ridecomp` | Sesuai dokumen | Pemilik |
| D-02 | 2026-11-12 | T0.8 | Daftar dependency Fase 0 disetujui (lihat `gradle/libs.versions.toml`) | — | — | Pemilik |
| D-03 | 2026-11-12 | T0.7 | `gradlePluginPortal()` hanya di `pluginManagement`, dibatasi `content{}` ke grup plugin build (RULE-EXCEPTION SUP-01) | Tolak Plugin Portal | Plugin ktlint/Dependency-Check/license-report hanya ada di Portal | Pemilik |
| D-04 | 2026-11-12 | T0.11 | Penjaga arsitektur = pemeriksaan dependensi modul di Gradle (saat konfigurasi) + aturan `ForbiddenImport` detekt per lapisan | Konsist | Tanpa dependency tambahan | Pemilik |
| D-05 | 2026-11-12 | Semua | Lanjut otomatis ke fase berikutnya; uji perangkat/lapangan Phase Gate dilakukan pemilik belakangan | Berhenti tiap fase | Permintaan pemilik | Pemilik |
| D-06 | 2026-11-12 | T0.2 | Modul tambahan `:ui-common` untuk design system bersama | Design system di `:app` | `:feature-*` tidak boleh bergantung ke `:app`; ARCH-02 mengizinkan `:ui-common` | (agent) |
| D-07 | 2026-11-12 | T0.7 | Daemon Gradle memakai **JDK 21 LTS** (`gradle/gradle-daemon-jvm.properties`), bukan JDK 25 | detekt 2.0 alpha; hapus detekt | detekt 1.23.8 crash di JDK 25; JDK 21 didukung AGP 9.5 & Kotlin 2.4 dan diunduh otomatis (foojay) | (agent) |
| D-08 | 2026-11-12 | T0.7 | Konfigurasi detekt dipaksa memakai Kotlin compiler 2.0.21 (versi bawaan detekt 1.23.8) | — | detekt berjalan di classpath terpisah; versi Kotlin proyek (2.4.21) tidak kompatibel dengannya | (agent) |
| D-09 | 2026-11-12 | T0.7 | Lint `ObsoleteLintCustomCheck` dinonaktifkan | Biarkan (build gagal) | Lint check bawaan library AndroidX (lifecycle) tidak kompatibel dengan versi lint; lint melewatinya sendiri, ini bukan temuan pada kode kita | (agent) |
| D-10 | 2026-11-12 | T0.7 | Level SDK/JVM (`compileSdk`, `targetSdk`, `minSdk`, `jvmTarget`) disimpan di `libs.versions.toml` dan diterapkan sekali dari root build | Diulang per modul | Permintaan pemilik: tanpa magic number, satu sumber | (agent) |
