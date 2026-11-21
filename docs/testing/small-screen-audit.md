# Audit Layar Kecil (T13.1)

Hasil audit tampilan RideComp di lebar **320 dp** (target terkecil, R1) dibanding **360 dp** (ukuran normal HP uji), sesuai `planning/16-PHASE13-PLAN.md` §6 T13.1. Menentukan isi T13.4–T13.8.

**Tanggal:** 2026-11-21 · **Build:** debug dari `main` `2988f76` (Fase 12 ditutup, T12.3 sudah tergabung).

## 1. Cara uji

| Hal | Nilai |
|---|---|
| HP | Sony Xperia XZ (F8331), Android 8.0, 1080 × 1920 px |
| 360 dp | density bawaan 480 (`wm density reset`) |
| 320 dp | `adb shell wm density 540` |
| Kombinasi | 320 dp: id-gelap, en-gelap, id-terang · 360 dp: id-gelap, en-gelap |
| Layar ride | Ride simulasi (Developer → Simulated GPS), dijeda lalu dibuang; tidak ada ride tersimpan baru |
| Onboarding | Emulator Pixel 9a bersih, `wm density 540` (320 dp): Sambutan, langkah 1–2. Langkah 3–6 diperiksa dari kode (emulator dihentikan atas permintaan pemilik karena laptop melambat) |
| Alat | `adb` + `uiautomator dump` + skrip analisa lokal (elemen keluar layar, teks bertumpuk/menempel < 3 dp, target sentuh < 48 dp), lalu tiap screenshot diperiksa visual. Skrip & PNG tidak di-commit (bahan T13.2). **Koreksi review:** batas 48 dp lebih longgar dari UI-07 (56 dp); target 48–55 dp tidak tersaring, jadi ditambah cek statis di kode (§3a). Skrip T13.2 memakai 56 dp |
| Total | ±230 screenshot, 22 dari 25 `*Screen.kt` + onboarding + dialog buang ride |

Prioritas: **P1** rusak/tidak bisa dipakai · **P2** jelek tapi bisa dipakai (teks terpotong tanpa jalan keluar, aksi utama tersembunyi) · **P3** kosmetik.

## 2. Ringkasan

- **P1: tidak ada.** Semua layar bisa dipakai di 320 dp; semua aksi bisa dicapai (langsung atau dengan scroll).
- **P2: 4 temuan** (Rute kosong, ringkasan bulan di Riwayat, tombol SELESAI di layar jeda, tombol Sambutan).
- **P3: 11 temuan**, sebagian besar teks turun 2 baris atau isi baru terlihat setelah scroll.
- 360 dp: tidak ada masalah baru dibanding kondisi sekarang (dua catatan P3 kosmetik di §4 tetap tidak diubah, R1).
- Bahasa Inggris tidak lebih buruk daripada Indonesia; teks Indonesia umumnya lebih panjang. Tema terang tidak menambah masalah tata letak.
- **R4 tidak perlu dibuka** (tidak ada kerusakan parah yang terkait tinggi layar atau skala font).

## 3. Temuan per layar (320 dp)

