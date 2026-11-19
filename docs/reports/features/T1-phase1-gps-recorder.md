# Feature Report — Fase 1: Perekam GPS / MVP-a (T1.1–T1.19)

**Tanggal:** 2026-11-13
**Branch:** satu branch per task, semua di-merge ke `main`. Ada 1 branch perbaikan: `fix/core-coverage-config-tests`.
**Status:** Menunggu review pemilik. **Uji perangkat & lapangan belum dilakukan** (D-05).

## 1. Ringkasan
RideComp kini bisa merekam ride GPS dari awal sampai akhir:
- **Mulai ride:** onboarding izin + profil → pre-ride check (GPS & baterai) → layar ride.
- **Selama ride:** 3 halaman data (utama, tanjakan, lap); jeda/lanjut otomatis tahan GPS drift; lap manual dan otomatis; pengingat minum/makan; mode baterai 20/10/5% dengan FIT darurat; mode layar selalu nyala / redup / mati; notifikasi foreground berisi data dan tombol jeda.
- **Selesai:** ringkasan (beri nama) → simpan → detail (grafik kecepatan & elevasi, lap) → **export FIT/GPX** (share sheet atau simpan ke Download) + panduan upload manual ke Strava.
- **Keandalan:** ride yang terputus karena crash/kill ditawarkan untuk dilanjutkan, disimpan, atau dibuang. Bila sistem menghidupkan ulang service, rekaman pulih otomatis.

Mode debug juga punya **GPS simulasi** (menu Developer) untuk mencoba tanpa harus keluar rumah.

## 2. Perubahan per lapisan
| Lapisan | Isi utama |
|---|---|
| `:core` | `config` (semua ambang di satu tempat), `model`, `engine.geo` (GeoMath, GpsFilter, DistanceTracker), `engine.metrics` (ExponentialSmoother, ElevationTracker), `engine.recording` (AutoPauseDetector, RideStateMachine, LapTracker, ReminderScheduler, BatteryModePolicy, RideMetricsAccumulator, RideRecorder, TrackPointBuffer, RecordingSession), `engine.export` (FitEncoder, FitProfile, FitActivityWriter, GpxWriter), `repository` + `source` (interface), `usecase` (activity, chart, profile/settings, export/share, ride control), `util.UnitFormatter`, fake di `testFixtures` |
| `:data` | Mapper eksplisit, `ActivityRepositoryImpl`, `ProfileRepositoryImpl`, `SettingsRepositoryImpl`, `SettingsDao.observeAll` |
| `:platform` | `RecordingService` (+ `WakeLockHolder`, `RecordingNotification`, `ServiceIntents`, `RideStateHolder`, `ServiceRideController`, `RecordingDependencies`), `AndroidLocationSource`, `AndroidPressureSource`, `AndroidBatteryLevelSource`, `AlertPlayer`, `UuidIdGenerator`, `AndroidRideNameProvider`, `AndroidExportFileStore`, `AndroidExportedFileHandler`; manifest izin + service |
| `:ui-common` | `LineChart`, `DataGrid`, `ChoiceChips`, `SwitchRow`, `NumberField`, `StatRow`, `ConfirmDialog` |
| `:feature-ride` | Beranda (+ recovery), Pre-ride, Ride (+ pause, banner, mode layar), Ringkasan; `RideMetric`/`DefaultDataPages` |
| `:feature-history` | Riwayat (lazy load), Detail (grafik, lap, export/share/simpan/hapus), panduan Strava |
| `:feature-settings` | Onboarding, Profil, Pengaturan ride/layar/baterai, Bantuan baterai per merek HP |
| `:app` | Navigasi lengkap (route di `AppRoutes`), `AppViewModel` (onboarding sebagai start), modul Koin, FileProvider, DevTools debug (GPS simulasi) |

## 3. Dependency baru
**Tidak ada library baru.** Library yang sudah disetujui dipakai di modul tambahan:
- `activity-compose` di 3 modul feature;
- `lifecycle-viewmodel`, `kotlinx-coroutines-test`, dan testFixtures `:core` untuk test.

Lockfile dan `verification-metadata.xml` diperbarui secara sadar. Satu artefak baru masuk metadata: `androidx.core 1.18.0`, sebagai dependency transitif.

