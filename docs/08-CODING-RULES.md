# 08 — Coding Rules (WAJIB)

Aturan ini berlaku untuk **setiap baris kode** di proyek. Jika sebuah aturan harus dilanggar, agent wajib menjelaskan alasannya dan meminta persetujuan pemilik proyek, lalu menandai kodenya dengan komentar `// RULE-EXCEPTION: <alasan>`.

Penomoran aturan (mis. `ARCH-03`) dipakai di checklist (`10-FEATURE-CHECKLIST.md`) dan review.

---

## 1. Arsitektur: Clean Architecture + UDF

### 1.1 Lapisan

```
┌────────────────────────────────────────────────────────────┐
│ PRESENTATION  (:feature-*)                                 │
│   Composable (stateless) ← UiState ← ViewModel → UseCase   │
├────────────────────────────────────────────────────────────┤
│ DOMAIN  (:core, Kotlin murni)                              │
│   model/ (entity)  usecase/  repository/ (interface)       │
│   source/ (interface: LocationSource, SensorSource, Clock) │
│   engine/ (sensors parser, metrics, geo, recording, export)│
├────────────────────────────────────────────────────────────┤
│ DATA & PLATFORM  (:data, :platform)                        │
│   RepositoryImpl, Room DAO/Entity, DataStore, Mapper       │
│   AndroidLocationSource, BleSensorSource, RecordingService │
└────────────────────────────────────────────────────────────┘
   :app = composition root (Koin modules, navigasi, tema)
```

| ID | Aturan |
|---|---|
| ARCH-01 | **Dependency rule:** dependensi hanya mengarah ke dalam (presentation → domain ← data/platform). `:core` tidak bergantung pada modul apa pun dan tidak boleh mengimpor `android.*`, `androidx.*`, Room, Koin, atau library UI. |
| ARCH-02 | Modul `:feature-*` hanya bergantung pada `:core` (+ design system di `:app`/`:ui-common` bila dibuat). **Dilarang** mengimpor `:data` atau `:platform` langsung; implementasi disuntikkan lewat Koin di `:app`. |
| ARCH-03 | Repository & source didefinisikan sebagai **interface di `:core`**, diimplementasikan di `:data`/`:platform` (dependency inversion). |
| ARCH-04 | Satu UseCase = satu aksi bisnis, satu fungsi publik `operator fun invoke(...)`. Nama: kata kerja + `UseCase` (mis. `StartRideUseCase`). UseCase tidak menyimpan state. |
| ARCH-05 | Entity domain (`:core/model`) terpisah dari entity Room (`:data`) dan dari UiState (`:feature-*`). Konversi lewat **mapper** eksplisit (`toDomain()`, `toEntity()`, `toUiModel()`). |
| ARCH-06 | **Unidirectional Data Flow:** UI mengirim `UiEvent`/memanggil fungsi ViewModel → ViewModel memanggil UseCase → ViewModel memperbarui `StateFlow<UiState>` → UI merender. UI tidak pernah mengubah state langsung. |
| ARCH-07 | ViewModel tidak memegang `Context`, `View`, `Activity`, atau `NavController`. Navigasi diekspresikan sebagai event/efek satu kali (mis. `Channel` → `receiveAsFlow()`). |
| ARCH-08 | Pipeline perekaman (RideStateMachine, SampleMerger, MetricsAggregator) adalah logika domain di `:core/engine`, dijalankan oleh `RecordingService` di `:platform`. Service hanya *wiring* + lifecycle Android, bukan tempat logika bisnis. |
| ARCH-09 | Tidak ada singleton global buatan sendiri (`object` dengan state mutable). Gunakan scope Koin (`single`, `factory`, `viewModel`). |
| ARCH-10 | Modul `:feature-strava` hanya diakses lewat interface `ActivityUploader` di `:core`. |

### 1.2 Struktur package per modul

```
:core        com.ridecomp.core.{model, usecase, repository, source, engine.sensors,
                               engine.metrics, engine.geo, engine.recording, engine.export, util}
:data        com.ridecomp.data.{db, db.dao, db.entity, repository, mapper, prefs}
:platform    com.ridecomp.platform.{location, ble, service, notification, device, export}
:feature-x   com.ridecomp.feature.x.{ui, ui.component, viewmodel, model}
:app         com.ridecomp.app.{di, navigation, theme}
```

