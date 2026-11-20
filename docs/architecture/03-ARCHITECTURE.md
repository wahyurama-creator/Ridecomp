# 03 — Technical Architecture

## 1. Ringkasan stack

**Keputusan:** Native Android dengan **Kotlin + Jetpack Compose**. Logika inti ditulis di modul **Kotlin murni** (tanpa dependency Android) agar kelak bisa dipindah ke Kotlin Multiplatform (KMP) untuk iOS; hanya UI & lapisan platform yang ditulis ulang.

| Lapisan | Pilihan | Alasan |
|---|---|---|
| Bahasa | Kotlin (stable terbaru) | Native, akses langsung API Android |
| UI | Jetpack Compose (Material 3) | Modern, deklaratif; kontrol recomposition granular |
| Async | Kotlin Coroutines + Flow/StateFlow | Stream GPS/BLE → metrik → UI |
| DI | Koin | Ringan, tanpa annotation processing, kompatibel KMP |
| Database | Room (SQLite) | Standar Android, migrasi, Flow; Room kini mendukung KMP (verifikasi versi) |
| Settings | Jetpack DataStore | Pengganti SharedPreferences |
| Lokasi | `LocationManager` (GPS_PROVIDER) | Tidak bergantung Google Play Services (beberapa HP tidak punya GMS); 1 Hz langsung dari GPS |
| Foreground service | `LifecycleService` + `foregroundServiceType="location|connectedDevice"` | Kontrol penuh, tanpa plugin pihak ketiga |
| BLE | Nordic Android BLE Library (atau `BluetoothGatt` langsung) | Antrian operasi GATT & reconnect yang matang. **Ajukan pilihan ke pemilik** |
| Peta | MapLibre Native Android (+ OkHttp 4.12.0 yang dibawanya, dideklarasikan untuk client ber-tag socket, D-132) | Vector tiles OSM, offline; di Compose via `AndroidView` |
| FIT | Garmin FIT SDK (Java) di balik interface, atau encoder Kotlin murni | **Ajukan pilihan**: SDK resmi = cepat & benar (JVM-only, cek lisensi); encoder sendiri = KMP-ready |
| GPX | `XmlPullParser` (bawaan Android) di balik interface `GpxReader` | Tanpa dependency tambahan |
| Grafik | Compose `Canvas` buatan sendiri | Tanpa library chart (hemat ukuran & RAM) |
| Secret (Fase 5) | Android Keystore (mis. via Tink) + DataStore | Simpan token Strava terenkripsi; verifikasi rekomendasi terbaru (EncryptedSharedPreferences sudah deprecated) |
| HTTP (Fase 5) | OkHttp (atau Ktor client bila ingin KMP) | Strava API |

> Semua versi library dipilih saat implementasi (stable terbaru) dan **harus disetujui** pemilik proyek. Gunakan Gradle version catalog (`libs.versions.toml`).

## 2. Target Android & build

- `minSdk` 26 (Android 8.0), `targetSdk` terbaru yang diwajibkan, Gradle Kotlin DSL.
- Build release: R8 (minify + shrink resources) aktif.
- **ABI splits** (arm64-v8a, armeabi-v7a), karena MapLibre membawa native library; banyak HP murah masih 32-bit.
- **Baseline Profile** untuk mempercepat cold start Compose di HP low-end (tambahkan di akhir Fase 1).
- Target ukuran APK per-ABI < 20 MB (tanpa data peta).
- Distribusi awal: APK sideload.

**Izin manifest:**
```
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION            (Android 14+)
FOREGROUND_SERVICE_CONNECTED_DEVICE    (Android 14+, untuk BLE)
BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT   (Android 12+)
BLUETOOTH, BLUETOOTH_ADMIN (maxSdkVersion 30)
POST_NOTIFICATIONS                     (Android 13+)
WAKE_LOCK
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
VIBRATE
SEND_SMS                               (hanya bila deteksi insiden diaktifkan; diminta saat itu)
INTERNET                               (peta & Strava)
```
Catatan: lokasi cukup "saat app digunakan" karena foreground service dimulai saat app terbuka. Tidak perlu `ACCESS_BACKGROUND_LOCATION`. Verifikasi ini saat implementasi. Android 8–11: perhatikan BLE scan memerlukan izin lokasi aktif.

## 3. Struktur modul Gradle

Proyek mengikuti **Clean Architecture** (aturan lengkap: `08-CODING-RULES.md` §1).

