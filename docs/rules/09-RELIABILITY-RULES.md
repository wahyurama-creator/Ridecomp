# 09 — Reliability Rules: Memory, Battery, Resource, Stability (WAJIB)

Aplikasi ini berjalan berjam-jam di HP low-end. Kebocoran kecil (memori, wakelock, GPS yang lupa dimatikan) akan menjadi masalah besar. Setiap aturan di sini punya **cara verifikasi**; verifikasi dicatat di Feature Report (`10-FEATURE-CHECKLIST.md`).

> Catatan jujur: tidak ada proses yang menjamin perangkat lunak 100% bebas bug. Aturan, test otomatis, alat deteksi (LeakCanary, StrictMode), dan uji lapangan di bawah ini dirancang agar bug **tertangkap sedini mungkin** dan tidak lolos ke ride sungguhan.

---

## 1. Tabel siklus hidup resource (sumber kebenaran)

Setiap resource berat **harus** memiliki satu pemilik, titik akuisisi, dan titik pelepasan yang pasti.

| Resource | Pemilik | Diakuisisi saat | WAJIB dilepas saat | API pelepasan | Verifikasi |
|---|---|---|---|---|---|
| Update lokasi GPS | `AndroidLocationSource` (dipakai `RecordingService` dan pre-ride lewat `ObserveGpsStatusUseCase` + `WhileSubscribed`) | Pre-ride (selama layar terlihat) / start ride | Ride selesai/dibuang, service destroy, `awaitClose` | `LocationManager.removeUpdates()` | `adb shell dumpsys location` tidak menampilkan request app setelah ride selesai |
| GPS saat ride Indoor | `RecordingService` | — (tidak diakuisisi: profil `gps_enabled = false`) | — | — | `dumpsys location` tanpa request app saat ride Indoor |
| Akselerometer (deteksi insiden) | `RecordingService` via `AndroidAccelerometerSource` | Ride direkam **dan** deteksi insiden aktif | Pause/selesai/ride berakhir, fitur dimatikan, service destroy | `unregisterListener` di `awaitClose` | Tidak ada listener sensor saat idle (`dumpsys sensorservice`) |
| Koneksi BLE (GATT) | `AndroidSensorConnector` + `GattClient` (dikoleksi `RecordingService`) | Ride dimulai/dipulihkan (sensor auto-connect) | Ride selesai (layar ringkasan), simpan/buang, service destroy (job dibatalkan → `finally`) | `disconnect()` lalu **`close()`** | Sensor kembali advertising (terlihat di scan app lain); log koneksi |
| Scan BLE | `AndroidBleScanner` (dikoleksi `SensorsViewModel`) | Tombol Pindai | Timeout 15 dtk, tombol Hentikan, layar ON_STOP, ViewModel dibersihkan | `stopScan()` | Log + scan berhenti dalam batas waktu |
| Mode olahraga jam GloryFit (T12.5b) | `AndroidSensorConnector` (sensor `isGloryFitWatch`) | Tersambung saat ride: tulis `FD 11 02 01`; detik-an: jam olahraga `FD 44` + HR ditahan ≤ 30 dtk | Ride selesai/sambungan putus: `FD 00 02 01` (NonCancellable, ≤ 2 dtk) lalu GATT `close()` | `stopWorkout` + `close()` | Logcat `BluetoothGatt: close()`; jam berhenti mengukur |
| Koneksi GATT pemeriksaan (T12.2) | `AndroidSensorProbe` + `GattClient` (dipanggil `SensorsViewModel`) | Tombol **Periksa** di Pindai luas | Setelah discover services, gagal, timeout, atau dibatalkan (`finally`) | **`close()`** | Logcat `BluetoothGatt: close()` + `GATT_Disconnect` |
| Wakelock parsial | `RecordingService` (`WakeLockHolder`) | Status recording | Status idle/finished, service destroy (di `finally`) | `release()` (cek `isHeld`) | `adb shell dumpsys power` → tidak ada wakelock milik app saat idle |
| Foreground service | `RecordingService` | Start ride | Ride disimpan/dibuang | `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()` | Notifikasi hilang; `adb shell dumpsys activity services` |
| Sensor barometer | `AndroidPressureSource` (dihentikan juga saat mode baterai Kritis) | Start ride | Ride selesai, `awaitClose` | `SensorManager.unregisterListener()` | `adb shell dumpsys sensorservice` |
| Wake layar + override kecerahan (redup otomatis) | `RideScreenModeEffect` (layar ride) | Mode "selalu nyala" & layar ride terlihat | Layar ride ditutup / mode diganti | `FLAG_KEEP_SCREEN_ON` dihapus (via `DisposableEffect`) | Layar mati normal setelah keluar layar ride |
| MapView MapLibre | `RouteMap` (`:ui-common`, dipakai halaman Peta ride, detail rute, detail aktivitas) | Composable masuk komposisi (`onCreate`), lalu `onStart`/`onResume` mengikuti lifecycle layar | Keluar komposisi / layar ON_STOP; mode hemat baterai mengganti peta dengan breadcrumb | `onPause()`/`onStop()` sesuai state lalu `onDestroy()` di `DisposableEffect`; `MapLibre.setConnected(isOnline)`: jaringan hanya saat peta online (D-90); offline tile dari cache saja (T11.11, D-128); batas cache diberikan sekali per proses |  LeakCanary bersih; memori turun setelah keluar layar peta |
| MapSnapshotter MapLibre | `rememberRouteSnapshot` (`:ui-common`), dipakai layar Bagikan untuk kartu Peta & Peta berbingkai (T10.6) | Layar Bagikan terbuka dan ada peta (online diizinkan atau region offline) + garis | Selesai satu gambar; input berubah (format, potongan ujung, jaringan) | `snapshotter.cancel()` di `onDispose` `DisposableEffect`; gambar hanya dipegang state layar; `MapLibre.setConnected(isOnline)` | LeakCanary bersih setelah keluar layar Bagikan |
| TextToSpeech + audio focus | `VoiceAnnouncer` di `RideVoiceCoach` (`:platform`, milik `RecordingService`) | Ride mulai/dipulihkan dengan Pengumuman suara aktif atau dengan sesi rencana (T7.4b) (`start`) | Pipeline berhenti (ride selesai, simpan/buang, `onDestroy`) | `stop()` + `shutdown()`; `abandonAudioFocusRequest` setelah tiap ucapan selesai dan saat shutdown | Uji perangkat: fokus audio dilepas setelah bicara (`dumpsys audio`), stack fokus kosong |
| Callback jaringan (`ConnectivityManager.NetworkCallback`) | `AndroidNetworkStatusSource` (`:platform`) | Flow `observe()` dikoleksi — hanya selama layar berpeta tampil (`WhileSubscribed` 5 dtk) dan peta online tidak "Mati" | Koleksi berhenti (layar keluar/latar belakang) | `unregisterNetworkCallback` di `awaitClose` | Test: `activeCollectors` kembali 0 (`RideViewModelsTest`, `MapUseCasesTest`) |
| Coroutine scope service | `RecordingService` | `onCreate` | `onDestroy` | `serviceScope.cancel()` | Test + log |
| Cursor/stream file | Pemanggil | Buka | Selesai dipakai | `use { }` | StrictMode `detectLeakedClosableObjects` |
| Vibrator/MediaPlayer/ToneGenerator (alert, alarm insiden) | `AlertPlayer` | Alert/hitung mundur muncul | Setelah bunyi, dibatalkan, atau service destroy | `release()` / `cancel()` | StrictMode + review |
| Akselerometer (deteksi insiden) | `AccelerometerSource` | Ride dimulai **dan** fitur insiden aktif | Ride selesai/jeda manual, fitur dimatikan, `awaitClose` | `SensorManager.unregisterListener()` | `dumpsys sensorservice` tidak menampilkan listener app setelah ride |
| Pembacaan level baterai | `BatteryLevelSource` | Tick pipeline (≤ 1x/30 dtk) | — (sticky broadcast dibaca, bukan receiver terdaftar) | Tidak ada receiver permanen | Review |
| Bitmap kartu share | `ShareCardRenderer` | Render dimulai | Setelah PNG ditulis | `recycle()`/lepas referensi | Memory Profiler + LeakCanary |
| Stream backup/restore (ZIP, DB snapshot) | `BackupManager` | Mulai backup/restore | Selesai/gagal/dibatalkan | `use { }` + hapus folder sementara | StrictMode `detectLeakedClosableObjects` |
| Impor file peta (`.part`) | `AndroidOfflineMapStore` | Impor dari SAF | Selesai/gagal (selalu, `use { }` + hapus `.part`) | `delete()` file sementara | Folder `files/maps` hanya berisi `.pmtiles` |
| File sementara share/FIT darurat | Pemanggil | Dibuat | Setelah dibagikan / app dibuka berikutnya / ride disimpan | Hapus file | Cek folder cache |
| File crash log (`filesDir/crash_logs`) | `CrashLogStore` (`:platform`) | Uncaught exception | Rotasi: hanya 10 file terbaru disimpan; `clear()` dari pengaturan | `File.delete()` | `CrashLogStoreTest` |
| Database Room | Koin `single` di `:app` (`dataModule`) | Akses pertama (lazy) | Hidup selama proses (by design; satu instance) | — | LeakCanary; StrictMode (tidak ada akses di main thread) |

