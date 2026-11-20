# CLAUDE.md — Aturan Kerja untuk Claude Code

Proyek: **RideComp**, aplikasi cycling computer untuk HP Android low-end (native Kotlin + Jetpack Compose). **Mulai dari `docs/STATUS.md`** (status terkini, task berikutnya, setup) dan **`docs/README.md`** (peta semua dokumen per folder: `product/`, `architecture/`, `planning/`, `rules/`, `testing/`, `releases/`, `reports/`). Spesifikasi inti `docs/product/01-PRD.md` … `docs/testing/07-TEST-PLAN.md`, fitur tambahan `docs/product/12-FEATURE-SPECS.md`, rencana Fase 11 `docs/planning/14-PHASE11-PLAN.md`, rencana Fase 12 (smartwatch) `docs/planning/15-PHASE12-PLAN.md`, rencana Fase 13 (layar kecil) `docs/planning/16-PHASE13-PLAN.md`. **Baca semua dokumen di `docs/` sebelum mengerjakan apa pun.** Aturan di `docs/rules/08-CODING-RULES.md`, `docs/rules/09-RELIABILITY-RULES.md`, `docs/rules/10-FEATURE-CHECKLIST.md`, dan `docs/rules/11-SECURITY-RULES.md` bersifat WAJIB. Keputusan yang sudah diambil tercatat di `docs/reports/DECISIONS.md`.

## Prinsip kerja (WAJIB)

1. **Minta persetujuan sebelum bertindak.** Setiap task dimulai dengan **Rencana** (`docs/rules/10-FEATURE-CHECKLIST.md` tahap ①). Tunggu persetujuan pemilik proyek sebelum menulis kode. (Pemilik dapat mendelegasikan keputusan; keputusan agent dicatat di `docs/reports/DECISIONS.md`.)
2. **Jika ada yang ambigu, berikan pilihan.** Jangan menebak. Sajikan 2–3 opsi beserta kelebihan/kekurangannya, lalu tunggu keputusan.
3. **Satu fase per sesi, satu task per branch.** Ikuti urutan di `docs/planning/06-ROADMAP.md`. Jangan mengerjakan fitur dari fase berikutnya.
4. **Jangan menambah dependency tanpa izin** (DEP-02). Kelola lewat Gradle version catalog.
5. **Spesifikasi adalah acuan.** Penyimpangan dari `docs/` atau pelanggaran aturan harus dijelaskan, disetujui, ditandai `// RULE-EXCEPTION: <alasan>`, dan dokumen diperbarui.
6. **Jujur soal status.** Jangan menyatakan sesuatu "berhasil/lulus" tanpa menjalankan perintah verifikasinya dan menampilkan hasilnya.
7. **Tanpa magic number/string.** Konstanta di satu tempat per lapisan (`com.ridecomp.core.config`, `*Names`/`*Keys`, token `:ui-common`, `strings.xml`). Komponen & helper dibuat reusable (`:ui-common`, `core.util`, `*Ext.kt`).
8. **Commit setiap task** (Conventional Commits + ID task), di branch `feature/<TaskID>-<slug>`, lalu merge ke `main`.

## Alur setiap task (ringkas; detail di `docs/rules/10-FEATURE-CHECKLIST.md`)

```
① Rencana → (persetujuan) → ② Implementasi + test → ③ Verifikasi (lint, test, LeakCanary, StrictMode, dumpsys, perangkat) → ④ Feature Report → (diterima) → selesai
```
Feature Report memakai `docs/templates/FEATURE_REPORT.md`, disimpan di `docs/reports/features/`. Status terkini & task berikutnya: `docs/STATUS.md`.

## Aturan inti (ringkasan; daftar lengkap di `docs/rules/08-CODING-RULES.md`)

**Arsitektur:** Clean Architecture + UDF. `:core` (domain, Kotlin murni, tanpa `android.*`) ← `:data`/`:platform` (implementasi) ; `:feature-*` (presentation) hanya bergantung pada `:core` dan `:ui-common` (design system). Repository/source = interface di `:core`. Satu UseCase = satu aksi. Logika bisnis tidak di ViewModel/Composable/Service. ViewModel layar turunan `UdfViewModel` (`:ui-common`).

**Penamaan:** `camelCase` variabel & fungsi · `PascalCase` class & Composable · `SCREAMING_SNAKE_CASE` konstanta & enum entry · `snake_case` resource · package huruf kecil · boolean `is/has/can/should` · sufiks satuan (`distanceM`, `speedMps`, `durationMs`) · sufiks peran (`…UseCase`, `…Repository`, `…RepositoryImpl`, `…ViewModel`, `…UiState`). Kode dalam bahasa Inggris.

**Kotlin:** tidak ada `!!`, tidak ada magic number, `val` & koleksi immutable, `when` exhaustive tanpa `else` untuk sealed, tidak ada catch kosong, tidak menelan `CancellationException`, waktu dari `Clock` yang diinjeksi.

