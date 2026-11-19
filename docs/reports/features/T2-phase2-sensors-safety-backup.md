# Feature Report — Fase 2: Sensor BLE, profil, keselamatan, backup / MVP-b (T2.1–T2.17)

**Tanggal:** 2026-11-13
**Branch:** satu branch per task (`feature/T2.x-…`), semua di-merge ke `main` dengan `--no-ff`.
**Status:** Menunggu review pemilik. **Uji perangkat & lapangan belum dilakukan** (D-05).

## 1. Ringkasan
Fase 2 menambah sensor, profil aktivitas, keselamatan, dan backup:
- **Sensor BLE (T2.1–T2.7, T2.9–T2.10):**
  - Layar Sensor: scan berfilter service dengan batas waktu, pasangkan, lupakan, baterai sensor.
  - Selama ride: sambung otomatis dengan reconnect backoff 2/4/8/15 dtk; HR, cadence, power (3 dtk/10 dtk/avg/max/NP, W/kg), estimated power (tidak pernah ditulis ke field `power` FIT), dan kalori.
  - Peringatan sensor putus/kembali serta baterai sensor < 15%; indikator HR/CAD/PWR di status bar.
  - FIT berisi HR, cadence, dan power.
- **Halaman data (T2.8):** editor untuk menambah/menghapus halaman, memilih 1–8 field, dan memilih metrik tiap field. Halaman disimpan sebagai JSON dan divalidasi saat dibaca.
- **Grafik HR/cadence/power** di detail aktivitas (T2.11). **Tombol volume** opsional: Atas = lap, Bawah = jeda/lanjut (T2.12).
- **Profil aktivitas (T2.13):**
  - Lima profil bawaan (Road, Gravel, MTB, Indoor, Commute), bisa diubah dan diduplikasi; profil kustom bisa dihapus.
  - Pemilih profil di Pre-ride; profil terakhir menjadi default.
  - Mode **Indoor** berjalan tanpa GPS dan tanpa barometer; sub_sport FIT mengikuti profil.
- **Keselamatan (T2.14–T2.16):**
  - Kontak darurat (maksimal 3) dari contact picker atau diketik; SMS uji maksimal 1 per menit.
  - Deteksi jatuh dari akselerometer, diikuti hitung mundur 30 dtk layar penuh dengan alarm keras.
  - SMS berisi lokasi ke semua kontak, retry tiap 60 dtk (maksimal 5×), satu update lokasi setelah 5 menit, lalu "Saya aman".
  - Batas keras 3 SMS per kontak per insiden. SOS manual: tahan 3 dtk di layar jeda.
  - Tombol insiden juga ada di notifikasi (layar kunci).
- **Backup & restore (T2.17):**
  - Arsip `.rcbackup` dibuat secara streaming, dengan opsi kata sandi (AES-256-GCM + PBKDF2).
  - Restore memvalidasi arsip (zip-slip, ukuran, checksum, versi skema, integritas DB), membuat salinan pengaman lebih dulu, dan menukar data secara atomik saat app dimulai ulang.
  - Pengingat backup muncul di Beranda saat app dibuka.

## 2. Perubahan per lapisan
| Lapisan | Isi utama |
|---|---|
| `:core` | `engine.sensors` (parser BLE, `SensorFusion`, alert sensor, `ReconnectBackoff`, `withScanResult`), `engine.safety` (`IncidentDetector`, `IncidentManager`), `engine.backup` (`BackupCrypto`, `BackupArchiveWriter/Reader`), model profil/halaman data/keselamatan, use case sensor, halaman data, profil, keselamatan, backup; fake baru di `testFixtures` |
| `:data` | Skema **v2** (`sensors`), **v3** (`activity_profiles` + seed), **v4** (`emergency_contacts`, `incidents`) dengan migrasi eksplisit + test SQL; repository sensor/profil/keselamatan/insiden/status backup; `DataPagesJson`; `RoomBackupDatabase` + `PendingRestore` |
| `:platform` | `AndroidBleScanner`, `GattClient` (antrian operasi serial), `AndroidSensorConnector`; `AndroidSmsGateway` (satu-satunya pengirim SMS), `AndroidSafetyMessages`, `AndroidPickedContactReader`, `AndroidAccelerometerSource`; `IncidentSupervisor` di service; alarm `ToneGenerator`; `AndroidBackupFiles`, `JsonBackupManifestCodec`, `AndroidAppRestarter`; izin BLE/SMS |
| `:ui-common` | `SectionHeader`, `PickerDialog`, `Modifier.volumeKeys`, `Modifier.holdToConfirm` |
| `:feature-sensors` | Layar Sensor + ViewModel |
| `:feature-ride` | Editor halaman data, profil (daftar, editor, pemilih Pre-ride), overlay hitung mundur insiden, banner "Saya aman", tombol SOS, kartu pengingat backup, banner sensor |
| `:feature-history` | Grafik umum `RideChart` (speed, elevasi, HR, cadence, power) |
| `:feature-settings` | Layar Keselamatan, layar Backup & restore, setting tombol volume |
| `:app` | Route baru (sensor, halaman data, profil, keselamatan, backup), wiring Koin, `AppModulesTest` diperluas |