**Aturan REL-01:** setiap resource baru yang ditambahkan ke kode **wajib** ditambahkan ke tabel ini di PR yang sama.

---

## 2. Memory leak

| ID | Aturan | Verifikasi |
|---|---|---|
| MEM-01 | Tidak menyimpan `Activity`, `Fragment`, `View`, `Composable` state, atau `Context` non-application di objek berumur panjang (ViewModel, singleton Koin, service, companion object). Butuh context → `applicationContext` yang diinjeksi. | Review + LeakCanary |
| MEM-02 | Setiap listener/callback/observer yang didaftarkan **harus** di-unregister pada lifecycle pasangannya (register di `onStart` → unregister di `onStop`, dst.). | Review + LeakCanary |
| MEM-03 | Callback Android dibungkus `callbackFlow` dengan `awaitClose { unregister }` (CO-04). | Unit/instrumented test: collector dibatalkan → unregister terpanggil (fake) |
| MEM-04 | Tidak ada inner class / lambda anonim berumur panjang yang menangkap `Activity`/`View` (termasuk `Handler` & `Runnable` tertunda). | Review + LeakCanary |
| MEM-05 | **Koleksi di memori harus berbatas.** Buffer track point di-flush tiap 5 dtk (maks ~10 record); rolling window (power 3s/10s/30s, grade, smoothing) memakai ring buffer ukuran tetap; data grafik live memakai downsample berukuran tetap. Dilarang `MutableList` yang terus bertambah sepanjang ride. | Unit test ukuran buffer + profil memori ride 3 jam |
| MEM-06 | Bitmap/gambar (thumbnail peta ringkasan) diskalakan sesuai ukuran tampil dan tidak di-cache tanpa batas. | Profiler |
| MEM-07 | LeakCanary aktif di build **debug** (`debugImplementation`). Setiap leak yang dilaporkan = bug P0 sebelum task selesai. | Laporan LeakCanary kosong setelah alur uji fitur |
| MEM-08 | **Uji memori ride panjang:** jalankan simulasi ride 3 jam (fake GPS/BLE 10x speed); heap Java/Kotlin harus stabil (tidak naik terus) setelah fase awal. | `adb shell dumpsys meminfo com.ridecomp.app` di awal, tengah, akhir; atau Memory Profiler |