| # | Layar (file) | Temuan | Prio | Task |
|---|---|---|---|---|
| 1 | Rute, kosong (`RoutesScreen`) | Teks "Belum ada rute. Import file GPX untuk ride mengikuti **rute.**" terpotong oleh tombol Import GPX dan **tidak bisa di-scroll**. Versi Inggris (lebih pendek) muat | **P2** | T13.3 (perbaikan di `MessageState`), dicek ulang di T13.7 |
| 2 | Riwayat › Aktivitas (`HistoryScreen` `MonthCard`) | Nilai "0,00 km" menempel ke kolom RIDE ("0,00 km1", jarak < 3 dp). `Row` 3 kolom `weight(1f)` tanpa jarak; jarak bulanan 4 digit kemungkinan bertumpuk | **P2** | T13.6 |
| 3 | Layar jeda (`RideScreen`) | Tombol **SELESAI** terpotong di tepi bawah; "Buang ride" hanya terlihat setelah scroll. Aksi utama saat berhenti di jalan | **P2** | T13.4 |
| 4 | Sambutan onboarding (`OnboardingScreen` `WelcomeStep`) | Tombol **Get started / Mulai** baru terlihat setelah 2× scroll; judul besar + kartu contoh memakan layar (sesuai cek awal 2026-11-19) | **P2** | T13.5 |
| 5 | Riwayat › Latihan, kosong | Teks "…power meter dengan FTP." terpotong di awal; **bisa di-scroll** | P3 | T13.6 |
| 6 | Persiapan ride (`PreRideScreen`) | "Butuh akurasi ≤ 30 m" dan kartu "Biar lebih cepat" baru terlihat setelah scroll (cincin GPS besar). Tombol "Mulai tanpa menunggu" tetap terlihat | P3 | T13.4 |
| 7 | Ringkasan ride (`SummaryScreen`) | "max 31,8 km/h" pecah menjadi "km/" + "h"; "W · estimasi dari kecepatan" 3 baris di kolom sempit | P3 | T13.4 |
| 8 | Ringkasan ride | Saat dibuka, kolom Nama aktivitas fokus dan keyboard muncul, menutupi angka (terlihat di 360 dp juga). **Tidak disengaja**: `SummaryScreen` tidak meminta fokus; dugaan fokus tertahan saat activity dibuat ulang (density diubah) | P3 | T13.4 (cek) |
| 9 | Sensor (`SensorsScreen`) | Area daftar sempit (±920 px); catatan ANT+ dan tautan "Cara menyambungkan detak jantung" baru terlihat setelah scroll di dalam daftar. Nama jam turun 2 baris (tombol hapus tetap ≥ 56 dp) | P3 | T13.7 |
| 10 | Setelan (`SettingsScreen`) | "Profil aktivitas", "Backup & export data", "Jeda & lanjut otomatis", "Keselamatan & kontak darurat" turun 2 baris karena nilai di kanan | P3 | T13.8 |
| 11 | HP saya mematikan app (`BatteryHelpScreen`) | Tombol "BANTUAN LAIN UNTUK HP KAMU" 2 baris | P3 | T13.8 |
| 12 | Kartu tips (Backup, Rute, Detail ride) | Kolom teks sempit karena ikon + tombol tutup; judul turun 2 baris | P3 | T13.3/T13.8 |
| 13 | Keselamatan (`SafetyScreen`) | Kolom "Nama kontak" dan "Nomor HP" berdampingan, sempit | P3 | T13.8 |
| 14 | Profil (`ProfileScreen`) | Label "TAHUN LAHIR (OPSIONAL)" / "HR ISTIRAHAT (OPSIONAL)" turun 2 baris; masih rapi | P3 | T13.8 |
| 15 | Detail ride, kartu tips | `uiautomator` melaporkan tombol tutup tips 56 × 34 dp. **Koreksi review:** di kode tombol ini 56 × 56 dp (`TipCard.kt:70`, `touchTargetMin`); 34 dp kemungkinan hanya bagian yang terlihat dari kartu yang terpotong tepi scroll (`uiautomator` melaporkan batas yang terlihat). Dicek ulang dengan skrip T13.2 | P3 (cek) | T13.2 |

**Tanpa masalah di 320 dp:** Beranda (4 kartu status, MULAI RIDE ≥ 88 dp), layar ride (4 halaman data, angka utama muat, PAUSE/LAP ≥ 72 dp), Riwayat › Rekor/Segmen kosong, Detail ride (statistik 3 kolom, grafik, ekspor), Sepeda saya, Profil aktivitas, Ride/layar & baterai, Halaman data, Peta, Tampilan, Bahasa, Backup & restore, Strava, Pembaruan aplikasi, Lisensi, Bantuan & panduan (+ topik), dialog "Buang ride ini?", navigasi bawah (5 label muat), onboarding langkah 1 (bahasa) & 2 (izin).

## 3a. Cek statis target sentuh < 56 dp (tambahan review, 2026-11-21)

Elemen yang bisa diketuk dengan ukuran tetap di bawah UI-07, dicari di kode `src/main`. Semuanya **sama di semua lebar** (bukan masalah layar kecil) dan sudah ada sebelum Fase 13, jadi tidak diubah di fase ini (tampilan ≥ 360 dp harus tetap, R1); dicatat sebagai utang UI-07.

| Elemen | Lokasi | Ukuran | Status |
|---|---|---|---|
| Swatch palet Poster di layar share | `ShareCardUi.kt:307` (`swatchSize`) | 32 dp | Pelanggaran UI-07 lama; di luar Fase 13 (galeri share belum diaudit, §7) |
| Tab segmen (Riwayat, Latihan, dll.) | `Navigation.kt:150` (`chipHeight`) | tinggi 48 dp | Lama, lebar penuh; di luar Fase 13 |
| `BigButtonStyle.COMPACT` | `BigButton.kt:82` | tinggi 48 dp | Disengaja: hanya aksi halaman share, di luar layar ride |
| Tombol "Connect with Strava" | `StravaScreen.kt:227` | tinggi 48 dp | Disengaja: pedoman brand Strava (D-85) |

