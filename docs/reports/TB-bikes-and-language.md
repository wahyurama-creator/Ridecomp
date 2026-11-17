# Feature Report — TL.1 + TB.1: Bahasa aplikasi & sepeda tersimpan

**Tanggal:** 2026-11-17
**Branch:** `feature/TL.1-app-language` (TL.1, commit cb88a6d) → `feature/TB.1-bikes` (TB.1, dibuat dari TL.1)
**Status:** Diuji di perangkat, di-merge ke `main`

## 1. Ringkasan
- **TL.1 (D-88):** bahasa dipilih di langkah pertama onboarding dan bisa diganti di Setelan → Bahasa: Bahasa Indonesia, English, atau Ikuti bahasa HP. Berlaku di Android 8–15 tanpa AppCompat.
- **TB.1 (D-89):** sepeda disimpan sekali lalu dipilih sebelum ride. Setiap sepeda punya nama, jenis (= profil aktivitas: Road/Gravel/MTB/Indoor/Commute/kustom), berat, dan keliling roda. Jenis sepeda menentukan halaman data, GPS, dan sport FIT; berat & roda dipakai untuk estimasi power dan sensor kecepatan. Pengguna lama mendapat sepeda pertama otomatis dari data profil lamanya.

## 2. Perubahan per lapisan (TB.1)
| Lapisan | File/kelas | Keterangan |
|---|---|---|
| Domain (`:core`) | `model/Bike.kt`, `RiderProfile.withBike` | Model sepeda; pengendara + sepeda |
| | `repository/SettingsRepositories.kt` `BikeRepository` | Interface |
| | `usecase/BikeUseCases.kt` | Observe pilihan, pilih, ambil terpilih, ambil, simpan (validasi rentang `ProfileDefaults`), hapus (bukan yang terakhir) |
| | `util/NumberText.kt` | `toDecimalOrNull`/`toPlainString` dipindah dari `:feature-settings` agar dipakai ulang |
| Data (`:data`) | `BikeEntity`, `BikeDao`, `BikeMappers`, `BikeRepositoryImpl` | Tabel `bikes`; sepeda terakhir di `settings` `app.last_bike_id` |
| | `RideCompDatabase` v8, `MIGRATION_7_8`, `BikeSeed` | Seed sepeda pertama (migrasi & `onCreate`); skema `8.json` |
| Platform (`:platform`) | `RecordingService.rideSetup`, `RecordingDependencies.getSelectedBike` | Berat sepeda & roda dari sepeda terpilih (juga saat pemulihan) |
| Presentation (`:feature-ride`) | `BikesScreen.kt` (`BikesRoute`, `BikeEditorRoute`, `BikeForm`, `OnboardingBikeForm`), `BikesViewModel.kt` | Daftar "Sepeda saya", formulir tambah/ubah/hapus |
| | `PreRideViewModel`/`PreRideScreen` | Pemilih profil diganti pemilih sepeda + ringkasan + tautan Kelola |
| Presentation (`:feature-settings`) | `ProfileScreen`/`ProfileViewModel` | Field berat sepeda & roda dihapus |
| | `SettingsScreen`/`SettingsViewModel` | Baris "Sepeda saya · n sepeda" |
| | `OnboardingScreen`/`OnboardingViewModel` | Langkah baru BIKE (slot `OnboardingBikeStep` dari `:app`) |
| App (`:app`) | `RideCompNavHost` | Rute `bikes`, `bike_new`, `bike/{id}`; slot onboarding; DI |

## 3. Dependency baru
Tidak ada.

## 4. Resource & siklus hidup
Tidak ada resource berat baru. GPS persiapan ride tetap `WhileSubscribed` dan mengikuti profil dari sepeda terpilih (sepeda Indoor → GPS tidak dinyalakan, diuji di `RideViewModelsTest`).

## 5. Checklist kode
- [x] Clean Architecture: domain murni di `:core`; `:feature-ride` dan `:feature-settings` tidak saling bergantung (form onboarding disisipkan lewat slot di `:app`).
- [x] Satu UseCase = satu aksi; tidak ada logika bisnis di Composable.
- [x] Tanpa magic number/string (konstanta kolom/tabel di `DbNames`, kunci di `SettingsKeys`, string di `strings.xml` en/id).
- [x] `when` eksklusif tanpa `else` untuk sealed; tanpa `!!`.
- [x] Migrasi eksplisit, tanpa destructive migration; data lama dipertahankan.
- [x] Screen stateless + `@ThemePreviews`.