## 4. Resource & siklus hidup
Tabel 09 §1 sudah diperbarui.
| Resource | Diakuisisi | Dilepas | Cara verifikasi |
|---|---|---|---|
| Update GPS | Pre-ride (selama layar terlihat, `WhileSubscribed`) & start ride | FINISH (pipeline berhenti), SAVE/DISCARD, `onDestroy`, `awaitClose` → `removeUpdates` | `FakeLocationSource.activeCollectors` (test); `dumpsys location` (**pending perangkat**) |
| Barometer | Start ride (bila ada) | FINISH, mode Kritis, `awaitClose` | Review; `dumpsys sensorservice` (pending) |
| Wakelock parsial | Start/resume ride, timeout 10 menit, diperbarui per tick | FINISH, SAVE/DISCARD, `onDestroy` | Review; `dumpsys power` (pending) |
| Foreground service | Setiap perintah (`startForeground` lebih dulu) | SAVE/DISCARD, tidak ada ride | Review; `dumpsys activity services` (pending) |
| `keepScreenOn` + kecerahan | Layar ride terlihat | Keluar layar ride (`onDispose`) | Review |
| Getar | Event | Sekali jalan; `cancel()` di `onDestroy` | Review |
| File export & FIT darurat | Export / baterai ≤ 5% | Hapus aktivitas menghapus FIT; FIT darurat dihapus saat export normal | Test use case |

## 5. Checklist kode
- **Arsitektur:**
  - semua logika ada di `:core` (RideRecorder dll.); service hanya wiring (ARCH-08);
  - feature hanya bergantung pada `:core`/`:ui-common`, ditegakkan otomatis (ARCH-01/02);
  - mapper eksplisit (ARCH-05);
  - UDF lewat `UdfViewModel` (ARCH-06), ViewModel tanpa Context/NavController (ARCH-07);
  - use case satu aksi; constructor besar dikelompokkan (KT-05).
- **Penamaan, gaya, magic number:**
  - ktlint + detekt bersih; compiler warning = error;
  - konstanta di `core.config`, `*Config`, `*Names`/`*Keys`, `FitProfile`, `AppRoutes`, `RideCompDimens`;
  - teks di `strings.xml` (en + id); string debug ditandai non-translatable.
- **Error & konkurensi:**
  - `Outcome` untuk error yang diharapkan (perintah tidak valid, export gagal, profil tidak valid);
  - `runCatchingCancellable`; DB gagal → dicatat + dicoba ulang, rekaman jalan terus (STB-08);
  - session dikunci ke satu dispatcher + Mutex (CONC-01);
  - timeout: menunggu GPS fix punya opsi "mulai tanpa menunggu", sedangkan wakelock selalu bertimeout.
- **Memori & baterai:**
  - buffer berbatas (TrackPointBuffer maks 600, RingBuffer/RollingAverage);
  - GPS 5 detik saat jeda lama;
  - tanpa AlarmManager/WorkManager;
  - notifikasi diperbarui hanya bila teks berubah;
  - grafik di-downsample di `Dispatchers.Default`.
- **Data:** DB lewat suspend Room; tanpa destructive migration; baca track per chunk; stream `use {}`.
- **UI:** Route/Screen + preview gelap/terang; field ride me-recompose sendiri lewat `derivedStateOf` (UI-04); target sentuh ≥ 56/72 dp.

## 6. Hasil testing
- **Unit test:** **244 test** (38 kelas), 0 gagal. Yang utama:
  - `RideRecorderTest`: simulasi ride lengkap dengan GPS palsu 1 Hz;
  - `RecordingSessionTest`: termasuk DB rusak lalu pulih tanpa kehilangan titik;
  - `AutoPauseDetectorTest`: lampu merah, drift, tanjakan 4–5 km/jam, sensor roda;
  - `ElevationTrackerTest`: noise ±3 m, offset barometer;
  - `FitWriterTest` + `ExportUseCasesTest`: decoder FIT memvalidasi CRC header/file, urutan pesan, satuan, nilai invalid, power estimasi tidak ditulis;
  - `BatteryModePolicyTest`, `ReminderSchedulerTest`, `LapTrackerTest`, `MappersTest`;
  - test ViewModel tiap fitur;
  - `AppModulesTest`: semua ViewModel ter-resolve dari graph Koin.
- **Instrumented test:** sudah ditulis dan ter-compile, belum dijalankan (butuh perangkat).
- **`./gradlew ktlintCheck detekt lint test koverVerify assembleDebug assembleRelease`:** ✅.
- **Coverage `:core`:** 96,8%.
- **APK release per-ABI:** ±1,68 MB, tanpa simulator/StrictMode/LeakCanary (sudah dicek di dex).
- **Bug yang ditemukan test selama Fase 1 dan sudah diperbaiki** (semua dengan regression test):
  1. jitter GPS terhitung sebagai jarak;
  2. ±30 m dan 5–12 detik waktu bergerak salah di setiap berhenti (D-21);
  3. HR istirahat yang tidak masuk akal lolos validasi;
  4. hook secret-scan meloloskan password;
  5. coverage gate terlewati karena exit code tertutup pipe (dicatat terbuka di commit `fix(core)`).