---

## 2. Konvensi penamaan

| ID | Elemen | Gaya | Contoh |
|---|---|---|---|
| NAME-01 | Variabel, properti, parameter, fungsi | `camelCase` | `movingTimeSec`, `fun computeNormalizedPower()` |
| NAME-02 | Class, interface, object, enum class, typealias | `PascalCase` | `RideStateMachine`, `LocationSource` |
| NAME-03 | Konstanta (`const val`, top-level/companion immutable) | `SCREAMING_SNAKE_CASE` | `MAX_GPS_ACCURACY_M`, `FLUSH_INTERVAL_MS` |
| NAME-04 | Enum entries | `SCREAMING_SNAKE_CASE` | `AUTO_PAUSED`, `HEART_RATE` |
| NAME-05 | Package | huruf kecil semua, tanpa underscore | `com.ridecomp.core.engine.metrics` |
| NAME-06 | File Kotlin | Sama dengan class utama di dalamnya (`PascalCase.kt`); file berisi fungsi ekstensi: `<Tipe>Ext.kt` | `RideViewModel.kt`, `LocationExt.kt` |
| NAME-07 | Composable | `PascalCase`, kata benda; tidak mengembalikan nilai | `DataField()`, `RideScreen()` |
| NAME-08 | Resource Android (string, drawable, dimen, color) | `snake_case` dengan prefiks jenis | `ride_button_pause`, `ic_heart_rate`, `bg_field` |
| NAME-09 | Boolean | Prefiks `is/has/can/should` | `isRecording`, `hasBarometer` |
| NAME-10 | Satuan dalam nama bila bukan jelas | sufiks satuan | `distanceM`, `speedMps`, `durationMs`, `powerW`, `cadenceRpm`, `hrBpm` |
| NAME-11 | Flow/StateFlow | Kata benda; backing property privat diawali `_` | `private val _uiState`, `val uiState` |
| NAME-12 | Sufiks peran | wajib konsisten | `…UseCase`, `…Repository` (interface), `…RepositoryImpl`, `…Dao`, `…Entity`, `…ViewModel`, `…UiState`, `…UiEvent`, `…Mapper`, `…Source`, `…Parser` |
| NAME-13 | Test class & fungsi | `<Class>Test`; fungsi pakai backtick kalimat | ``fun `crank time rollover produces correct cadence`()`` |
| NAME-14 | Singkatan | Diperlakukan sebagai kata | `GpxReader`, `FitWriter`, `BleSensorSource` (bukan `GPXReader`) |

Bahasa kode: **Inggris**. Teks UI di string resources (id & en). Komentar boleh Indonesia atau Inggris, konsisten per file.

---

## 3. Gaya & kualitas Kotlin

| ID | Aturan |
|---|---|
| KT-01 | Ikuti Kotlin Coding Conventions resmi. Format otomatis dengan ktlint (`.editorconfig` di root). Analisis statis dengan detekt + Android Lint. **Nol warning** sebelum task dianggap selesai. |
| KT-02 | Utamakan `val` & koleksi immutable (`List`, bukan `MutableList`) di API publik. Data model = `data class` dengan properti `val`. |
| KT-03 | **Dilarang `!!`.** Tangani null dengan `?.`, `?:`, `requireNotNull(x) { "pesan" }` (hanya untuk invariant), atau early return. |
| KT-04 | Dilarang `lateinit` di luar kelas framework yang mewajibkannya; dilarang `lateinit` untuk state domain. |
| KT-05 | Fungsi maks ~40 baris, class maks ~300 baris, parameter maks 6 (gunakan data class). Melebihi → pecah. |
| KT-06 | Tidak ada *magic number*. Semua ambang (akurasi GPS, auto-pause, threshold elevasi, timeout) adalah konstanta bernama atau nilai config, dengan satuan di nama. |
| KT-07 | Modifier visibilitas seketat mungkin: default `internal`/`private`; `public` hanya untuk API modul. |
| KT-08 | `when` atas sealed class/enum harus *exhaustive* tanpa cabang `else`. |
| KT-09 | State & hasil dimodelkan dengan `sealed interface`/`sealed class` (mis. `RideState`, `SensorConnectionState`). |
| KT-10 | Tidak ada kode mati, kode di-comment, atau `TODO` tanpa nomor task (`// TODO(T2.5): ...`). |
| KT-11 | KDoc wajib untuk setiap API publik `:core` dan setiap rumus (sebutkan sumber rumus/dokumen `05-DATA-MODEL.md` §x). |
| KT-12 | Hanya `kotlinx.datetime`/`java.time` untuk waktu; semua timestamp internal = epoch ms UTC (`Long`). Waktu diambil dari `Clock` yang diinjeksi, bukan `System.currentTimeMillis()` langsung. |