```
:core            ← DOMAIN. Kotlin/JVM MURNI (tanpa android.*). Siap dipindah ke KMP commonMain.
   ├─ model/         # entity domain: TrackSample, Activity, Lap, Route, SensorReading, enum
   ├─ usecase/       # StartRideUseCase, SaveRideUseCase, ExportFitUseCase, ImportGpxUseCase, ...
   ├─ repository/    # interface: ActivityRepository, RouteRepository, ProfileRepository, ...
   ├─ source/        # interface: LocationSource, SensorSource, PressureSource, Clock,
   │                 #            DispatcherProvider, Logger, ActivityUploader
   ├─ engine/
   │   ├─ sensors/   # parser HR/CSC/CP (ByteArray → model), rollover
   │   ├─ metrics/   # agregator, auto-pause, elevasi, NP, est. power, kalori, zona
   │   ├─ geo/       # haversine, filter GPS, simplifikasi, route tracker
   │   ├─ recording/ # RideStateMachine, SampleMerger, AutoPauseDetector, ReminderScheduler, BatteryModePolicy
   │   ├─ safety/    # IncidentDetector (logika murni atas sampel akselerasi + kecepatan), IncidentFlow state machine
   │   ├─ route/     # ClimbDetector, TurnDetector, ClimbTracker, TurnAnnouncer
   │   ├─ training/  # BestEffortCalculator, SegmentMatcher, LiveSegmentTracker, TrainingLoadCalculator
   │   ├─ share/     # ShareCardLayout (model tata letak murni; render di :platform)
   │   └─ export/    # FitWriter, GpxWriter (interface + implementasi Kotlin murni bila dipilih)
   └─ util/          # satuan, ring buffer, format
:data            ← DATA. Android library: Room DB, DAO, entity Room, RepositoryImpl, mapper, DataStore
:platform        ← Android library: LocationSource, BleSensorManager, RecordingService,
                   notifikasi, deteksi merek HP, FIT SDK adapter (jika dipilih), GpxReader,
                   AccelerometerSource, BatteryLevelSource, SmsSender, AlertPlayer (getar/bunyi),
                   ShareCardRenderer (Canvas → PNG)
:ui-common       ← Android library (Compose): design system — tema & token (warna, dimensi, tipografi),
                   komponen reusable (DataField, BigButton, AlertBanner, ListRow, state kosong/error,
                   ScreenScaffold), UdfViewModel + ObserveEffects, @ThemePreviews. Tidak bergantung
                   pada modul lain (keputusan D-06).
:feature-ride    ← Compose: layar ride, data field, layar pause, ringkasan
:feature-history ← Compose: riwayat & detail
:feature-sensors ← Compose: scan & pairing
:feature-routes  ← Compose: rute, peta, offline maps
:feature-settings← Compose: profil, profil aktivitas, editor halaman data, keselamatan, pengaturan
:feature-training← Compose: rekor, segmen, tren latihan, editor kartu share
:feature-strava  ← Fase 5, opsional, terisolasi (hanya lewat interface ActivityUploader)
:app             ← Application, navigasi, tema, DI root, i18n (values/ & values-in/)
```

**Aturan dependensi:** `:core` tidak bergantung pada modul lain. `:data` & `:platform` bergantung pada `:core` (mengimplementasikan interface-nya). `:feature-*` hanya bergantung pada `:core` dan `:ui-common`, dan tidak saling bergantung. Aturan ini ditegakkan otomatis oleh root `build.gradle.kts` (build gagal saat konfigurasi) dan aturan detekt `ForbiddenImport` per lapisan (`config/detekt/`). `:app` adalah composition root yang menyambungkan implementasi lewat Koin. Semua logika yang bisa diuji tanpa Android berada di `:core`.

## 4. Komponen utama & alur data

```
 LocationSource (GPS 1 Hz) ─┐
                            ├─► SampleMerger ─► RideStateMachine ─┬─► MetricsAggregator ─► StateFlow ─► Compose UI (≤1 Hz)
 BleSensorManager ──────────┘    (1 record/dtk)        │          ├─► DbWriter (batch tiap 5 dtk)
   HR / CSC / CP                                       │          ├─► RouteTracker (Fase 3)
                                                       │          └─► Notification updater
                                                       └─► Checkpoint (recovery)
         Semua di dalam RecordingService (foreground service), coroutine scope milik service.
```

### 4.1 RecordingService
- `LifecycleService` di proses app, dimulai dengan `startForegroundService` dan memanggil `startForeground()` segera.
- Memiliki seluruh pipeline perekaman; UI hanya meng-*observe* `StateFlow<RideState>` yang diekspos melalui repository/binder. Menutup UI tidak memengaruhi rekaman.
- Wakelock parsial hanya selama status recording/paused.
- `onTaskRemoved` → rekaman tetap berjalan (service bukan bound-only).

### 4.2 RideStateMachine
```
idle ─start→ recording ─pause→ paused ─resume→ recording
recording ─autoPause→ autoPaused ─(gerak)→ recording
paused ─finish→ finished ─save→ idle
paused ─discard→ idle
(any) ─crash/kill→ [saat buka app] recoverable → resume | save | discard
```
Setiap transisi dicatat sebagai event (untuk FIT timer start/stop).

