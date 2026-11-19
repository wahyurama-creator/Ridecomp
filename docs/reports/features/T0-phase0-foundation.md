# Feature Report — Fase 0: Fondasi (T0.1–T0.13)

**Tanggal:** 2026-11-13
**Branch:** satu branch per task, semua sudah di-merge ke `main` (`feature/T0.1-T0.2-project-structure`, `feature/T0.7-T0.11-quality-tooling`, `feature/T0.10-core-abstractions`, `feature/T0.3-design-system`, `feature/T0.6-i18n-koin`, `feature/T0.4-navigation-skeleton`, `feature/T0.5-room-schema`, `feature/T0.9-reliability-foundation`, `feature/T0.13-security-foundation`, `feature/T0.12-phase0-report`)
**Status:** Menunggu review pemilik

## 1. Ringkasan
Proyek template satu modul diubah menjadi proyek multi-modul Clean Architecture (`com.ridecomp.app`, minSdk 26). Isinya:
- design system gelap/terang dengan komponen reusable;
- navigasi 5 tab dengan layar placeholder dalam bahasa Inggris dan Indonesia;
- database Room v1;
- Koin DI;
- fondasi reliabilitas (LeakCanary, StrictMode, crash log lokal);
- tooling kualitas (ktlint, detekt, Lint, Kover, penjaga arsitektur);
- fondasi keamanan supply chain (locking, verifikasi SHA-256, wrapper resmi, scan CVE, lisensi, hook secret scan).

Yang terlihat oleh pengguna: app terbuka di layar Beranda (tombol **MULAI RIDE**), dengan tab Riwayat, Rute, Sensor, dan Pengaturan. Tab selain Beranda baru menampilkan state kosong.

## 2. Perubahan per lapisan
| Lapisan | File/kelas | Keterangan |
|---|---|---|
| Domain (`:core`) | `source/Clock`, `DispatcherProvider`, `Logger`; `util/RingBuffer`, `RollingAverage`, `Outcome` (+ ekstensi), `runCatchingCancellable`; `testFixtures`: `FakeClock`, `TestDispatcherProvider`, `RecordingLogger` | Kotlin/JVM murni, explicit API, coverage 97,5% |
| Data (`:data`) | `RideCompDatabase` v1, entity `profile/settings/activities/track_points/events/laps`, 6 DAO, `DbNames`, registri migrasi | Skema diekspor ke `data/schemas/1.json` |
| Platform (`:platform`) | `AndroidClock`, `StandardDispatcherProvider`, `AndroidLogger`, `CrashLogStore`, `CrashLogHandler` | |
| Design system (`:ui-common`) | Tema (`RideCompColors`, `RideCompDimens`, `RideCompTypography`, `RideCompTheme`), `DataField`, `BigButton`, `AlertBanner`, `MessageState/EmptyState/ErrorState`, `ScreenScaffold`, `ListRow`, `ModifierExt`, `UdfViewModel`, `ObserveEffects`, `@ThemePreviews`, ikon vektor | Modul baru (D-06) |
| Presentation (`:feature-*`) | `HomeScreen` (ride), `HistoryScreen`, `RoutesScreen`, `SensorsScreen`, `SettingsScreen` (Route/Screen + preview) | `:feature-training` masih kosong (Fase 4) |
| App (`:app`) | `RideCompApplication`, `MainActivity`, `RideCompApp` (bottom bar), `RideCompNavHost`, `TopLevelDestination`, DI `platformModule`/`dataModule`, `DevTools` (debug/release) | |
| Build | Root `build.gradle.kts` (konfigurasi Android bersama, ktlint/detekt/lint, penjaga arsitektur, locking, Dependency-Check, license report), `config/detekt/*`, `gradle/verification-metadata.xml`, lockfile, `scripts/` | |

## 3. Dependency baru
Daftar dependency Fase 0 disetujui pemilik pada 2026-11-12 (D-02). Versinya ada di `gradle/libs.versions.toml`. Tambahan yang masih dalam keluarga/versi yang sama dengan yang disetujui:
- `lifecycle-viewmodel` (versi lifecycle 2.11.0);
- `kotlin-test-junit` (versi Kotlin);
- detekt memakai Kotlin compiler 2.0.21 di classpath terpisahnya (D-08).