---

## 4. Error handling & logging

| ID | Aturan |
|---|---|
| ERR-01 | Dilarang `catch (e: Exception) {}` kosong atau menelan exception tanpa log/penanganan. |
| ERR-02 | Jangan menangkap `CancellationException` (atau lempar ulang segera). Gunakan helper `runCatchingCancellable` bila perlu. |
| ERR-03 | Error yang diharapkan (sensor putus, file GPX rusak, izin ditolak) dimodelkan sebagai tipe hasil (`sealed` `Result`/`Outcome`), bukan exception, dan ditampilkan ke UI dengan pesan yang dapat dipahami. |
| ERR-04 | Data rusak dari sensor/GPS **dibuang dan dicatat**, tidak pernah membuat crash atau menghentikan perekaman. |
| ERR-05 | Logging lewat interface `Logger` di `:core` (implementasi Android di `:platform`). Di build release: tidak log koordinat, token, atau data pribadi. Level `debug` dimatikan di release. |
| ERR-06 | Uncaught exception handler menulis crash log lokal (file teks, maks 10 file, rotasi) yang bisa diekspor pengguna. Tanpa layanan crash reporting pihak ketiga. |

---

## 5. Coroutines & Flow

| ID | Aturan |
|---|---|
| CO-01 | **Dilarang `GlobalScope`** dan `runBlocking` di kode produksi. |
| CO-02 | Setiap coroutine milik scope yang jelas dengan lifecycle: `viewModelScope`, `lifecycleScope`, atau `serviceScope` (`SupervisorJob()`) yang di-`cancel()` di `onDestroy`. |
| CO-03 | Dispatcher diinjeksi (interface `DispatcherProvider`), tidak di-hardcode, agar bisa diuji. CPU → `Default`, DB/file → `IO`, main hanya untuk UI. |
| CO-04 | Callback API Android (Location, BLE, Sensor) dibungkus `callbackFlow { ...; awaitClose { unregister() } }`. **`awaitClose` wajib berisi pelepasan resource.** |
| CO-05 | UI meng-collect Flow dengan `collectAsStateWithLifecycle()` (Compose) atau `repeatOnLifecycle(STARTED)`. Dilarang `collectAsState()` untuk flow dari repository/service. |
| CO-06 | `StateFlow` diekspos read-only; `MutableStateFlow` selalu `private`. Update state dengan `update { }` (atomik). |
| CO-07 | Flow panas yang dibagikan memakai `stateIn/shareIn` dengan `SharingStarted.WhileSubscribed(5_000)`, kecuali pipeline perekaman yang memang hidup selama service. |
| CO-08 | Tidak ada polling `while(true) { delay() }` kecuali ticker 1 Hz pipeline perekaman yang hanya berjalan saat status recording/paused dan berhenti saat ride selesai. |
| CO-09 | Operasi dengan durasi tak tentu (scan BLE, menunggu GPS fix, upload) wajib punya `withTimeout`/batas waktu. |

---

## 6. Jetpack Compose

| ID | Aturan |
|---|---|
| UI-01 | Pola **Route/Screen**: `XxxRoute()` mengambil ViewModel & state; `XxxScreen(state, onEvent)` stateless dan bisa di-preview. |
| UI-02 | Composable menerima tipe stabil/immutable (`@Immutable` data class, `ImmutableList` dari kotlinx-collections-immutable atau wrapper stabil). Lambda diteruskan sebagai parameter, bukan dibuat ulang di dalam loop. |
| UI-03 | Tidak ada side effect di body composable. Gunakan `LaunchedEffect`/`DisposableEffect` dengan key yang benar; `DisposableEffect` wajib `onDispose` melepas resource. |
| UI-04 | Layar ride: setiap `DataField` membaca state miliknya sendiri; perubahan satu metrik tidak boleh me-recompose field lain (verifikasi dengan Layout Inspector recomposition count). |
| UI-05 | Dilarang hardcode string & warna di composable. Gunakan `stringResource()` dan design token tema. Dimensi dari token (`Spacing`, `Size`). |
| UI-06 | Setiap Screen punya `@Preview` minimal untuk state normal, kosong, dan error (tema gelap). |
| UI-07 | Target sentuh ≥ 56dp (utama ≥ 72dp); setiap ikon interaktif punya `contentDescription`. |
| UI-08 | `MapView` MapLibre dibungkus `AndroidView` dengan lifecycle diteruskan (onStart/onStop/onDestroy) lewat `DisposableEffect` + `LifecycleEventObserver`. |
| UI-09 | Tidak ada animasi di layar ride (lihat `02-DESIGN.md`). |

