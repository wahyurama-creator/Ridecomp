# Feature Report — Fase 5: Strava API (T5.0–T5.4)

Tanggal: 2026-11-17 · Branch: `docs/T5.0-strava-docs`, `feature/T5.2-strava-connect` (T5.1+T5.2), `feature/T5.3-strava-upload`, `feature/T5.4-strava-disconnect` · Keputusan: D-79, D-80

## 1. Ringkasan
- **T5.0 Dokumentasi:** temuan dokumentasi Strava terbaru dicatat di `04-INTEGRATIONS.md` §E.2: URL otorisasi mobile, tanpa PKCE, rotasi refresh token, polling ≤ 1/dtk, revoke, base URL `api-v3` mulai 4 Jan 2027.
- **T5.1 Layar setup:** Setelan → Strava. Ada panduan 3 langkah (buka strava.com/settings/api, isi formulir dengan Callback Domain `localhost`, salin Client ID/Secret). Kedua kolom divalidasi formatnya, dan Secret hilang dari layar begitu tersimpan.
- **T5.2 Hubungkan:** satu tombol membuka app Strava atau browser. Setelah "Authorize", pengguna otomatis kembali ke RideComp. Isinya:
  - `state` acak sekali pakai
  - redirect yang dicocokkan persis
  - pemeriksaan izin `activity:write`
  - token di berkas terenkripsi Keystore yang tidak ikut backup
  - refresh token yang aman terhadap rotasi
- **T5.3 Upload:**
  - Detail ride punya baris "Upload ke Strava" yang menampilkan status: hubungkan dulu → mengirim → Strava memproses → "Lihat di Strava".
  - Antrian tersimpan (tabel `strava_uploads`, skema v7) dan dijalankan saat app terbuka. Gangguan jaringan dicoba ulang otomatis.
  - Duplikat langsung ditautkan ke aktivitas yang sudah ada.
  - Opsi upload otomatis setelah ride disimpan (default mati).
- **T5.4 Putuskan:** revoke di Strava lalu token dihapus dari HP, termasuk saat offline (dengan pesan agar akses juga dicabut di strava.com). Base URL berpindah ke `api-v3.strava.com` sesuai tanggal.

## 2. Perubahan per lapisan
- **`:core`:**
  - `StravaConfig`
  - `StravaModels` (akun, status tanpa secret, upload, error)
  - `StravaSources` (`StravaAccountStore`, `StravaApi`)
  - `StravaUploadRepository`
  - Use case: Observe/Save/Start/Complete/GetAccessToken/Disconnect, `AuthorizedStravaApi`, Queue/Process/QueueNewRides/SetAutoUpload/ObserveUpload, `StravaUploadRequests`
- **`:platform`:** `KeystoreCipher` (AES-256-GCM), `AndroidStravaAccountStore` (berkas di `noBackupFilesDir`, tulis-lalu-rename), `HttpStravaApi` (`HttpsURLConnection`, multipart di-stream), `StravaJson` (org.json, validasi).
- **`:data`:** `StravaUploadEntity`/`Dao`/`Mapper`/`RepositoryImpl`, migrasi 6→7, skema `7.json`.
- **`:feature-settings`:** `StravaScreen`/`StravaViewModel`, entri Setelan dengan status "Terhubung", toggle upload otomatis, tombol Putuskan.
- **`:feature-history`:** baris Strava di detail aktivitas (`ActivityStravaUseCases`).
- **`:app`:**
  - izin INTERNET kembali aktif
  - `MainActivity` jadi `singleTask` dengan intent-filter redirect BROWSABLE
  - `IncomingStravaAuth`
  - rute `strava`
  - antrian upload di `AppViewModel` (`StravaQueue`)
  - DI

## 3. Dependency baru
Tidak ada (DEP-02). OAuth memakai `ACTION_VIEW`, HTTP memakai `HttpsURLConnection`, JSON memakai `org.json` bawaan Android, dan kripto memakai Android Keystore.