## 6. Hasil testing
- Unit test baru: `BikeUseCasesTest` (6), `BikeViewModelsTest` (5), `MigrationSqlTest` migrasi 7→8 (1); `RideViewModelsTest` pre-ride diperbarui (sepeda Indoor → profil Indoor, GPS tidak dipakai, ride dimulai dengan profil sepeda); `AppModulesTest` memeriksa VM sepeda.
- Test migrasi perangkat (`RideCompMigrationTest`, realme RMX3830 Android 15): **7/7 lulus**, termasuk 7→8 dengan data profil & profil terakhir MTB dan 7→8 tanpa profil → default + Road.
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`: ✅ BUILD SUCCESSFUL.
- Uji perangkat (realme RMX3830, Android 15, build debug dipasang di atas data lama tanpa hapus data):
  - Migrasi v7→v8 saat app dibuka: tanpa crash; Setelan → Sepeda saya berisi "My bike · Road · 9 kg · 2105 mm" (nilai profil lama).
  - Tambah "Trainer" jenis Indoor, berat "12,5" (koma) + 700x28c → tersimpan "Indoor · 12.5 kg · 2136 mm" dan langsung terpilih.
  - Persiapan ride dengan Trainer: GPS "Not used (Indoor)", `dumpsys location` gps provider OFF; ganti ke My bike → GPS mulai (HIGH_ACCURACY); kembali ke Trainer lalu START → ride Indoor dengan tata letak halaman Indoor, GPS tetap OFF; ride uji dibuang, service berhenti.
  - Tautan "Manage" dari persiapan ride → daftar → ubah Trainer → hapus dengan konfirmasi → My bike terpilih lagi; sepeda terakhir tidak punya tombol hapus.
  - Profil pengendara tanpa field berat sepeda & roda.
  - Bahasa: Setelan → Bahasa → Bahasa Indonesia → teks langsung berganti, `cmd locale get-app-locales` = `[id]`; layar sepeda dalam Bahasa Indonesia ("Sepedaku · ✓ Dipakai").
  - Temuan & perbaikan: layar sepeda belum punya tombol kembali → ditambahkan ("Kembali"), diuji di HP.
  - Crash buffer kosong; tidak ada notifikasi LeakCanary selama uji.
  - Belum diuji: langkah onboarding Bahasa & Sepedamu dari awal (butuh hapus data — perlu persetujuan pemilik).

## 7. Keamanan
- §D: tidak ada input luar baru selain teks formulir (nama dipangkas ≤ 30 karakter, angka divalidasi rentang di use case); SQL seed memakai bind argument; tidak ada komponen ter-export, izin, jaringan, atau log data baru; data di Room internal storage (ikut backup terenkripsi seperti tabel lain).
- Android Lint Security: 0 temuan. Secret scan: pre-commit hook.

## 8. Penyimpangan dari spesifikasi / RULE-EXCEPTION
- 12-FEATURE-SPECS §4 menyebut pemilih profil di persiapan ride; kini diganti pemilih sepeda (profil = jenis sepeda), atas keputusan pemilik (D-89). Profil aktivitas tetap bisa diatur di Setelan → Profil aktivitas.

## 9. Risiko & bug yang diketahui
- Jika profil kustom yang dipakai sebuah sepeda dihapus, sepeda itu memakai profil default saat ride sampai jenisnya dipilih ulang.
- Ride yang dipulihkan setelah crash memakai sepeda yang sedang terpilih (bukan kolom di `activities`); bila pengguna mengganti pilihan sepeda di tengah ride lalu app crash, nilai berat/roda bisa berbeda.

## 10. Cara menguji (untuk pemilik proyek)
1. Pasang build debug di atas versi lama (tanpa hapus data): Setelan → Sepeda saya berisi "Sepedaku" dengan berat & roda dari profil lama.
2. Tambah sepeda "Trainer" jenis Indoor → langsung terpilih; buka persiapan ride: GPS "Tidak dipakai", ringkasan "Indoor · … kg · … mm".
3. Pilih sepeda lain di persiapan ride, mulai ride → halaman data mengikuti jenis sepeda.
4. Profil pengendara tidak lagi menampilkan berat sepeda/roda.
5. Bahasa: Setelan → Bahasa → English/Indonesia/Ikuti HP; teks berubah setelah layar dibuat ulang.
6. (Butuh hapus data — perlu persetujuan) Onboarding: Bahasa → Izin → Pengalaman → Profil → **Sepedamu** → Siap.
