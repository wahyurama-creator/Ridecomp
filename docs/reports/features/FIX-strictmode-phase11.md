# Feature Report — fix: temuan StrictMode Fase 11 (sebelum penutup fase)

**Tanggal:** 2026-11-20
**Branch:** `fix/strictmode-findings`
**Status:** Selesai (opsi OkHttp disetujui pemilik; uji HP lulus)

## 1. Ringkasan
Semua temuan StrictMode yang tercatat selama Fase 11 ditelusuri sampai penyebab pastinya lalu diperbaiki. Penyebab sebelumnya tidak terlihat karena logcat ColorOS membuang stack `penaltyLog`. Karena itu ditambahkan listener StrictMode khusus debug (Android 9+) yang mencatat jenis dan stack lengkap dengan tag `RideComp/StrictMode`. Uji ini juga menemukan dan memperbaiki satu bug UI: peta provinsi yang filenya ada tapi versinya beda tidak bisa dihapus.

| Temuan | Penyebab pasti | Perbaikan |
|---|---|---|
| Saat aplikasi dibuka | `AndroidStravaAccountStore` memanggil `noBackupFilesDir` (disk) di konstruktor, yang dibuat Koin di thread utama | `lazy`, hanya disentuh di IO |
| Saat aplikasi dibuka | Layanan sistem realme `CabcService` menulis file di dalam panggilan Binder | Bukan kode aplikasi: dicatat terpisah (RULE-EXCEPTION, D-132) |
| "Untagged socket" cek pembaruan / Strava | `HttpsURLConnection` tanpa tag | `withTrafficTag` + `TrafficTags` (`:platform`), juga untuk unduhan peta |
| "Untagged socket" peta online | OkHttp bawaan MapLibre | OkHttp 4.12.0 dideklarasikan (versi yang sama di APK); MapLibre diberi client dengan thread pekerja ber-tag |
| Saat impor peta | `displayName` (query ContentResolver) di thread utama | `suspend` di IO |
| Bug UI (ditemukan saat uji) | File provinsi dengan versi beda hanya punya tombol unduh, sehingga tidak bisa dihapus | Tombol unduh dan hapus tampil bersama |

## 2. Perubahan per lapisan
| Lapisan | File | Keterangan |
|---|---|---|
| App (debug) | `DevTools.kt` | Listener StrictMode (Android 9+, `AndroidLogger`), Android 8 tetap `penaltyLog` |
| Platform | `AndroidStravaAccountStore`, `GitHubReleaseSource`, `HttpStravaApi`, `AndroidMapPackDownloader`, `AndroidOfflineMapStore.displayName`, `system/TrafficTags.kt` | |
| Domain | `OfflineMapRepository.displayName` jadi `suspend` | |
| Design system | `MapHttp.kt` (client OkHttp MapLibre), `MapEngine`, `RouteSnapshot` | |
| Presentation | `OfflineMapsScreen.RegionActions` | |
| Build | `libs.versions.toml` (`okhttp = 4.12.0`), `ui-common/build.gradle.kts`, `ui-common/gradle.lockfile` | Verification metadata tidak berubah |

## 3. Dependency baru
`com.squareup.okhttp3:okhttp:4.12.0` dideklarasikan di `:ui-common` atas izin pemilik. Artefak dan versinya sama dengan yang sudah ikut MapLibre di APK, jadi APK tidak bertambah library. Checksum di `verification-metadata.xml` sudah ada sebelumnya. Lockfile hanya menambah classpath compile.

## 4. Resource & siklus hidup
Executor thread pekerja OkHttp milik MapLibre: thread idle mati setelah 60 detik (perilaku bawaan OkHttp). Tidak ada resource lain.

## 5. Checklist kode
- [x] Tanpa magic number (tag `TrafficTags`, batas OkHttp sebagai konstanta); RULE-EXCEPTION dijelaskan.
- [x] StrictMode tidak dilemahkan: `detectAll()` tetap aktif, dan pengecualian hanya untuk stack `com.android.server.*`.

## 6. Hasil testing
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`: ✅ BUILD SUCCESSFUL, exit 0.
- Uji perangkat (realme RMX3830, `adb logcat -s RideComp/StrictMode`):
  - Buka aplikasi dari dingin: sebelum perbaikan ada 2 temuan aplikasi (Strava store) dan 3 dari sistem; sesudahnya **0 temuan aplikasi** (3 sistem dicatat sebagai D).
  - Detail ride + geser peta ke area baru: tile dimuat lewat thread "RideComp map http", **0 temuan** (sebelumnya `UntaggedSocketViolation` dari OkHttp).
  - Setelan → Pembaruan → Cek sekarang: "Anda sudah memakai versi terbaru", **0 temuan**; rilis peta tidak dianggap pembaruan.
  - Impor file `.pmtiles` lewat pemilih file (subagent Sonnet): **0 temuan**. Bug tombol hapus ditemukan di sini lalu diperbaiki; file uji dihapus lewat tombol baru, folder peta kosong, dan HP kembali seperti semula.

## 7. Keamanan
- Socket aplikasi kini ber-tag. Client MapLibre memakai pengaturan TLS dan trust anchor bawaan OkHttp/sistem; konfigurasi jaringan (HTTPS saja) tidak berubah.
- Android Lint Security: 0 temuan. Secret scan: hook pre-commit bersih.

## 8. Penyimpangan dari spesifikasi / RULE-EXCEPTION
- RULE-EXCEPTION di `DevTools.report`: pelanggaran dari layanan sistem lewat Binder dicatat terpisah (D-132), sejalan dengan kebocoran OEM ColorOS (D-87).

## 9. Risiko & bug yang diketahui
- Versi OkHttp yang dideklarasikan harus tetap sama dengan yang dibawa MapLibre saat MapLibre diperbarui (ada catatan di version catalog).
- Android 8 (tanpa listener) tetap memakai `penaltyLog`, jadi stack-nya bisa terpotong di logcat OEM.

## 10. Cara menguji
`adb logcat -c`, lalu buka aplikasi, detail ride (peta), cek pembaruan, dan impor peta. `adb logcat -d -s RideComp/StrictMode:W` harus kosong.