## 3. Dependency baru
| Library | Versi | Scope | Alasan | Lisensi |
|---|---|---|---|---|
| `org.json:json` | 20250107 | **test saja** (`:data`, `:platform`) | Implementasi org.json nyata untuk unit test JVM (android.jar hanya stub). Tidak masuk APK (D-30) | JSON License (Public Domain–like) |

Dependency runtime tidak bertambah. BLE memakai API platform (D-24), kripto memakai JCA (D-25, D-41), dan JSON memakai org.json bawaan Android (D-26). `verification-metadata.xml` diperbarui untuk artefak baru dan POM yang dibaca laporan lisensi.

## 4. Resource & siklus hidup
| Resource | Diakuisisi | Dilepas | Cara verifikasi |
|---|---|---|---|
| Scan BLE | Tombol Pindai | Timeout 15 dtk, tombol Hentikan, layar `ON_STOP`, ViewModel dibersihkan (`awaitClose` → `stopScan`) | Review; log (pending perangkat) |
| Koneksi GATT | Start/resume ride (sensor auto-connect) | Ride selesai, simpan/buang, `onDestroy` (job batal → `finally` `disconnect()`+`close()`) | `FakeSensorConnector.activeCollectors`; L13 (pending) |
| Akselerometer | Ride berjalan **dan** deteksi insiden aktif | Ride selesai, `onDestroy` (`unregisterListener` di `awaitClose`) | `FakeAccelerometerSource`; `dumpsys sensorservice` (pending) |
| GPS saat Indoor | Tidak diakuisisi | — | `PreRideViewModel` test: 0 kolektor GPS saat Indoor |
| `ToneGenerator` alarm | Detik pertama hitung mundur | `AlertPlayer.cancel()` di `onDestroy` | Review |
| Receiver hasil SMS | Per SMS | `finally` → `unregisterReceiver` (timeout 30 dtk) | Review |
| Folder kerja backup | Per backup/restore (cache internal) | `finally` → `deleteRecursively` | `BackupUseCasesTest` |

Tidak ada GPS, BLE, wakelock, maupun service saat tidak merekam (BAT-01). Pengingat backup tidak memakai AlarmManager/WorkManager (BAT-05).

## 5. Checklist kode
- **Arsitektur:**
  - logika baru di `:core` (detektor & alur insiden, kripto, arsip, fusi sensor, override profil);
  - service hanya wiring lewat `IncidentSupervisor`;
  - modul feature tidak saling bergantung, sehingga editor halaman data dan UI profil berada di `:feature-ride` (D-31, D-33).
- **Konstanta:**
  - ambang di `IncidentConfig`, `IncidentFlowConfig`, `BleConfig`, `SafetyConfig`, `DataPageLimits`, `BackupArchiveConfig`, `BackupCryptoConfig`, `BackupReminderConfig`, `FitSportValues`;
  - nama tabel di `DbNames`, kunci setting di `SettingsKeys`;
  - teks en/id di `strings.xml`, dengan `plurals` untuk jumlah.
- **Constructor besar** dikelompokkan sebagai data class use case (`RideUseCases`, `HomeUseCases`, `SafetyUseCases`, `BackupUseCases`).
- **Konkurensi:**
  - operasi GATT diserialkan (CONC-03);
  - detektor dikunci ke dispatcher service;
  - `IncidentManager` memakai Mutex dan pengiriman SMS dijalankan di luar lock rekaman.
- **Memori:**
  - hasil scan dibatasi 20 dan sampel akselerometer memakai ring buffer 300;
  - backup/restore streaming dengan buffer 64 KiB, dan dekripsi per segmen tidak memuat file ke RAM.
- **Data:** tiga migrasi eksplisit dengan test SQL vs skema yang di-export dan test perangkat; tanpa destructive migration.