---

## 7. Data & persistence

| ID | Aturan |
|---|---|
| DATA-01 | Akses DB/file tidak pernah di main thread (Room `allowMainThreadQueries` dilarang). |
| DATA-02 | Setiap perubahan skema Room = naikkan versi + **migrasi eksplisit** + test migrasi. Dilarang `fallbackToDestructiveMigration` (data ride pengguna tidak boleh hilang). |
| DATA-03 | Insert batch track point dalam satu `@Transaction`. |
| DATA-04 | Query daftar memakai paging/limit; dilarang memuat semua `track_points` ride ke memori sekaligus untuk UI (downsample di query/stream). |
| DATA-05 | Semua stream file/cursor ditutup dengan `use { }`. |
| DATA-06 | Rahasia (token Strava) tidak pernah masuk log, DB biasa, atau repo; simpan terenkripsi (Keystore). `../local.properties` dan file kunci masuk `.gitignore`. |

---

## 8. Dependensi & build

| ID | Aturan |
|---|---|
| DEP-01 | Semua dependency di `../gradle/libs.versions.toml`; tidak ada versi hardcode di `build.gradle.kts`. |
| DEP-02 | Dependency baru wajib disetujui pemilik proyek, dengan evaluasi keamanan SUP-07 dan review merged manifest SUP-08 (`11-SECURITY-RULES.md`). |
| DEP-03 | Dilarang SDK analytics, iklan, atau tracking. |
| DEP-04 | Build release: R8 + shrink resources aktif; uji fitur di build release sebelum dianggap selesai. |

---

## 9. Testing

| ID | Aturan |
|---|---|
| TEST-01 | Setiap UseCase, parser, rumus, mapper, dan state machine **wajib** punya unit test. Target coverage `:core` ≥ 80% baris (diukur dengan Kover/JaCoCo). |
| TEST-02 | Pola test **Given–When–Then**; satu perilaku per test. |
| TEST-03 | Test deterministik: waktu via fake `Clock`, coroutine via `runTest` + `StandardTestDispatcher`, tanpa `Thread.sleep`. |
| TEST-04 | Gunakan *fake* (implementasi interface sederhana), bukan mock berlebihan, untuk repository/source. |
| TEST-05 | Setiap bug yang ditemukan → tulis test yang mereproduksinya **sebelum** memperbaikinya (regression test). |
| TEST-06 | ViewModel diuji: event → UiState yang diharapkan (pakai Turbine atau `StateFlow.value`). |
| TEST-07 | Room: test DAO & migrasi di `androidTest`. |
| TEST-08 | Fitur yang menyentuh GPS/BLE/service wajib diuji dengan sumber simulasi (`07-TEST-PLAN.md` §2) dan dicatat uji di perangkat fisik. |

---

## 10. Git & alur kerja

| ID | Aturan |
|---|---|
| GIT-01 | Commit kecil & fokus, pesan **Conventional Commits**: `feat(ride): add auto-pause`, `fix(ble): handle crank time rollover`, `test(core): ...`, `refactor`, `docs`, `chore`. Sertakan nomor task: `feat(ride): add auto-pause (T1.4)`. |
| GIT-02 | Satu branch per task/fitur: `feature/T1.4-auto-pause`, `fix/<deskripsi>`. |
| GIT-03 | Sebelum commit: format, lint, detekt, unit test lulus (`./gradlew ktlintCheck detekt lint test` atau padanan yang disetujui). |
| GIT-04 | Setiap fitur ditutup dengan **Feature Report** sesuai `10-FEATURE-CHECKLIST.md`. |