## 4. Resource & siklus hidup
- **Jaringan:** koneksi dibuka per request dan selalu `disconnect()` di `finally`. Timeout 15 dtk (connect) dan 30 dtk (read).
- **Antrian upload:** hanya berjalan di `viewModelScope` `AppViewModel` selama app terbuka (BAT-05), satu run pada satu waktu (mutex), dan tidak dibatalkan di tengah upload. Saat tidak merekam tidak ada GPS/BLE/wakelock/service baru.
- **Memori:** file FIT di-stream (`setChunkedStreamingMode`) dan balasan dibatasi 64 KB (MEM-01).

## 5. Checklist kode
- **Arsitektur:** logika Strava di `:core`. Platform hanya I/O, dan feature hanya bergantung pada `:core`/`:ui-common`. Redirect diterima di `:app` lalu diteruskan ke layar lewat slot.
- **Konstanta:** `StravaConfig`, `DbNames.TABLE_STRAVA_UPLOADS`, dan string en/id.
- **Kotlin:** tanpa `!!`. Error ditangani dengan `Outcome`. `runCatchingCancellable` di loop antrian. Waktu dari `Clock` yang diinjeksi.
- **Data:** migrasi eksplisit v6→v7 dengan test JVM (SQL = skema ekspor) dan test perangkat (FK cascade). Tidak ada destructive migration.

## 6. Hasil testing
- **Unit test:** **498 test**, 0 gagal (Fase 4: 461).
- **Coverage `:core`:** 94,3%.
- **Test baru:**
  - `StravaUseCasesTest`: status tanpa secret, validasi app, URL otorisasi, `state` sekali pakai, redirect palsu tidak membatalkan otorisasi asli, scope, refresh & rotasi, revoke, base URL 2027.
  - `StravaUploadUseCasesTest`: upload → polling → DONE, polling lanjut di run berikutnya, duplikat, ditolak, offline menghentikan run, token ditolak → refresh sekali, akun dicabut, ride hilang, upload otomatis.
  - `StravaJsonTest`, `StravaViewModelTest`, migrasi 6→7, baris Strava di detail.
- **`./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`:** ✅. `assembleRelease` ✅.
- **APK release per-ABI:** 13,4 MB arm64 / 10,5 MB armeabi-v7a (target < 20 MB).
- **Uji perangkat (realme, T5.6):** layar Setelan → Strava, validasi Client ID/Secret, Secret tersamar, tidak ada tawaran simpan ke password manager (D-81), redirect palsu tanpa efek, host lain tidak membuka app, baris "Upload ke Strava" di detail membuka layar Strava saat belum terhubung.
- **Belum:**
  - Uji alur nyata dengan akun Strava: membutuhkan Client ID/Secret milik pemilik, yang dimasukkan sendiri di HP.
  - Instrumented test migrasi 6→7 belum dijalankan.

## 7. Keamanan
- **Checklist manual §D:**
  1. Input luar: redirect dicocokkan persis dan nilainya dibatasi; balasan JSON divalidasi.
  2. Komponen ter-export: tetap hanya launcher. Filter BROWSABLE baru dibatasi skema/host/path dan dijaga `state`.
  3. PendingIntent: tidak ada yang baru.
  4. Penyimpanan: token/secret di Keystore, `noBackupFilesDir`.
  5. Log: tidak ada token/secret/balasan, hanya kode status.
  6. Jaringan: HTTPS, trust sistem, timeout, tanpa redirect, domain terdokumentasi.
  7. WebView/TrustManager: tidak ada.
  8. Izin: INTERNET aktif kembali (disetujui dalam Fase 5).
  9. Dependency: tidak ada yang baru.
  10. Scope minimal: `activity:write`.
  11. Disconnect: revoke + hapus.
  12. Data terkirim: hanya file FIT ride yang dipilih, atau semua ride baru bila upload otomatis diaktifkan pengguna.
  13. Secret scan pre-commit: lolos.