## 6. Hasil testing
- **Unit test:** **365 test**, 0 gagal (Fase 1: 244). Yang baru:
  - `IncidentDetectorTest` — skenario sintetis: jatuh terdeteksi; lubang jalan, polisi tidur, rem mendadak, dan HP jatuh saat diam tidak terdeteksi;
  - `IncidentManagerTest` — batal berarti tanpa SMS, batas 3 SMS per kontak, retry 5×, SOS manual, fitur mati;
  - `BackupCryptoTest` — round-trip, kata sandi salah, data diubah, segmen terakhir dibuang, file terpotong;
  - `BackupArchiveTest` — zip-slip (`../`, path absolut, subfolder), checksum, entri hilang, manifest bukan entri pertama;
  - `BackupUseCasesTest` — round-trip terenkripsi, blokir saat ride, skema lebih baru, pengingat;
  - `SafetyUseCasesTest` — normalisasi nomor, batas kontak, izin, batas SMS uji;
  - test profil, halaman data, sensor, alert sensor, tombol volume, grafik;
  - `MigrationSqlTest` untuk migrasi 1→2, 2→3, 3→4;
  - test ViewModel semua layar baru, dan `AppModulesTest` me-resolve semua ViewModel serta `IncidentManager`.
- **Instrumented test** (ditulis, ter-compile, belum dijalankan): migrasi 1→2, 2→3 (seed), semua migrasi, serta snapshot backup tanpa kontak darurat + `integrity_check`.
- **`./gradlew ktlintCheck detekt lint test koverVerify assembleDebug assembleRelease`:** ✅.
- **Coverage `:core`:** 96,8% baris.
- **APK release per-ABI:** ±1,89 MB (arm64 1.892.424 B; armeabi-v7a 1.889.582 B).
- **Bug yang ditemukan dan diperbaiki selama fase:**
  1. connector mengirim SENSOR_LOST di setiap percobaan reconnect (D-28);
  2. reset halaman data profil tidak meneruskan `profileId`;
  3. dialog izin SMS bisa berulang (sekarang hanya sekali, lalu pesan);
  4. ringkasan backup terkunci oleh flag `isWorking` bersarang.
- **Uji perangkat / lapangan: BELUM**, yaitu L13 (3 sensor 2 jam), L17 (uji jatuh aman + 10 km jalan berlubang), S11 (SMS), restore pada HP lain, serta kalibrasi ambang insiden.

## 7. Keamanan
- **Checklist manual §D:**
  1. **Input luar:**
     - byte BLE divalidasi parser (STB-09);
     - nomor telepon dinormalisasi dan divalidasi;
     - URI contact picker hanya dari authority kontak;
     - JSON halaman data dan manifest backup tidak pernah crash (null berarti default/tolak);
     - arsip backup diperlakukan sebagai input tidak tepercaya: nama diwhitelist, ukuran/jumlah dibatasi, SHA-256 dicek, dan iterasi KDF di header dibatasi.
  2. **Komponen:** tidak ada komponen baru yang ter-export. Receiver hasil SMS didaftarkan `RECEIVER_NOT_EXPORTED`, sehingga SMS tidak bisa dipicu dari luar (SEC-SMS-06).
  3. **Data sensitif:**
     - nomor kontak tidak pernah di-log (`toString` disamarkan) dan tidak ikut backup (dihapus lalu `VACUUM`, SEC-BK-01);
     - kata sandi backup tidak disimpan dan array-nya dihapus setelah dipakai.
  4. **File:**
     - backup hanya ke/dari lokasi pilihan SAF (D-40);
     - folder kerja di cache internal dihapus;
     - salinan pra-restore di storage internal.
  5. **PendingIntent:** aksi notifikasi insiden dan hasil SMS memakai intent eksplisit + `FLAG_IMMUTABLE`.
  6. **Jaringan:** tidak ada. Tautan peta di SMS adalah HTTPS.
  7. **SQL:** konstanta atau bind args; seed profil memakai bind args.
  8. **Rahasia:** tidak ada.
  9. **Izin baru:**
     - `BLUETOOTH_SCAN` (`neverForLocation`) dan `BLUETOOTH_CONNECT`;
     - `BLUETOOTH`/`BLUETOOTH_ADMIN` (`maxSdk 30`);
     - `SEND_SMS` (diminta hanya saat fitur diaktifkan, SEC-SMS-01);
     - `uses-feature` bluetooth_le/telephony `required=false`.
  10. **Dependency:** satu, khusus test (lihat §3).
  11. **Pesan error:** generik, tanpa detail teknis.
  12. **Fitur debug:** tetap hanya di source set debug.
  13. **SMS & kontak:**
      - `SmsGateway.send` hanya dipanggil oleh `IncidentManager` dan `SendTestSmsUseCase` (diverifikasi dengan grep);
      - batas 3 SMS per kontak per insiden, retry maksimal 5×, SMS uji 1 per menit (SEC-SMS-03);
      - isi minimal (SEC-SMS-04).