---

## 3. Battery leak

| ID | Aturan | Verifikasi |
|---|---|---|
| BAT-01 | **Saat tidak merekam, app tidak boleh memegang GPS, koneksi BLE, wakelock, maupun foreground service.** | Uji "idle drain" (§6, L11) |
| BAT-02 | Wakelock hanya `PARTIAL_WAKE_LOCK`, selalu `acquire(timeout)` (mis. 10 menit) dan diperpanjang berkala selama recording; dilepas di blok `finally` dan `onDestroy`. Dilarang `acquire()` tanpa timeout. | `dumpsys power` |
| BAT-03 | GPS 1 Hz hanya saat recording/pre-ride. Saat **paused manual > 10 menit**, interval diturunkan (mis. 5 dtk) dan kembali 1 Hz saat resume. | Log interval + test state machine |
| BAT-04 | Scan BLE selalu dengan filter service UUID dan timeout (pengecualian: **Pindai luas** T12.2 tanpa filter, hanya atas pilihan pengguna, default mati, tetap bertimeout; D-134). Tidak ada scan terus-menerus; reconnect memakai backoff (`03-ARCHITECTURE.md` §4.4). | Log + test backoff |
| BAT-05 | Tidak ada `AlarmManager`, `WorkManager` periodik, `JobScheduler`, atau sinkronisasi background saat idle. (Fase 5: upload Strava hanya dipicu pengguna atau saat app terbuka.) | Review + `dumpsys jobscheduler`/`dumpsys alarm` |
| BAT-06 | Update UI & notifikasi ≤ 1 Hz; notifikasi foreground di-update dengan `setOnlyAlertOnce(true)` dan hanya bila teks berubah. | Review |
| BAT-07 | Tidak ada request jaringan selama ride (kecuali pengguna mengaktifkan unduh tile online). | Network Inspector |
| BAT-08 | Layar: mode redup/mati bekerja; `KEEP_SCREEN_ON` hanya di layar ride. | Uji manual |
| BAT-10 | Akselerometer hanya aktif selama recording dengan fitur insiden aktif, laju sedang (~50 Hz), dan dihentikan saat jeda manual; tidak pernah saat idle. | `dumpsys sensorservice` |
| BAT-11 | Perhitungan best efforts, segmen, dan beban latihan dijalankan **setelah** ride disimpan (bukan selama ride) dan hanya sekali per ride. | Review + log |
| BAT-09 | **Uji konsumsi:** catat % baterai per jam pada ride uji; regresi > 20% dibanding uji sebelumnya = bug. Untuk investigasi gunakan `adb shell dumpsys batterystats` (reset sebelum uji) atau Battery Historian. | Catatan di `field-tests.md` |