Toggle 56 × 34 dp tidak termasuk: seluruh baris `SwitchRow` yang bisa diketuk.

**Cek perangkat dengan batas 56 dp (2026-11-21, `targets.py`, realme 320 & 360 dp):** selain di atas, juga < 56 dp di **semua lebar**: chip pilihan di Profil aktivitas (68 × 48 dp), baris pilihan Bahasa (tinggi 48 dp), kotak halaman di editor Halaman data (lebar 47 dp). Tidak ada yang hanya muncul di 320 dp. Utang UI-07 lama, di luar Fase 13 (R1).

## 4. Catatan di 360 dp (tidak diubah, R1)

- Kartu ANT+ di Sensor dan kartu tips di Persiapan ride terpotong tepat di atas tombol bawah tanpa tanda bisa di-scroll (P3 kosmetik). Dicatat saja; tampilan ≥ 360 dp tidak boleh berubah di fase ini.

## 5. Penyebab bersama (untuk T13.3)

| Penyebab | Lokasi | Dampak |
|---|---|---|
| `MessageState` (dipakai `EmptyState`/`ErrorState`) = `Column(fillMaxSize)` **tanpa scroll**, gaya `title` | `ui-common/.../component/StateViews.kt:31` | Temuan #1 (dan semua layar kosong lain dengan teks panjang). Satu perbaikan di komponen bersama |
| Baris statistik `Row` + `weight(1f)` tanpa `spacedBy` | `feature-history/.../HistoryScreen.kt:187` | Temuan #2 |
| Baris setelan label kiri + nilai kanan tanpa batas lebar nilai | `SettingsScreen` | Temuan #10 (P3; bisa jadi "nilai pindah ke bawah label" di `COMPACT`, sesuai T13.8) |

## 6. Di luar cakupan ukuran layar (bug terpisah)

1. **Format angka sesaat memakai titik.** Setelah density diubah di tengah rekaman (activity dibuat ulang), satu screenshot layar ride menampilkan "0.57" dan "25.5"; ±1 detik kemudian kembali "0,58" dan "25,4". Dugaan: satu frame memakai locale sistem (en-US) sebelum locale aplikasi (id) diterapkan. Perlu verifikasi (P3).
2. **Tab Setelan dari sub-layar.** Mengetuk "Setelan" di navigasi bawah saat berada di sub-layar (mis. Tampilan) tidak kembali ke daftar Setelan; tab juga tidak tersorot di sub-layar. Perilaku navigasi, bukan ukuran (P3).

Keduanya **tidak dikerjakan di Fase 13** (bukan ukuran layar, CLAUDE.md prinsip 3); dicatat sebagai bug terpisah untuk fase berikutnya (D-141).

Tidak ada crash, StrictMode, atau leak selama audit; mengubah density 320 ↔ 360 dp saat merekam tidak menghentikan rekaman. Tidak ada GPS/foreground service tersisa setelah ride simulasi dibuang.

## 7. Belum dicakup

| Layar | Alasan | Rencana |
|---|---|---|
| Detail rute (`RouteDetailScreen`) | Butuh file GPX di HP uji | T13.7 (impor GPX contoh) |
| Editor komponen sepeda (`ComponentScreen`), form sepeda onboarding | Tidak dibuka di audit ini | T13.7/T13.5 |
| Onboarding langkah 3–6 (level, profil, sepeda, siap) | Emulator dihentikan atas permintaan pemilik; diperiksa dari kode: kerangka langkah sama dengan langkah 1–2 (isi scroll + tombol Lanjut tetap), profil memakai `ProfileForm` yang sama dengan layar Profil (#14) | T13.5 (uji visual) |
| Layar insiden (hitung mundur) | Butuh deteksi insiden aktif + kontak | T13.4 |
| `StravaHelpScreen`, galeri kartu share, editor segmen | Tidak dibuka di audit ini | T13.6 |

## 8. Isi task berikutnya (usulan)

- **T13.3:** `MessageState` bisa di-scroll (perilaku 360 dp sama); token `Compact` untuk judul layar kosong, padding kartu tips.
- **T13.4:** layar jeda: tombol SELESAI terlihat penuh di 320 dp; Persiapan ride: cincin GPS lebih kecil di `COMPACT`; Ringkasan: satuan tidak terpecah.
- **T13.5:** Sambutan: tombol Mulai terlihat tanpa scroll jauh.
- **T13.6:** `MonthCard` diberi jarak antar kolom + angka tidak bertabrakan.
- **T13.7:** cek ulang Rute kosong (diperbaiki di T13.3); area daftar Sensor.
- **T13.8:** baris Setelan dengan nilai panjang; kolom kontak Keselamatan.