- **Merged manifest release:** komponen ter-export hanya launcher dan `ProfileInstallReceiver` (dari library, dilindungi izin `DUMP`).
- **Scan CVE (OSV, `security/phase2-osv-scan.txt`):**
  - **0 temuan di kode yang masuk APK**;
  - 14 High/Critical ada di tool build saja (tidak berubah sejak Fase 1);
  - `org.json:json` (test saja) punya temuan Moderate DoS. App memakai org.json bawaan Android, dengan input manifest dibatasi 64 KiB.
- **Lisensi:** `generateLicenseReport` sukses. Runtime tetap Apache-2.0.
- **Belum:** MobSF atas APK release dan Dependency-Check NVD (butuh API key, D-16) menunggu pemilik.

## 8. Penyimpangan / keputusan
D-24…D-42 di `DECISIONS.md`. Yang paling perlu ditinjau:
- **D-32:** profil hanya meng-override yang perlu (GPS, halaman data, auto-pause on/off, auto-lap, mode layar, permukaan/posisi, FIT sub_sport). Pengingat dan ambang auto-pause tetap global; kolomnya sudah disiapkan.
- **D-32 (nilai FIT):** nilai FIT `sub_sport` diambil dari ingatan Profile.xlsx dan **wajib dicek dengan FitCSVTool**.
- **D-34:** nomor kontak tidak dikonversi otomatis ke format +62.
- **D-37:** tanpa full-screen intent. Alarm keras + notifikasi dengan tombol menjadi pengganti saat layar terkunci.
- **D-39:** restore memulai ulang app agar DB ditukar sebelum Room terbuka.
- **D-41:** enkripsi per segmen (konstruksi STREAM) di atas primitif JCA. Meski hanya menyusun primitif standar, mohon ditinjau karena menyangkut kripto.
- **T2.8:** commit sempat masuk langsung ke `main`, lalu dipindahkan ke branch dan di-merge `--no-ff`. Belum pernah di-push.

## 9. Risiko & bug yang diketahui
- **Semua fitur Fase 2 belum diuji di perangkat.** Risiko terbesar:
  - perilaku GATT antar-vendor HP/sensor;
  - **ambang deteksi insiden** yang belum dikalibrasi dengan data nyata (alarm palsu/terlewat);
  - pengiriman SMS di berbagai operator;
  - restore pada HP lain.
- Bila app tidak terbuka saat hitung mundur, layar tidak menyala otomatis. Yang tersedia hanya alarm, getar, dan notifikasi dengan tombol (D-37).
- Restore gagal di tengah tidak menyentuh data lama. Namun bila proses mati **setelah** staging dan **sebelum** restart, DB tertukar saat app dibuka berikutnya; ini memang yang diharapkan.
- `PendingRestore` belum punya test perangkat khusus (logikanya kecil: rename atomik).
- Pengingat berbasis jarak dan tema per profil belum ada.

## 10. Cara menguji (untuk pemilik proyek)
1. **Sensor:** Sensor → Pindai → pasangkan HR strap/sensor cadence → mulai ride. Status bar menampilkan HR/CAD/PWR hijau. Matikan sensor: muncul banner kuning dan getar dua kali. Nyalakan lagi: banner hilang.
2. **Profil:** Pre-ride → pilih **Indoor** → teks "GPS tidak dipakai" → mulai di trainer dengan sensor speed. Jarak bertambah dari sensor roda. Export FIT, lalu cek `sub_sport` dengan FitCSVTool.
3. **Halaman data:** Pengaturan → Halaman data → ubah jumlah field dan metrik → Simpan → mulai ride.
4. **Keselamatan:**
   - Pengaturan → Keselamatan → pilih kontak → aktifkan (izin SMS) → Kirim SMS uji.
   - Lalu L17: dengan HP di holder, berjalan pelan, jatuhkan HP ke permukaan empuk dan biarkan diam. Hitung mundur dan alarm harus muncul; tekan "Saya baik-baik saja".
   - Coba juga SOS (tahan 3 dtk di layar jeda) dengan kontak milik sendiri.
5. **Backup:**
   - Pengaturan → Backup & restore → aktifkan kata sandi → Buat backup ke Drive.
   - Di HP lain (atau setelah uninstall), Pilih file backup → kata sandi → ringkasan → konfirmasi 2× → Mulai ulang. Riwayat harus kembali, dan kontak darurat perlu diisi ulang.
6. Setelah ride disimpan, jalankan `dumpsys` di `docs/testing/field-tests.md` (L11, L13), ditambah `adb shell dumpsys sensorservice` (tidak ada listener milik app).