### 4.3 SampleMerger
- Menghasilkan **satu record per detik** (timestamp UTC dibulatkan ke detik).
- Nilai sensor dipakai bila usianya ≤ 3 detik; lebih lama → null.
- Prioritas sumber: speed = sensor roda > GPS; cadence = power meter > CSC (dapat diatur); power = CP.

### 4.4 BleSensorManager
- Satu koneksi per perangkat; auto-connect sensor tersimpan saat pre-ride.
- Reconnect: backoff 2s, 4s, 8s, lalu tiap 15s selama ride. Tidak pernah menghentikan perekaman.
- Operasi GATT diserialkan (satu operasi pada satu waktu). Ini sumber bug umum di Android bila dikerjakan manual.
- Byte mentah diteruskan ke parser murni di `:core`.

### 4.5 Penyimpanan & recovery
- Buffer record di memori → flush ke `track_points` tiap 5 detik dalam satu transaksi Room.
- Agregat ride di-checkpoint tiap flush.
- Saat app dibuka: cek `activities` berstatus `recording|paused` → tawarkan lanjutkan/simpan/buang. Bila service dibangkitkan ulang sistem (`START_STICKY`), lanjutkan dari checkpoint dan catat event `gap`.
- File FIT dibuat saat ride disimpan, bisa dibuat ulang dari DB kapan saja.

## 5. GPS: pengaturan & filter

- `LocationManager.requestLocationUpdates(GPS_PROVIDER, 1000 ms, 0 m)` di looper/thread milik service.
- Buang titik dengan akurasi > 30 m (konfigurasi) atau lonjakan > 120 km/h antar titik.
- Jarak: akumulasi haversine antar titik valid yang bergerak; abaikan jitter saat diam.
- Speed GPS: `Location.speed` bila `hasSpeed()`, dihaluskan EMA (α ≈ 0.3).
- Barometer: `Sensor.TYPE_PRESSURE` bila tersedia.
- Detail rumus: `05-DATA-MODEL.md`.

## 6. Performa Compose di HP low-end

- Setiap data field meng-collect `StateFlow` miliknya sendiri (atau `derivedStateOf`), agar satu nilai berubah tidak me-recompose seluruh layar.
- Model state UI `@Immutable`/stabil; hindari list/lambda tidak stabil di layar ride.
- Tidak ada animasi di layar ride; `HorizontalPager` untuk halaman data tanpa efek tambahan.
- Grafik & profil elevasi digambar dengan `Canvas` dari data yang sudah di-downsample (≤ ~1000 titik), dihitung di `Dispatchers.Default`.
- Selalu uji performa dengan **build release** (debug Compose jauh lebih lambat).

## 7. Strategi baterai

| Sumber konsumsi | Strategi |
|---|---|
| Layar | Mode redup/mati (`02-DESIGN.md` §6); latar hitam |
| GPS | 1 Hz; dimatikan total saat ride selesai |
| BLE | Hanya notifikasi karakteristik yang diperlukan; tidak scan selama ride kecuali reconnect |
| CPU | Update UI ≤ 1 Hz; kalkulasi ringan; batch write DB |
| Peta | Tile offline, kamera tidak dianimasikan terus |
| Jaringan | Tidak ada request selama ride |

**Ketahanan dari "battery killer" OEM:** foreground service + notifikasi; minta `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; halaman bantuan yang membuka setting autostart/background sesuai `Build.MANUFACTURER` (Xiaomi/Redmi/POCO, Oppo, Realme, Vivo, Infinix/Tecno, Samsung) dengan tautan ke dontkillmyapp.com.

## 8. Modul Strava (Fase 5, opsional)

`:feature-strava` hanya diakses lewat interface `ActivityUploader` yang didefinisikan di `:core`. Jika modul tidak dipakai, app tetap utuh. Disembunyikan di Pengaturan sampai diaktifkan pengguna.

## 9. Jalur ke iOS (fase lanjut)

1. Ubah `:core` menjadi modul KMP (`commonMain`); ganti dependency JVM-only (mis. FIT SDK Java) dengan implementasi Kotlin murni bila perlu.
2. Room/DataStore dapat ikut ke KMP, atau DB iOS terpisah. Diputuskan saat itu.
3. UI iOS ditulis dengan SwiftUI (atau Compose Multiplatform), lapisan platform (lokasi, BLE, background) ditulis native iOS.
4. Catatan: perlu Background Modes "Location updates" & "Uses Bluetooth LE accessories"; build & instalasi permanen membutuhkan Mac dan Apple Developer Program (berbayar).