Tidak ada library pihak ketiga baru di luar daftar. DataStore disetujui tetapi belum dipakai (D-11).

## 4. Resource & siklus hidup
| Resource | Diakuisisi | Dilepas | Diverifikasi dengan |
|---|---|---|---|
| File crash log | Saat uncaught exception | Rotasi 10 file; `clear()` | `CrashLogStoreTest` |
| Database Room | Akses pertama (Koin `single`) | Hidup selama proses | Tabel 09 §1 diperbarui |

Belum ada GPS, BLE, wakelock, maupun service di Fase 0.

## 5. Checklist kode
- **Arsitektur:** ARCH-01/02 ditegakkan otomatis. Terbukti: menambahkan `:data` ke `:feature-ride` langsung menggagalkan build. Mapper dan UseCase belum ada (Fase 1).
- **Penamaan & gaya:** dicek ktlint dan detekt (aturan dipetakan ke KT-*/NAME-*). Tidak ada `!!`. Tidak ada magic number di kode produksi: dimensi, warna, nama tabel, nama route, dan level SDK semuanya di satu tempat. `TODO` selalu ber-ID task (`TODO(T1.3)` dst.).
- **KDoc:** API publik `:core` terdokumentasi.
- **String & warna:** dari resource/tema. Teks di preview memakai literal sampel (hanya untuk preview).
- **Error & konkurensi:** `runCatchingCancellable`, `Outcome`, dispatcher diinjeksi. Tidak ada `GlobalScope`/`runBlocking`.
- **Memori:** koleksi berbatas (`RingBuffer`, `RollingAverage`). Tidak ada Context di objek berumur panjang selain `applicationContext` milik Room.
- **Data:** tanpa `allowMainThreadQueries` dan tanpa destructive migration. Query daftar memakai paging, track point dibaca per chunk.
- **UI:** Route/Screen terpisah. Setiap layar punya preview gelap & terang. Target sentuh ≥ 56/72 dp. Ikon tab memakai label sebagai deskripsi. String tersedia dalam `en` & `id`.

## 6. Hasil testing
- **Unit test:** 37, semua lulus.
  - `RingBufferTest` (9), `RollingAverageTest` (6), `OutcomeTest` (6), `CoroutinesExtTest` (3);
  - `CrashLogStoreTest` (5), `UdfViewModelTest` (3);
  - `TopLevelDestinationTest` (4), `AppModulesTest` (1).
- **Instrumented test (sudah ditulis dan ter-compile, belum dijalankan):** `DataFieldTest` (3, Compose UI), `RideCompDatabaseTest` (6, DAO), `RideCompMigrationTest` (1). Belum ada HP/emulator yang tersambung.
- **`./gradlew ktlintCheck detekt lint test koverVerify`:** ✅ BUILD SUCCESSFUL (lint `warningsAsErrors`, Security fatal).
- **`assembleRelease`:** ✅. APK per-ABI tanpa tanda tangan: arm64-v8a 1,17 MB, armeabi-v7a 1,17 MB (target < 20 MB).
- **Coverage `:core`:** 97,5% (batas 80%).
- **LeakCanary / StrictMode:** terpasang di debug. Terverifikasi tidak ada di dex release. **Belum diamati di perangkat.**
- **`dumpsys`:** N/A (belum ada resource berat).
- **Uji perangkat:** **belum dilakukan** (tidak ada perangkat). DoD Fase 0 "app terpasang di HP fisik" masih menunggu pemilik (D-05).

