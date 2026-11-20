# 02 — Design Specification (UI/UX)

## 1. Prinsip desain

1. **Terbaca dalam 1 detik.** Angka besar, kontras tinggi, label kecil. Pengguna hanya melirik saat mengayuh.
2. **Gelap secara default.** Latar hitam pekat (#000) menghemat baterai di layar AMOLED dan mengurangi silau.
3. **Sentuhan minimal saat ride.** Aksi penting bisa dilakukan dengan satu tap besar atau tombol volume.
4. **Ringan.** Tanpa animasi dekoratif, blur, bayangan, atau gradien di layar ride.
5. **Bisa dipakai bersarung tangan, berkeringat, dan di bawah matahari.**

## 2. Peta layar

```
Splash → (pertama kali) Onboarding & Izin → Home
Home
 ├─ Mulai Ride → Pre-ride check → Layar Ride (halaman data) → Ringkasan → Simpan/Buang
 ├─ Rute → Daftar rute → Detail rute (peta + profil elevasi) → "Ride dengan rute ini"
 ├─ Riwayat → Detail aktivitas → Export FIT/GPX / Upload Strava (opsional)
 ├─ Sensor → Scan & pairing → Detail sensor
 └─ Pengaturan → Profil, Halaman data, Layar & baterai, Peta offline, Satuan, Bahasa, Strava (opsional), Bantuan baterai per merek HP
```

## 3. Spesifikasi layar

### 3.1 Onboarding & izin
- **Sejak T10.12 (D-105):** layar **Sambutan** dulu (logo, "Ubah HP-mu jadi cycling computer.", kartu contoh layar ride, 3 sorotan fitur, tombol Mulai; muncul dengan fade/slide sekali). Langkah: Bahasa → Izin → Level → Profil → Sepeda → Siap ("Langkah n dari 6"), header berikon dalam lingkaran aksen, tombol utama menempel di bawah. **Level, Profil, Sepeda bisa "Lewati"** (nilai bawaan dipakai; level = Menengah, berat 70 kg, sepeda Road 700x25c); profil yang belum diisi diingatkan lewat kartu di layar Siap berangkat ("Lengkapi profilmu · Isi sekarang", bisa ditutup). Bahasa & izin lokasi tetap wajib.
- 3 langkah singkat: (1) izin lokasi "saat app digunakan" + penjelasan, (2) izin Bluetooth (Android 12+: Nearby devices) dan notifikasi (Android 13+), (3) pengecualian optimasi baterai + tautan panduan pengaturan merek HP terdeteksi (`Build.MANUFACTURER`).
- Isi profil dasar: berat badan, berat sepeda, keliling roda (default 2105 mm untuk 700x25c, dengan daftar ukuran umum), HR max (opsional), FTP (opsional).

### 3.2 Home
- Tombol besar **MULAI RIDE** (tinggi ≥ 72dp).
- Status bar mini: GPS (mencari/siap + akurasi), sensor tersambung (ikon HR, cadence, power, dengan indikator baterai sensor).
- Rute aktif (bila dipilih) + tombol hapus.
- Ringkasan ride terakhir.
- Banner pemulihan bila ada ride yang belum selesai: "Ride belum tersimpan ditemukan, lanjutkan / simpan / buang".

### 3.3 Pre-ride check
Tampil singkat sebelum ride dimulai: status GPS fix (wajib ada fix atau pilih "mulai tanpa menunggu"), sensor terhubung, sisa baterai HP. Tombol **MULAI**.

### 3.4 Layar ride (inti aplikasi)

**Struktur:**
- Bar atas tipis (24dp): jam, ikon GPS, ikon sensor, baterai HP, indikator REC/PAUSE.
- Area halaman data: digeser horizontal (`HorizontalPager`). Default 4 halaman:
  1. **Utama:** Speed (besar), Jarak, Waktu bergerak, HR, Cadence, Power 3s
  2. **Peta/Navigasi:** peta + 2 field (jarak tersisa, speed); di mode breadcrumb tanpa tile
  3. **Tanjakan:** Grade, Elevasi, Total tanjakan, Profil elevasi (bila ada rute)
  4. **Lap:** Waktu lap, Jarak lap, Avg speed lap, Avg power lap, Avg HR lap
- Bar bawah (64dp): tombol **PAUSE/RESUME** (kiri, besar), **LAP** (kanan). Stop hanya dari layar pause (mencegah salah tekan).

**Layout data field (grid):**

| Jumlah field | Layout |
|---|---|
| 1 | 1 field penuh |
| 2 | 1 kolom x 2 baris |
| 3 | 1 besar atas + 2 di bawah |
| 4 | 2 x 2 |
| 6 | 2 x 3 (default halaman utama; field pertama boleh lebar penuh) |
| 8 | 2 x 4 |

**Anatomi satu field:**
```
┌────────────────────┐
│ SPEED        km/h  │  ← label 12–14sp, warna sekunder, huruf kapital
│                    │
│      32.4          │  ← nilai: auto-fit, minimum 32sp, angka tabular (monospace digit)
└────────────────────┘
```
- Nilai tidak tersedia ditampilkan `--` (bukan 0).
- Field sensor yang putus: nilai `--` + label berwarna peringatan.
- Field HR/power boleh berwarna latar sesuai zona (opsi, default mati untuk hemat baterai).

**Kontrol tambahan:**
- Tap 2x di area data: tampilkan/sembunyikan bar status atas saja. **Tombol PAUSE/LAP selalu tampil** (D-101: pengguna bingung saat tombol berhenti hilang).
- Long-press field: ganti metrik field (opsional di Fase 2+).
- Tombol volume (opsional, setting): Volume Up = Lap, Volume Down = Pause/Resume.
- Peringatan (keluar jalur, sensor putus, auto-pause): banner atas 3 detik + getar, tidak memblokir layar.

### 3.5 Layar pause
Menampilkan ringkasan sementara + 3 tombol: **LANJUTKAN** (besar), **SELESAI & SIMPAN**, **BUANG** (konfirmasi 2 langkah).

### 3.6 Ringkasan setelah ride
Jarak, waktu bergerak, avg/max speed, total tanjakan, avg/max HR, avg/max/NP power, avg cadence, kalori; peta kecil track (render statis sekali); grafik speed/HR/elevasi. Nama aktivitas (default: "Gowes Pagi/Siang/Sore/Malam"). Tombol: **Simpan**, **Export FIT**, **Upload ke Strava (panduan manual)**.

### 3.7 Riwayat & detail aktivitas
Daftar berurutan terbaru (lazy load, 20 per halaman). Detail: ringkasan, grafik (speed, HR, cadence, power, elevasi vs jarak), lap, tombol export/hapus.

### 3.8 Sensor
- Tombol **Scan**: daftar perangkat BLE yang mengiklankan service HR (0x180D), CSC (0x1816), atau CP (0x1818); tampilkan nama, tipe, sinyal (RSSI).
- Sensor tersimpan: nama, tipe, baterai (%), status, tombol lupakan.
- Detail sensor CSC/CP: pengaturan keliling roda; untuk power meter, info "kalibrasi dilakukan via app pabrikan".

### 3.9 Rute
Import GPX (file picker + intent "Buka dengan"). Detail rute: peta, jarak, total tanjakan, profil elevasi, tombol "Ride dengan rute ini", "Balik arah rute".

### 3.10 Pengaturan
Profil; editor halaman data (tambah/hapus halaman, pilih jumlah field & metrik); Layar & baterai (mode layar, kecerahan); Auto-pause (on/off, ambang); Auto-lap (off / tiap X km); Peta offline (unduh/hapus area, ruang terpakai); Satuan; Bahasa; Strava (Fase 5); "HP saya mematikan app" (panduan per merek); Ekspor/backup database.

### 3.11 Layar & elemen tambahan (detail perilaku: `12-FEATURE-SPECS.md`)
- **Jeda otomatis:** label besar "JEDA OTOMATIS" warna `warn` di atas data, angka tetap terlihat; getar pendek saat masuk/keluar. Diam > 15 mnt → kartu "Selesai ride?".
- **Banner pengingat minum/makan:** ikon + teks besar + tombol **Sudah** & **Tunda 5 mnt** (≥ 56dp).
- **Banner mode baterai:** "Mode hemat aktif · 19%" (warn) / "Mode kritis · 9%" (danger).
- **Pemilih profil aktivitas** di Pre-ride check (chip: Road, Gravel, MTB, Indoor, Commute).
- **Layar Keselamatan** (Pengaturan): toggle deteksi insiden, daftar kontak darurat (maks 3, tambah via contact picker), nama pengendara, tombol "Kirim SMS uji", penjelasan keterbatasan fitur.
- **Layar hitung mundur insiden:** layar penuh latar `danger`, angka hitung mundur sangat besar, tombol putih raksasa "SAYA BAIK-BAIK SAJA" (≥ 50% lebar & tinggi ≥ 96dp), tombol sekunder "KIRIM SEKARANG". Setelah terkirim: status pengiriman per kontak + tombol "Saya aman".
- **Tombol SOS manual** di layar pause (tekan lama 3 dtk, dengan indikator progres).
- **Halaman tanjakan (ClimbPro):** "Tanjakan 2/4", sisa jarak & gain besar, grade saat ini, profil tanjakan berwarna per pita grade dengan angka grade tertulis; di luar tanjakan: "Tanjakan berikutnya 3,4 km".
- **Banner belokan:** panah arah besar + "Belok kiri tajam · 150 m"; field data "Belokan berikutnya".
- **Banner segmen:** "Segmen dimulai: [nama]" → field Segmen (waktu, sisa jarak, ±detik vs PR) → hasil "PR baru!".
- **Layar Rekor:** tab per profil aktivitas; kartu PR per kategori.
- **Layar Segmen:** daftar segmen + PR; detail segmen dengan 5 effort terbaik.
- **Layar Latihan:** grafik Fitness/Kelelahan/Form (garis), ringkasan minggu ini, distribusi zona; label "estimasi" + metode.
- **Editor kartu share:** pratinjau Story/Kotak, pilih hingga 4 statistik, toggle "Sembunyikan awal & akhir rute" (default aktif), toggle tanpa jejak, tema gelap/terang, tombol Bagikan.
- **Navigasi bawah** bertambah akses ke Latihan (Rekor, Segmen, Tren) di tab Riwayat agar tetap 5 tab.

### 3.12 Backup & restore
Layar di Pengaturan: status "Backup terakhir: 12 Okt 2026 · 48 aktivitas", toggle "Lindungi dengan kata sandi" (rekomendasi aktif) + field kata sandi & konfirmasi, tombol **Buat backup** (progres), tombol **Restore dari file** (konfirmasi 2 langkah, peringatan "data saat ini akan diganti; backup darurat dibuat otomatis"). Kartu pengingat di Home bila backup > 30 hari.

### 3.13 State kosong, menunggu & error
Setiap layar data punya state berikut (wajib ada di Preview, UI-06):
- **Kosong:** ilustrasi garis sederhana + kalimat jelas + satu aksi (mis. Riwayat kosong → "Belum ada ride. Mulai ride pertamamu" + tombol MULAI RIDE; Rute kosong → "Import GPX").
- **Menunggu:** GPS belum fix di pre-ride → indikator statis (tanpa animasi berat), info akurasi, tips ("cari area terbuka"), tombol **Mulai tanpa menunggu** (rekaman dimulai, posisi menyusul).
- **Error/peringatan saat ride:** sensor putus → field menampilkan `--` dengan label warna `warn` + banner "HR Strap terputus · menyambung ulang…"; izin dicabut/GPS mati → banner `danger` dengan aksi perbaikan. Tidak pernah layar penuh yang menutupi data (kecuali insiden).
- **Gagal operasi** (export, backup, restore, import GPX): pesan jelas penyebabnya + aksi coba lagi; tanpa stack trace.

## 4. Design system

### 4.1 Warna (tema gelap, default)

| Token | Hex | Pemakaian |
|---|---|---|
| `bg` | `#000000` | Latar layar ride |
| `surface` | `#121212` | Kartu, list di luar layar ride |
| `divider` | `#2A2A2A` | Garis antar field |
| `textPrimary` | `#FFFFFF` | Nilai data |
| `textSecondary` | `#A0A0A0` | Label, satuan |
| `accent` | `#FC6A1E` | Tombol utama, garis rute |
| `track` | `#2FA8FF` | Jejak yang sudah dilalui |
| `ok` | `#3DDC84` | Status tersambung/GPS siap |
| `warn` | `#FFC400` | Sensor putus, auto-pause |
| `danger` | `#FF4D4D` | Keluar jalur, rekaman berhenti |

Warna zona (HR/power) 1–5/7: abu, biru, hijau, kuning, oranye, merah, ungu.

Token tambahan dari desain canvas (`surfaceAlt`, `surfaceRaised`, `textTertiary`, `disabled`, `indicatorInactive`, `accentTint`, `okTint`, `warnTint`, `dangerDeep`, pita grade) ada di `13-UI-SCREENS.md` §1.2 dan `RideCompColors` (D-54).

Tema terang opsional (untuk layar LCD di bawah matahari terik): data ride ≥ 7:1, teks lain ≥ 4,5:1, garis/batang/ikon ≥ 3:1; dikunci oleh `ThemeContrastTest` (T11.10, D-127). Widget mengikuti mode HP.

| Token | Gelap | Terang |
|---|---|---|
| `bg` | `#000000` | `#FFFFFF` |
| `surface` | `#121212` | `#F2F2F2` |
| `divider` | `#2A2A2A` | `#BDBDBD` |
| `textPrimary` | `#FFFFFF` | `#000000` |
| `textSecondary` | `#A0A0A0` | `#4A4A4A` |
| `accent` (tombol) | `#FC6A1E` teks hitam | `#B3390A` teks putih (T11.10) |
| `warn` | `#FFC400` | `#755200` (`warnText`: teks, ikon, cincin) / `#FFC400` (latar blok/banner, teks hitam) |
| `danger` | `#FF4D4D` | `#B3261E` |
| `ok` | `#3DDC84` | `#17683A` |
| `track` | `#2FA8FF` teks hitam | `#0B6BB8` teks putih (T11.10) |
| Zona 1–7 | abu, biru, hijau, kuning, oranye, merah, ungu | sama, versi pekat ≥ 3:1 (`#757575 #1E78D2 #2E8B3E #A87400 #D9530F #C62828 #8E44C9`) |
| Pita grade 1–5 | kuning muda → merah | `#948200 #C27C00 #D25E00 #C8431A #8C2410` |

Pilihan tema: Gelap (default) / Terang / Ikuti HP, satu untuk seluruh aplikasi di Setelan → Tampilan (T11.9, D-110, D-126; bukan per profil aktivitas). Status bar & navigation bar mengikuti tema aplikasi. Tetap gelap: hitung mundur insiden; kartu share punya pilihan sendiri.

### 4.2 Tipografi
- Font angka: font sistem dengan `fontFeatureSettings = "tnum"` (angka tabular) agar angka tidak "goyang".
- Nilai data: auto-fit ke ukuran sel, minimum 32sp, maksimum 120sp.
- Label: 12–14sp, kapital, letter-spacing 0.5.
- Tidak memakai font kustom yang diunduh (hemat ukuran APK).

### 4.3 Komponen
`DataField`, `DataPage`, `RideControlBar`, `StatusBar`, `AlertBanner`, `SensorTile`, `BigButton` (≥ 56dp, utama ≥ 72dp), `ElevationProfile` (Compose `Canvas` ringan), `RouteMap`.

### 4.4 Ikon
Material Symbols/Icons secukupnya sebagai vector drawable. Jangan memakai dependency `material-icons-extended` utuh (besar); salin hanya ikon yang dipakai.

## 5. Daftar metrik yang tersedia untuk data field

| Kategori | Metrik |
|---|---|
| Kecepatan | Speed, Avg speed, Max speed, Lap avg speed |
| Jarak | Jarak, Jarak lap, Jarak tersisa (rute) |
| Waktu | Waktu bergerak, Waktu total, Waktu lap, Jam, ETA (rute) |
| Elevasi | Elevasi, Total tanjakan, Total turunan, Grade, Sisa tanjakan (rute) |
| Heart rate | HR, Avg HR, Max HR, Zona HR, % HR max, Lap avg HR |
| Cadence | Cadence, Avg cadence, Lap avg cadence |
| Power | Power, Power 3s, Power 10s, Avg power, Max power, NP, Zona power, W/kg, Lap avg power, Estimated power |
| Navigasi | Belokan berikutnya, Tanjakan saat ini (sisa jarak/gain), Tanjakan berikutnya |
| Segmen | Waktu segmen, Sisa segmen, Selisih vs PR |
| Lain | Kalori, Suhu (bila sensor/HP mendukung), Baterai HP, Waktu ke pengingat minum |

## 6. Aturan hemat baterai di UI

- Mode layar (setting): **Selalu nyala** / **Redup otomatis** (redup setelah 15 detik tanpa sentuhan, nyala kembali saat ada peringatan, lap, atau tap) / **Mati** (data utama di notifikasi foreground).
- Update data maksimum 1 Hz; tiap field meng-collect state miliknya sendiri agar hanya field yang berubah yang di-recompose.
- Peta: frame rate dibatasi; kamera mengikuti posisi tiap 1–2 detik, bukan animasi kontinu; rotasi peta "north-up" default (lebih ringan).
- Grafik riwayat dirender sekali menjadi gambar atau dengan downsampling (maks ~1000 titik).