**Coroutines/Compose:** tidak ada `GlobalScope`/`runBlocking`; dispatcher diinjeksi; callback Android lewat `callbackFlow` + `awaitClose { unregister }`; UI collect dengan `collectAsStateWithLifecycle()`; Screen stateless + Preview (`@ThemePreviews`); satu data field = satu sumber state.

## Reliabilitas (ringkasan; detail di `docs/rules/09-RELIABILITY-RULES.md`)

- **Setiap resource berat (GPS, BLE GATT, scan, wakelock, foreground service, sensor, MapView, listener) punya pemilik, titik akuisisi, dan titik pelepasan pasti**, terdaftar di tabel §1 dokumen 09.
- **Saat tidak merekam: tidak ada GPS, BLE, wakelock, maupun foreground service aktif.**
- Wakelock selalu dengan timeout dan dilepas di `finally`. BLE: `disconnect()` + `close()`. Scan selalu bertimeout.
- Koleksi di memori berbatas (`RingBuffer`/`RollingAverage`); track di-flush ke DB tiap 5 detik.
- LeakCanary (debug) dan StrictMode (debug) aktif; temuan = bug yang wajib diperbaiki.
- Perekaman di foreground service; recovery setelah crash/kill; kehilangan sensor/izin tidak menghentikan rekaman atau membuat crash.

## Keamanan (ringkasan; detail di `docs/rules/11-SECURITY-RULES.md`)

- **Library:** hanya `google()`/`mavenCentral()` (Plugin Portal hanya untuk plugin build, dibatasi `content{}`); versi dikunci (tanpa `+`); dependency locking & verification metadata aktif; scan CVE (High/Critical = blocker); evaluasi + review merged manifest sebelum menambah dependency; secret scan sebelum commit (`git config core.hooksPath scripts/git-hooks`).
- **Kode:** semua input luar (GPX, intent, BLE, jaringan) divalidasi; XML tanpa DOCTYPE/entity; komponen `exported=false` kecuali terdokumentasi; PendingIntent `FLAG_IMMUTABLE`; share via FileProvider; data hanya di internal storage; rahasia di Keystore & dikecualikan dari backup; HTTPS saja; tidak ada data lokasi/token di log release.
- **Setiap task:** jawab checklist review keamanan manual (§D, 13 poin). **Setiap akhir fase:** scan CVE, lisensi, update dependency, MobSF atas APK release, perbarui threat model.

## Batasan performa (HP low-end: RAM 2–3 GB)

- Tidak ada animasi dekoratif, blur, bayangan berat, atau gradien di layar ride.
- Update UI & notifikasi ≤ 1 Hz. Kalkulasi di `Dispatchers.Default`, I/O di `Dispatchers.IO`.
- Uji performa selalu dengan build release (R8 aktif). Target APK per-ABI < 20 MB (tanpa data peta).
- Satuan internal SI (meter, detik, m/s, watt, bpm, rpm); konversi hanya di lapisan tampilan.

## Perintah verifikasi standar (sebelum task dianggap selesai)

```
./gradlew ktlintCheck detekt lint test koverVerify   # setiap task (Lint Security = error)
scripts/git-hooks/pre-commit (otomatis saat commit)  # secret scan; gitleaks bila terpasang
./gradlew dependencyCheckAggregate                  # bila dependency berubah & setiap akhir fase (NVD_API_KEY disarankan)
./gradlew generateLicenseReport                     # akhir fase
```
Daemon Gradle berjalan di JDK 21 (`gradle/gradle-daemon-jvm.properties`, lihat D-07).
Plus verifikasi manual sesuai `docs/rules/10-FEATURE-CHECKLIST.md` tahap ③. Setiap task baru dicatat sebagai `- [ ]` sebelum dikerjakan; setelah selesai **centang `- [x]`** di `docs/planning/06-ROADMAP.md` (perbarui kolom Status fase), di rencana fasenya, dan di **`docs/STATUS.md`** (task terakhir & berikutnya).

## Yang TIDAK boleh dilakukan

- Mengimplementasikan upload Strava API sebelum Fase 5 dan tanpa persetujuan eksplisit.
- Menulis estimated power ke field `power` di file FIT.
- Memakai tile server yang melarang bulk download untuk peta offline.
- Menyimpan token/secret di repo, log, atau source code (termasuk Client Secret Strava).
- Menambah repository Maven selain google/mavenCentral, memakai versi dinamis, atau menonaktifkan dependency verification tanpa persetujuan.
- Membuat komponen ter-export, custom TrustManager, cleartext HTTP, atau WebView tanpa persetujuan.
- Memakai `fallbackToDestructiveMigration` atau menghapus data ride pengguna tanpa konfirmasi.
- Menandai task selesai sebelum seluruh checklist dan Feature Report terisi.