---

## 4. Stabilitas, ANR, dan crash

| ID | Aturan | Verifikasi |
|---|---|---|
| STB-01 | **StrictMode** aktif di build debug: `ThreadPolicy.detectAll()` dan `VmPolicy.detectAll()`; Android 9+ dicatat oleh listener dengan stack lengkap (tag `RideComp/StrictMode`, karena logcat sebagian OEM membuang stack `penaltyLog`), Android 8 dengan `penaltyLog()`. Pelanggaran = bug yang harus diperbaiki; pengecualian hanya pelanggaran di dalam layanan sistem (`com.android.server.*` lewat Binder, D-132). Socket jaringan aplikasi selalu diberi tag `TrafficStats`. | `adb logcat -s RideComp/StrictMode:W` kosong selama alur uji |
| STB-02 | Tidak ada I/O, parsing GPX/FIT, atau kalkulasi berat di main thread. | StrictMode |
| STB-03 | `RecordingService.onStartCommand` memanggil `startForeground()` segera (sebelum kerja lain) untuk menghindari crash `ForegroundServiceDidNotStartInTimeException`. | Instrumented test + uji Android 12+ |
| STB-04 | Izin dicek **setiap kali** sebelum memakai GPS/BLE (pengguna bisa mencabut izin kapan saja); jika dicabut di tengah ride → simpan data, tampilkan peringatan, jangan crash. | Uji manual: cabut izin saat ride simulasi |
| STB-05 | Bluetooth/GPS dimatikan pengguna di tengah ride → rekaman lanjut dengan data yang tersisa, peringatan muncul, pulih otomatis saat dinyalakan lagi. | Uji manual |
| STB-06 | Proses dibunuh/HP restart → recovery (`03-ARCHITECTURE.md` §4.5). | L4 |
| STB-07 | Rotasi layar, mode gelap/terang, ubah ukuran font sistem, dan bahasa tidak menghilangkan state atau menghentikan rekaman (state di ViewModel/service, bukan di composable). | Uji manual |
| STB-08 | Penyimpanan hampir penuh → peringatan sebelum ride; jika gagal tulis DB, rekaman tetap di buffer dan dicoba ulang, pengguna diberi tahu. | Test dengan fake DAO yang gagal |
| STB-09 | Semua input eksternal (file GPX, byte BLE, nilai GPS) divalidasi; input rusak tidak boleh menyebabkan crash. | Test dengan input rusak/acak (fuzz sederhana untuk parser) |

---

## 5. Konkurensi & data race

| ID | Aturan |
|---|---|
| CONC-01 | State bersama di pipeline perekaman hanya diubah oleh satu coroutine (aktor/channel) atau melalui `MutableStateFlow.update {}`. Dilarang variabel mutable yang ditulis dari beberapa thread tanpa sinkronisasi. |
| CONC-02 | Callback BLE/GPS (thread binder) hanya meneruskan data ke Flow/Channel; tidak mengubah state domain langsung. |
| CONC-03 | Operasi GATT diserialkan (satu operasi pada satu waktu). |

---

## 6. Uji wajib terkait reliabilitas (tambahan untuk `07-TEST-PLAN.md`)

| # | Skenario | Lulus jika |
|---|---|---|
| R1 | Simulasi ride 3 jam (10x speed) | Heap stabil; tidak ada leak LeakCanary; tidak ada pelanggaran StrictMode |
| R2 | Buka-tutup layar ride/peta/riwayat 30x | Memori kembali ke baseline; LeakCanary bersih |
| L11 | **Idle drain:** setelah ride disimpan, HP dibiarkan 8 jam dengan app di background | Tidak ada wakelock/GPS/BLE/service aktif milik app (`dumpsys power`, `dumpsys location`, `dumpsys activity services`); konsumsi baterai app dapat diabaikan |
| L12 | Cabut izin lokasi / matikan Bluetooth / matikan GPS di tengah ride | Tidak crash, data tersimpan, pulih sesuai STB-04/05 |
| L13 | Ride + 3 sensor tersambung 2 jam | Tidak ada koneksi GATT "nyangkut" setelah ride selesai; sensor bisa dipakai app lain |