## 7. Keamanan
- **Checklist manual §D:**
  1. Input luar: tidak ada.
  2. Komponen manifest: hanya launcher yang ter-export. `ProfileInstallReceiver` dari AndroidX ter-export tetapi dilindungi `android.permission.DUMP`.
  3. Data sensitif: skema DB menyimpan lokasi/HR di internal storage; tidak di-log.
  4. File: crash log di `filesDir`, koordinat disamarkan.
  5. PendingIntent: tidak ada.
  6. Jaringan: tidak ada. Network Security Config HTTPS-only.
  7. SQL: hanya `@Query` berparameter/konstanta.
  8. Rahasia: tidak ada. Signing diambil dari `keystore.properties` yang di-gitignore.
  9. Izin baru: tidak ada.
  10. Dependency baru: lihat §3.
  11. Kebocoran info: belum ada pesan error ke UI.
  12. Fitur debug di release: StrictMode/LeakCanary hanya di source set debug, sudah dicek di dex.
  13. SMS/kontak/kartu share: N/A.
- **Android Lint Security:** 0 temuan (issue Security dijadikan fatal).
- **Secret scan:** hook pre-commit aktif (`core.hooksPath`) dan diuji dengan secret palsu. **gitleaks belum terpasang**; saat ini hook memakai pola dasar (D-19).
- **Supply chain:**
  - wrapper Gradle diganti dengan jar resmi (checksum template tidak cocok, D-18);
  - locking dan verifikasi SHA-256 aktif, dan terbukti menolak checksum palsu;
  - **scan CVE OSV:** 0 temuan pada kode yang masuk APK; 27 temuan di tool build, 14 di antaranya High/Critical (jackson, bouncycastle, guava, freemarker, jdom2, jose4j, jsoup — dari plugin lint/detekt/dependency-check). Lihat `security/phase0-osv-scan.txt`, D-17;
  - Dependency-Check butuh NVD API key (D-16);
  - lisensi: semua Apache-2.0.
- **Merged manifest release (SUP-08):** izin hanya `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature, AndroidX). Cleartext dimatikan. `allowBackup=true` dengan aturan yang mengecualikan semua data dari cloud backup.
- **Risiko sisa:**
  - CVE di tool build (bukan di APK);
  - MobSF belum dijalankan (perlu instalasi lokal);
  - threat model §E belum berubah karena belum ada fitur data.

## 8. Penyimpangan dari spesifikasi / RULE-EXCEPTION
- `gradlePluginPortal()` dibatasi `content{}` (RULE-EXCEPTION SUP-01, disetujui, D-03).
- Modul tambahan `:ui-common` (D-06). Daemon Gradle di JDK 21 (D-07). `ObsoleteLintCustomCheck` dimatikan (D-09).
- Setting disimpan di Room, bukan DataStore (D-11). Skema `activities` sudah lengkap sejak v1 (D-12).
- Fase berikutnya dikerjakan tanpa menunggu uji perangkat Phase Gate (permintaan pemilik, D-05).
- Semua keputusan ada di `docs/reports/DECISIONS.md`.

## 9. Risiko & bug yang diketahui
- Instrumented test dan uji perangkat belum dijalankan.
- Dependency-Check belum bisa jalan tanpa NVD API key.
- gitleaks dan MobSF belum terpasang.
- Kompatibilitas detekt 1.23.8 dengan Kotlin 2.4 bergantung pada pemaksaan Kotlin 2.0.21 di classpath detekt. Kalau detekt 2.0 stabil, sebaiknya migrasi.

## 10. Cara menguji (untuk pemilik proyek)
1. Sambungkan HP (USB debugging), lalu jalankan `./gradlew installDebug`. Pastikan app terbuka di Beranda dan kelima tab bisa dipindah-pindah. Ganti bahasa sistem ke Indonesia, lalu pastikan teks berubah.
2. Jalankan `./gradlew connectedDebugAndroidTest` (DAO, migrasi, `DataField`), lalu tempel hasilnya.
3. Debug build: buka-tutup tab ±30 kali. Pastikan tidak ada notifikasi LeakCanary dan logcat bersih dari `StrictMode`.
4. Opsional: pasang gitleaks (`winget install gitleaks`). Daftar NVD API key gratis, set `NVD_API_KEY`, lalu jalankan `./gradlew dependencyCheckAggregate`.
5. Opsional: jalankan MobSF atas `app/build/outputs/apk/release/*.apk`.