- **Uji perangkat / lapangan (L1, L3, L4, L5, L9, L11, L12, L14–L16, R1, R2, S5, S7): BELUM.** Template pencatatan ada di `docs/testing/field-tests.md`.

## 7. Keamanan
- **Checklist manual §D:**
  1. Input luar: fix GPS divalidasi (rentang, NaN, akurasi, lonjakan); input profil divalidasi; nama ride di-trim, dibatasi panjangnya, dan di-escape di GPX.
  2. Komponen: service `exported=false`; FileProvider `exported=false` + `grantUriPermissions`; tidak ada receiver ter-export.
  3. Data sensitif: lokasi hanya di DB/file internal; crash log disamarkan; log debug saja.
  4. File: internal `filesDir/exports`, nama dibuat app (SEC-IN-04), share lewat FileProvider dengan path terbatas (SEC-PLT-04).
  5. PendingIntent: eksplisit + `FLAG_IMMUTABLE`.
  6. Jaringan: tidak ada. Tautan Strava/dontkillmyapp dibuka di browser.
  7. SQL: hanya `@Query` konstanta/parameter.
  8. Rahasia: tidak ada.
  9. Izin baru: lokasi, foreground service (+location), notifikasi, wakelock, getar, pengecualian optimasi baterai (`tools:ignore BatteryLife` + alasan). Sesuai 03-ARCH §2.
  10. Dependency: tidak ada yang baru.
  11. Pesan error ke UI generik, tanpa stack trace.
  12. Fitur debug (StrictMode, LeakCanary, GPS simulasi, menu Developer) hanya di source set debug; terverifikasi tidak ada di dex release.
  13. SMS/kontak/kartu share: N/A.
- **Merged manifest release:** izin sesuai daftar di atas. `READ/WRITE_EXTERNAL_STORAGE` hanya muncul di debug (dari LeakCanary).
- **Scan CVE (OSV):** 0 temuan di kode yang masuk APK; 14 High/Critical di tool build saja (`security/phase1-osv-scan.txt`).
- **Lisensi:** semua Apache-2.0.

## 8. Penyimpangan / keputusan
- **D-20:** dependensi service lewat interface.
- **D-21:** koreksi gerakan tentatif saat auto-pause/resume.
- **D-22:** encoder FIT sendiri.
- **D-23:** Baseline Profile ditunda.
- **D-05:** Phase Gate tanpa uji perangkat.
- **Navigasi:** route berbasis string (Navigation Compose type-safe butuh plugin serialization yang belum disetujui).

Lengkapnya di `DECISIONS.md`.

## 9. Risiko & bug yang diketahui
- **Belum diuji di HP sungguhan.** Risiko terbesar ada pada perilaku OEM (Xiaomi/Oppo/Vivo membunuh service), waktu GPS di HP murah, dan kompatibilitas file FIT dengan Strava. Validasi FIT terhadap FitCSVTool/Strava belum dilakukan.
- **Metrik sensor** (HR, cadence, power) tampil `--` sampai Fase 2.
- **Mode layar "Mati":** pengingat tidak menyalakan layar (hanya getar + notifikasi).
- **Simulator GPS** berjalan real-time 1x; replay 10x belum ada.
- **Pengingat berbasis jarak** belum ada (akan ikut profil aktivitas, T2.13).

## 10. Cara menguji (untuk pemilik proyek)
1. Jalankan `./gradlew installDebug`, buka app. Lalui onboarding (izinkan lokasi, notifikasi, berjalan di latar), lalu isi profil.
2. **Coba di dalam rumah:** Pengaturan → Developer → aktifkan *Simulated GPS*. Lalu Beranda → MULAI RIDE → MULAI. Amati angka berjalan, jeda otomatis tiap ±2 menit, getar, notifikasi. Coba LAP, JEDA → SELESAI & SIMPAN → beri nama → SIMPAN → di detail ketuk EKSPOR FIT → pilih Drive/WhatsApp.
3. **Uji recovery (L4):** saat merekam, `adb shell am force-stop com.ridecomp.app`, lalu buka app lagi. Banner "Ride belum tersimpan" harus muncul.
4. **Uji lapangan:** matikan simulasi, gowes ≥ 30 menit (L1/L5/L14). Upload FIT ke strava.com (L9). Catat semua di `docs/testing/field-tests.md`.
5. **Setelah ride disimpan**, jalankan perintah `dumpsys` di `docs/testing/field-tests.md`. Pastikan tidak ada GPS, wakelock, maupun service yang tersisa (L11).
