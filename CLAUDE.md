# CLAUDE.md — Aturan Kerja untuk Claude Code

Proyek: **RideComp**, aplikasi cycling computer untuk HP Android low-end (native Kotlin + Jetpack Compose). Spesifikasi lengkap ada di `docs` (fitur tambahan detail di `../docs`). **Baca semua dokumen di `../docs` sebelum mengerjakan apa pun.** Aturan di `../docs`, `../docs`, `../docs`, dan `../docs` bersifat WAJIB.

## Prinsip kerja (WAJIB)

1. **Minta persetujuan sebelum bertindak.** Setiap task dimulai dengan **Rencana** (`docs` tahap ①). Tunggu persetujuan pemilik proyek sebelum menulis kode.
2. **Jika ada yang ambigu, berikan pilihan.** Jangan menebak. Sajikan 2–3 opsi beserta kelebihan/kekurangannya, lalu tunggu keputusan.
3. **Satu fase per sesi, satu task per branch.** Ikuti urutan di `docs`. Jangan mengerjakan fitur dari fase berikutnya.
4. **Jangan menambah dependency tanpa izin** (DEP-02). Kelola lewat Gradle version catalog.
5. **Spesifikasi adalah acuan.** Penyimpangan dari `docs` atau pelanggaran aturan harus dijelaskan, disetujui, ditandai `// RULE-EXCEPTION: <alasan>`, dan dokumen diperbarui.
6. **Jujur soal status.** Jangan menyatakan sesuatu "berhasil/lulus" tanpa menjalankan perintah verifikasinya dan menampilkan hasilnya.

## Alur setiap task (ringkas; detail di `docs`)

```
① Rencana → (persetujuan) → ② Implementasi + test → ③ Verifikasi (lint, test, LeakCanary, StrictMode, dumpsys, perangkat) → ④ Feature Report → (diterima) → selesai
```
Feature Report memakai `docs`, disimpan di `../docs`.

## Aturan inti (ringkasan; daftar lengkap di `docs`)

**Arsitektur:** Clean Architecture + UDF. `:core` (domain, Kotlin murni, tanpa `android.*`) ← `:data`/`:platform` (implementasi) ; `:feature-*` (presentation) hanya bergantung pada `:core`. Repository/source = interface di `:core`. Satu UseCase = satu aksi. Logika bisnis tidak di ViewModel/Composable/Service.

**Penamaan:** `camelCase` variabel & fungsi · `PascalCase` class & Composable · `SCREAMING_SNAKE_CASE` konstanta & enum entry · `snake_case` resource · package huruf kecil · boolean `is/has/can/should` · sufiks satuan (`distanceM`, `speedMps`, `durationMs`) · sufiks peran (`…UseCase`, `…Repository`, `…RepositoryImpl`, `…ViewModel`, `…UiState`). Kode dalam bahasa Inggris.

**Kotlin:** tidak ada `!!`, tidak ada magic number, `val` & koleksi immutable, `when` exhaustive tanpa `else` untuk sealed, tidak ada catch kosong, tidak menelan `CancellationException`, waktu dari `Clock` yang diinjeksi.

**Coroutines/Compose:** tidak ada `GlobalScope`/`runBlocking`; dispatcher diinjeksi; callback Android lewat `callbackFlow` + `awaitClose { unregister }`; UI collect dengan `collectAsStateWithLifecycle()`; Screen stateless + Preview; satu data field = satu sumber state.

## Reliabilitas (ringkasan; detail di `docs`)

- **Setiap resource berat (GPS, BLE GATT, scan, wakelock, foreground service, sensor, MapView, listener) punya pemilik, titik akuisisi, dan titik pelepasan pasti**, terdaftar di tabel §1 dokumen 09.
- **Saat tidak merekam: tidak ada GPS, BLE, wakelock, maupun foreground service aktif.**
- Wakelock selalu dengan timeout dan dilepas di `finally`. BLE: `disconnect()` + `close()`. Scan selalu bertimeout.
- Koleksi di memori berbatas (ring buffer); track di-flush ke DB tiap 5 detik.
- LeakCanary (debug) dan StrictMode (debug) aktif; temuan = bug yang wajib diperbaiki.
- Perekaman di foreground service; recovery setelah crash/kill; kehilangan sensor/izin tidak menghentikan rekaman atau membuat crash.

## Keamanan (ringkasan; detail di `docs`)

- **Library:** hanya `google()`/`mavenCentral()`; versi dikunci (tanpa `+`); dependency locking & verification metadata aktif; scan CVE (High/Critical = blocker); evaluasi + review merged manifest sebelum menambah dependency; secret scan sebelum commit.
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
gitleaks protect --staged                           # sebelum commit
./gradlew dependencyCheckAnalyze                    # bila dependency berubah & setiap akhir fase
```
(Nama task/tool disesuaikan dengan pilihan yang disetujui di Fase 0.)
Plus verifikasi manual sesuai `docs` tahap ③.

## Yang TIDAK boleh dilakukan

- Mengimplementasikan upload Strava API sebelum Fase 5 dan tanpa persetujuan eksplisit.
- Menulis estimated power ke field `power` di file FIT.
- Memakai tile server yang melarang bulk download untuk peta offline.
- Menyimpan token/secret di repo, log, atau source code (termasuk Client Secret Strava).
- Menambah repository Maven selain google/mavenCentral, memakai versi dinamis, atau menonaktifkan dependency verification tanpa persetujuan.
- Membuat komponen ter-export, custom TrustManager, cleartext HTTP, atau WebView tanpa persetujuan.
- Memakai `fallbackToDestructiveMigration` atau menghapus data ride pengguna tanpa konfirmasi.
- Menandai task selesai sebelum seluruh checklist dan Feature Report terisi.