- **Merged manifest release:** INTERNET 1. Komponen ter-export: launcher dan `ProfileInstallReceiver` (izin `DUMP`).
- **Scan CVE (OSV, `security/phase5-osv-scan.txt`):** 642 koordinat, **0 temuan di kode yang masuk APK**. 14 High/Critical hanya di tool build, sama dengan Fase 4.
- **Threat model §E:** baris Token & secret, Alur OAuth, serta baris baru Upload Strava & jaringan.
- **Belum:** MobSF dan Dependency-Check NVD (menunggu pemilik, D-16).

## 8. Penyimpangan / keputusan
- **D-79:** Custom Tabs diganti `ACTION_VIEW`, tanpa PKCE (tidak didukung), Keystore + berkas sendiri, HTTP tanpa library.
- **D-80:** antrian Room tanpa WorkManager, upload otomatis default mati.

## 9. Risiko & bug yang diketahui
- **Belum diuji dengan akun Strava sungguhan** (lihat §6).
- **Path base URL `api-v3`:** diasumsikan `https://api-v3.strava.com/uploads`, yaitu pengganti `/api/v3`. Perlu dicek ulang saat Strava merilisnya (Jan 2027).
- **Upload hanya saat app terbuka:** ride yang tertunda karena offline dikirim saat app dibuka berikutnya.
- ~~Daftar Riwayat belum menampilkan tanda "di Strava"~~ — selesai di T5.5 (label "Di Strava").
- **Skema `ridecomp://`** bisa didaftarkan app lain. Code yang dicegat tidak berguna tanpa Client Secret, tetapi otorisasi pengguna bisa gagal.

## 10. Cara menguji (untuk pemilik proyek)
1. Buka strava.com/settings/api, buat app (Callback Domain **`localhost`**), lalu catat Client ID & Client Secret.
2. RideComp → Setelan → **Strava**. Isi kedua kolom lalu Simpan.
3. Ketuk **Hubungkan ke Strava** → app Strava/browser terbuka → biarkan "Upload your activities" tercentang → **Authorize**. RideComp terbuka lagi dengan pesan "Terhubung".
4. Riwayat → buka ride → **Upload ke Strava**. Tunggu sampai muncul "Lihat di Strava", lalu ketuk untuk membuka aktivitasnya.
5. Ketuk Upload lagi pada ride yang sama: ride tidak dikirim dua kali.
6. Aktifkan **Upload ride otomatis**, rekam ride singkat, lalu simpan: ride terkirim sendiri.
7. Mode pesawat → Upload: muncul "Menunggu internet". Matikan mode pesawat dan buka ulang app: ride terkirim.
8. **Putuskan Strava**: status kembali "Belum terhubung", dan RideComp hilang dari strava.com/settings/apps.

## 11. Pembaruan T5.7 — Strava satu ketukan (D-82)
- Pemilik menilai formulir Client ID/Secret terlalu ribet. Sekarang pengguna cukup mengetuk **Hubungkan dengan Strava** lalu **Authorize**.
- Client Secret dipindah ke **server token** (`server/strava-token-server`, Cloudflare Worker tanpa dependency) yang hanya meneruskan tukar code, refresh, dan revoke. Server tidak menyimpan dan tidak me-log token. Test Node: 4/4 lolos (`node --test server/strava-token-server/`).
- App membaca Client ID dan alamat server dari properti Gradle. Tanpa keduanya, layar Strava menampilkan "belum tersedia" dan baris upload disembunyikan. Keduanya sudah dicek di HP, begitu juga tampilan tombol Hubungkan dengan konfigurasi contoh.
- **Langkah pemilik:**
  1. Buat app Strava.
  2. Deploy Worker dan isi 2 secret.
  3. Isi 2 properti Gradle (lihat README server).
  4. Agar pengguna lain bisa terhubung, ajukan kenaikan kapasitas atlet ke Strava. Ini butuh tombol "Connect with Strava" dan logo "Powered by Strava" resmi.
