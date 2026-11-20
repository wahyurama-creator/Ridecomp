# 12 — Spesifikasi Fitur Tambahan

Fitur-fitur ini dipilih pemilik proyek dengan referensi Garmin Edge, Wahoo, dan Strava, disesuaikan agar **berjalan offline, tanpa server, dan ringan di HP low-end**. Semua ambang di bawah adalah **nilai default yang bisa diatur** (KT-06) dan harus divalidasi lewat uji lapangan; ubah dengan persetujuan bila hasil uji menunjukkan nilai lain lebih baik.

| ID | Fitur | Fase | Detail |
|---|---|---|---|
| AP | Jeda & lanjut otomatis (auto-pause/resume) | 1 | §1 |
| RM | Pengingat minum & makan | 1 | §2 |
| BM | Mode baterai kritis otomatis | 1 | §3 |
| PR-F | Profil aktivitas (Road, Gravel, MTB, Indoor, Commute, Sepeda lipat) | 2 | §4 |
| INC | Deteksi insiden/jatuh + SMS darurat | 2 | §5 |
| CL | Info tanjakan otomatis (ala ClimbPro) | 3 | §6 |
| TW | Peringatan belokan | 3 | §7 |
| BE | Personal record & best efforts | 4 | §8 |
| SG | Segmen pribadi | 4 | §9 |
| TL | Beban latihan (fitness, kelelahan, form) | 4 | §10 |
| SC | Kartu share WA/IG | 4 | §11 |
| BK | Backup & restore data | 2 | §12 |
| AN | Analisa ride di detail (kecepatan, elevasi, HR, zona power Z1–Z7) | 11 | `14-PHASE11-PLAN.md` §5 (T11.1–T11.6) |
| UT | Usaha terbaik otomatis & bagian Hasil | 11 | `14-PHASE11-PLAN.md` §5 (T11.7–T11.8) |
| TM | Tema terang/gelap | 11 | `14-PHASE11-PLAN.md` §5 (T11.9–T11.10) |
| PO | Peta tanpa internet (cache, unduh rute/area) | 11 | `14-PHASE11-PLAN.md` §5 (T11.11–T11.14) |
| SW | Detak jantung dari smartwatch (broadcast HR, Wear OS, jam tangan eksperimental) | 12 | `15-PHASE12-PLAN.md` §5 (T12.1–T12.5) |

---

## 1. Jeda & lanjut otomatis (AP)

**Tujuan:** saat pengendara berhenti (lampu merah, warung, istirahat), rekaman otomatis dijeda sehingga waktu bergerak dan kecepatan rata-rata akurat, lalu otomatis lanjut saat bergerak lagi, tanpa menyentuh HP.

### 1.1 Perilaku
- Setting (per profil aktivitas, §4): **Aktif/Nonaktif** (default aktif), **ambang kecepatan** (default 2 km/h), **jeda setelah diam selama** (pilihan 3 / 5 / 10 / 30 detik; default 5 detik).
- **Auto-pause:** kecepatan < ambang **dan** tidak ada perpindahan berarti selama durasi yang dipilih → status `autoPaused`.
- **Auto-resume:** kecepatan > ambang + 1 km/h (histeresis) selama ≥ 2 detik **dan** perpindahan nyata → status `recording`.
- Hanya berlaku dari status `recording`/`autoPaused`. **Pause manual tidak pernah di-resume otomatis.**
- Saat `autoPaused`:
  - Layar ride menampilkan label besar **"JEDA OTOMATIS"** (warna `warn`), angka tetap terlihat.
  - Getar pendek saat masuk & keluar jeda (bisa dimatikan); bunyi opsional.
  - Waktu bergerak berhenti; waktu total tetap berjalan; jarak tidak bertambah.
  - Notifikasi foreground menampilkan "Jeda otomatis".
- **Diam lama:** jika `autoPaused` > 15 menit (setting), tampilkan pertanyaan "Selesai ride?" (Simpan / Lanjutkan). **Tidak pernah menghentikan ride otomatis.** GPS diturunkan ke interval 5 detik (BAT-03) sampai bergerak lagi.

### 1.2 Sumber deteksi
| Kondisi | Sumber kecepatan | Catatan |
|---|---|---|
| Sensor roda (CSC) aktif | Sensor roda | Paling akurat & responsif: roda berhenti = 0 dalam ≤ 3 dtk |
| Tanpa sensor roda | GPS | Wajib cek perpindahan: jarak dari titik jeda > max(akurasi GPS, 10 m) sebelum resume, agar **GPS drift saat diam tidak memicu resume palsu** |
| Indoor (§4) | Sensor roda saja | Tanpa GPS |

Cadence **tidak** dipakai untuk resume (pengendara bisa mengayuh di tempat atau trackstand).

### 1.3 Data
- Event `auto_pause` / `auto_resume` dicatat; di FIT ditulis sebagai event timer stop/start.
- Uji: lampu merah, diam dengan GPS drift, tanjakan sangat pelan (4–5 km/h tidak boleh dijeda dengan ambang 2), dorong sepeda.

---

## 2. Pengingat minum & makan (RM)

- Setting per profil: **Minum** tiap X menit (default 20), **Makan** tiap Y menit (default 45), masing-masing bisa nonaktif; opsi berbasis jarak (tiap N km) sebagai alternatif.
- Dihitung berdasarkan **waktu bergerak** (tidak berjalan saat jeda).
- Saat waktunya: banner atas (ikon botol/makanan) + getar 2x + bunyi opsional; layar dinyalakan sejenak bila mode redup.
- Banner punya tombol **"Sudah"** (reset hitungan) dan **"Tunda 5 menit"**; tanpa respons → hilang setelah 10 detik dan diingatkan lagi 5 menit kemudian (maks 2x).
- Ringkasan ride menampilkan jumlah pengingat yang dikonfirmasi (opsional).
- Tidak memakai AlarmManager; pengingat dihitung di pipeline perekaman (BAT-05).

---

## 3. Mode baterai kritis otomatis (BM)

Tujuan utama: **ride tidak pernah hilang karena HP mati**.

| Level baterai HP | Mode | Tindakan |
|---|---|---|
| ≤ 20% (setting) | **Hemat** | Banner + getar sekali. Mode layar dipaksa "Redup otomatis" (jika sebelumnya "Selalu nyala"). Peta beralih ke mode breadcrumb. Pengumuman di notifikasi |
| ≤ 10% | **Kritis** | Layar dipaksa mode "Mati" (data di notifikasi). GPS tetap 1 Hz saat bergerak (kualitas rekaman diutamakan), 5 dtk saat jeda. Barometer & sensor non-esensial dimatikan |
| ≤ 5% | **Darurat** | Checkpoint penuh ke DB + **buat file FIT sementara** dari data sejauh ini, sehingga bila HP mati, ride tetap bisa dipulihkan/diekspor. Rekaman tetap berjalan |
| Sedang mengisi daya | — | Mode kembali ke pilihan pengguna |

- Level dibaca dari `BatteryManager` (sticky broadcast `ACTION_BATTERY_CHANGED` dibaca saat tick, bukan receiver permanen) maksimal tiap 30 detik.
- Pengguna dapat menonaktifkan mode Hemat (bukan Kritis/Darurat checkpoint).
- Setiap perubahan mode dicatat sebagai event.

---

## 4. Profil aktivitas (PR-F)

Profil bawaan: **Road**, **Gravel**, **MTB**, **Indoor**, **Commute**, **Sepeda lipat** (skema v10, D-102). Pengguna bisa mengubah dan menduplikasi profil.

Setiap profil menyimpan: halaman data (konfigurasi `05-DATA-MODEL.md` §2), setting auto-pause (§1), pengingat (§2), auto-lap, mode layar default, permukaan (Crr) & posisi (CdA) untuk estimated power, sensor yang dipakai, dan `sport/sub_sport` FIT.

| Profil | Khusus |
|---|---|
| Road | Default; Crr road |
| Gravel | Crr gravel; ambang auto-pause sama |
| MTB | Crr lebih tinggi; halaman data menonjolkan elevasi & HR |
| Indoor | **GPS mati**; speed & jarak dari sensor roda; auto-pause dari sensor roda; FIT tanpa posisi, sub_sport indoor |
| Commute | Halaman data sederhana (speed, jarak, jam); bisa ditandai commute saat upload Strava (Fase 5) |
| Sepeda lipat | Posisi tegak, permukaan jalan, halaman data umum; sepeda baru berjenis ini mulai dengan roda 20x1.75 (1.515 mm); daftar roda memuat 16"/20" (305, 349, 406, 451). FIT sub_sport generic (D-102) |

- Profil dipilih di **Pre-ride check**; profil terakhir menjadi default.
- Nilai enum FIT `sport`/`sub_sport` diverifikasi agent terhadap FIT SDK Profile.

---

## 5. Deteksi insiden/jatuh + SMS darurat (INC)

> **Penting:** fitur ini membantu, tetapi **tidak menjamin** deteksi semua kecelakaan, dan bisa memberi alarm palsu. Disampaikan dengan jelas di layar setup. Bergantung pada sinyal seluler & pulsa SMS.

### 5.1 Setup
- Layar **Keselamatan**: aktif/nonaktif (default nonaktif sampai kontak diisi), 1–3 **kontak darurat** (dipilih lewat contact picker sistem `ACTION_PICK`, tanpa izin `READ_CONTACTS`, atau diketik manual), nama pengendara untuk isi SMS.
- Tombol **"Kirim SMS uji"** ke kontak (pesan berlabel UJI) agar pengguna yakin berfungsi.
- Izin `SEND_SMS` diminta hanya saat fitur diaktifkan, dengan penjelasan.

### 5.2 Algoritma deteksi (dijalankan hanya saat recording)
```
Prasyarat: kecepatan sebelum kejadian ≥ 8 km/h (rata-rata 5 dtk terakhir)
1. Impact: magnitudo akselerasi |a| ≥ 3.5 g (≈ 34 m/s²) pada sampel akselerometer
2. Konfirmasi diam: dalam 15 dtk setelah impact,
      kecepatan GPS/roda < 2 km/h  DAN
      variansi |a| rendah (HP relatif diam, tidak sedang dikayuh)
3. Bila 1 & 2 terpenuhi → status INCIDENT_SUSPECTED
```
- Akselerometer `TYPE_ACCELEROMETER` dengan laju sedang (≈ 50 Hz, `SENSOR_DELAY_GAME`); buffer berbatas (ring buffer ~5 detik, MEM-05).
- Semua ambang adalah konstanta yang dapat dikonfigurasi dan **harus dikalibrasi lewat uji** (lubang jalan, polisi tidur, HP jatuh dari holder saat berhenti, rem mendadak).

### 5.3 Alur setelah terdeteksi
1. **Layar hitung mundur 30 detik** layar penuh (menyalakan layar, alarm keras + getar terus): "Apakah kamu baik-baik saja?" dengan tombol besar **"SAYA BAIK-BAIK SAJA"** (batalkan) dan **"KIRIM SEKARANG"**.
2. Tidak dibatalkan → kirim SMS ke semua kontak:
   `RideComp: [Nama] mungkin mengalami kecelakaan saat bersepeda pada [jam]. Lokasi terakhir: https://maps.google.com/?q=<lat>,<lon> (akurasi ±X m). Pesan otomatis.`
3. Kirim **update lokasi** sekali lagi setelah 5 menit bila posisi berubah atau belum ada pembatalan; **maksimum 3 SMS per insiden** per kontak.
4. Pengguna dapat menekan **"Saya aman"** setelahnya → SMS "Saya aman" ke kontak yang sama.
5. **Tombol SOS manual:** tekan lama 3 detik pada tombol di layar pause → langsung ke langkah 1 (hitung mundur 5 detik).
6. Rekaman ride tetap berjalan; semua kejadian dicatat sebagai event (`incident_detected`, `incident_cancelled`, `sos_sent`, `sms_failed`).
- Gagal kirim (tanpa sinyal/pulsa) → coba ulang tiap 60 detik maks 5x, tampilkan status di layar.
- Catatan distribusi: izin SMS dibatasi kebijakan Google Play. Untuk APK sideload tidak masalah; bila kelak masuk Play Store, alur ini perlu ditinjau ulang (ajukan pilihan saat itu).

---

## 6. Info tanjakan otomatis (CL, ala ClimbPro)

Hanya untuk ride dengan **rute GPX yang memiliki elevasi** (jika tidak ada elevasi: fitur disembunyikan + info di detail rute).

### 6.1 Deteksi tanjakan (saat import rute, disimpan di DB)
```
1. Resample elevasi rute tiap 20 m, smoothing moving average 100 m
2. Kandidat = bagian menanjak kontinu; gabungkan bila dipisah turunan
   < 10 m elevasi atau < 200 m jarak
3. Tanjakan valid bila: panjang ≥ 500 m DAN gain ≥ 20 m DAN grade rata-rata ≥ 3%
4. Simpan: urutan, jarak mulai/akhir, panjang, gain, grade rata-rata & maks,
   profil grade per 100 m
```
(Ambang default ini milik RideComp, bukan klaim kesamaan dengan algoritma Garmin.)

### 6.2 Saat ride
- **Daftar tanjakan** di detail rute & halaman tanjakan: "Tanjakan 2 dari 4 · 2,1 km · 6,3% · ↑ 132 m".
- **Peringatan 500 m sebelum tanjakan:** banner "Tanjakan dimulai 500 m lagi · 2,1 km · 6%".
- **Halaman tanjakan** (menggantikan halaman tanjakan biasa saat berada di tanjakan): sisa jarak, sisa gain, grade saat ini, grade rata-rata sisa, profil tanjakan berwarna per pita grade dengan posisi saat ini.
- Pita grade: 0–3%, 3–6%, 6–9%, 9–12%, >12%. Warna dibedakan juga oleh **kecerahan** (tidak hanya hue) dan angka grade ditulis pada profil (aksesibilitas).
- Di luar tanjakan: tampil "Tanjakan berikutnya: 3,4 km lagi".

---

## 7. Peringatan belokan (TW)

Turn-by-turn ringan **tanpa routing engine**: belokan dihitung dari geometri rute GPX saat import.

### 7.1 Deteksi (saat import)
```
Untuk setiap titik rute (setelah simplifikasi & resample 10 m):
  bearingMasuk  = arah dari titik 30 m sebelumnya ke titik ini
  bearingKeluar = arah dari titik ini ke titik 30 m sesudahnya
  sudut = selisih bertanda (−180..180)
|sudut| ≥ 45°  → belok (kiri/kanan)
|sudut| ≥ 100° → belok tajam
|sudut| ≥ 150° → putar balik
Gabungkan belokan dalam 30 m; abaikan belokan di 50 m pertama/terakhir rute.
```
- Waypoint/cue dari GPX (mis. `<wpt>` dengan nama/sym belokan) dipakai bila ada dan lebih diutamakan.
- Disimpan sebagai `route_turns`.

### 7.2 Saat ride
- Peringatan pada jarak **≈ 150 m** dan **≈ 30 m** sebelum belokan (disesuaikan kecepatan: lebih awal bila > 30 km/h).
- Banner atas: panah besar + teks "Belok kiri tajam · 150 m" + getar (pola beda untuk kiri/kanan opsional).
- Field data baru: **"Belokan berikutnya"** (arah + jarak).
- Tidak menampilkan nama jalan (data tidak tersedia offline tanpa routing). Saat keluar jalur, peringatan belokan dihentikan sampai kembali ke rute (off-route tetap prioritas).

---

## 8. Personal record & best efforts (BE)

Dihitung **setelah ride disimpan** (di background coroutine, bukan saat ride), dari `track_points`.

| Jenis | Durasi/jarak |
|---|---|
| Jarak tercepat | 1 km, 5 km, 10 km, 20 km, 40 km, 100 km (yang tercapai) |
| Kecepatan terbaik per durasi (T11.8) | 30 dtk, 1 mnt, 3 mnt, 5 mnt, 20 mnt (jarak ÷ waktu bergerak) |
| Power terbaik (power meter saja) | 5 dtk, 30 dtk (T11.8), 1 mnt, 3 mnt (T11.8), 5 mnt, 20 mnt, 60 mnt |
| Lainnya | Ride terjauh, tanjakan total terbanyak, kecepatan rata-rata tertinggi (min. 20 km) |

- Algoritma jendela geser (two-pointer) atas jarak kumulatif / waktu bergerak; **pause tidak dihitung** dalam jendela waktu.
- Hasil disimpan di `best_efforts`; PR baru ditandai dan muncul di ringkasan ride ("PR baru: 10 km tercepat 21:14").
- Layar **Rekor**: daftar PR per kategori dengan tanggal & tautan ke aktivitas, dipisah per profil aktivitas (Indoor tidak dicampur dengan outdoor).
- Estimated power **tidak** masuk PR power.

---

## 9. Segmen pribadi (SG)

Alternatif offline untuk Strava segments (segmen resmi Strava tidak tersedia untuk aplikasi kecil via API).

### 9.1 Membuat segmen
- Dari **detail aktivitas**: pilih titik mulai & akhir di grafik/peta → beri nama.
- Dari **tanjakan rute** (§6): tombol "Jadikan segmen".
- Dari **usaha terbaik** jarak/kecepatan di kartu Hasil (T11.8): "Simpan sebagai segmen" membuka editor pada jalur usaha itu, nama awal "Sprint 20 Nov" (tanggal ride). Power dan rekor seluruh ride tidak bisa (panjang jalur tidak disimpan / tanpa jendela).
- Panjang 200 m – 50 km. Disimpan sebagai polyline yang disederhanakan.

### 9.2 Pencocokan (setelah ride disimpan)
```
1. Cari titik track yang lewat ≤ 25 m dari START segmen, lalu ≤ 25 m dari END (searah)
2. Validasi jalur: ≥ 90% titik segmen berjarak ≤ 30 m dari track di antara keduanya
3. Arah harus sama (arah sebaliknya bukan effort)
4. Effort = waktu dari START ke END (interpolasi), + avg speed/HR/power
```
- Effort tersimpan di `segment_efforts`; PR per segmen ditandai.
- Layar **Segmen**: daftar segmen, PR, 5 effort terbaik, tren.

### 9.3 Live segment (saat ride)
- Saat mendekati START segmen (≤ 50 m) di arah benar: banner "Segmen: Tanjakan Kampung · mulai".
- Selama segmen: halaman/field **"Segmen"**: waktu berjalan, jarak tersisa, **selisih vs PR** (+/− detik, diperkirakan dari progres jarak).
- Selesai: banner hasil "1:42 · PR baru!" atau "+0:08 dari PR".
- Hanya mengecek segmen di sekitar posisi (indeks spasial/bounding box sederhana) agar ringan.

---

## 10. Beban latihan & kebugaran (TL)

> Semua nilai adalah **estimasi untuk tren latihan**, bukan nilai medis. Disampaikan dengan label yang jelas.

### 10.1 Beban per ride (pilih otomatis sesuai data tersedia)
1. **Power meter + FTP:** Training load ala TSS:
   `IF = NP / FTP` · `load = (durasi_detik × NP × IF) / (FTP × 3600) × 100`
2. **HR (tanpa power):** TRIMP (Banister):
   `HRr = (HRavg − HRrest) / (HRmax − HRrest)` ·
   pria: `load = menit × HRr × 0.64 × e^(1.92 × HRr)` · wanita: `0.86 × e^(1.67 × HRr)`
   (bila jenis kelamin tidak diisi: pakai rata-rata kedua koefisien, ditandai).
   Lebih akurat bila dihitung per menit lalu dijumlahkan.
3. Tanpa HR & power: beban tidak dihitung (tidak memakai estimated power untuk beban).
- Metode dicatat (`load_method`) agar tren tidak mencampur metode secara diam-diam; UI menampilkan metode.

### 10.2 Tren harian
```
loadHarian = jumlah beban ride pada hari itu (0 bila tidak ride)
Fitness  (CTL) = CTL_kemarin + (loadHarian − CTL_kemarin) / 42
Kelelahan(ATL) = ATL_kemarin + (loadHarian − ATL_kemarin) / 7
Form     (TSB) = CTL_kemarin − ATL_kemarin
```
- Layar **Latihan**: grafik Fitness/Kelelahan/Form 6–12 minggu (Canvas, downsampled), ringkasan minggu ini (jarak, waktu, beban, jumlah ride), distribusi waktu per zona HR/power minggu ini.
- Dihitung ulang secara inkremental saat ride disimpan/dihapus.

### 10.3 Profil aktivitas
Lihat §4; beban dari semua profil digabung (Indoor ikut dihitung).

---

## 11. Kartu share WA/IG (SC)

- Dari ringkasan/detail aktivitas: tombol **"Bagikan"** → editor sederhana.
- Format: **Story 1080×1920** dan **Kotak 1080×1080**.
- Isi yang bisa dipilih: jejak rute (garis, tanpa peta tile), hingga 4 statistik (jarak, waktu, tanjakan, avg speed/HR/power), nama aktivitas, tanggal, PR baru (bila ada), logo kecil RideComp. Tema gelap/terang.
- **Privasi (wajib):** opsi "Sembunyikan awal & akhir rute" (default **aktif**, memotong 500 m pertama & terakhir dari garis), opsi "Tanpa peta". Tidak ada koordinat/teks lokasi di gambar. Zona privasi (`11-SECURITY-RULES.md` SEC-STO-05) diterapkan bila aktif.
- Render offline (Canvas → bitmap → PNG terkompresi) di `Dispatchers.Default`; bitmap dilepas setelah ditulis (MEM-06); file di cache app dan dibagikan via **FileProvider** (SEC-PLT-04), dihapus saat app dibuka berikutnya.
- Share lewat share sheet Android (WhatsApp, Instagram, dll.); tanpa SDK media sosial.

**Galeri share (T10.6, D-100; referensi pemilik dari layar "Bagikan Aktivitas" Strava):**
- Layar Bagikan berupa **grid 2 kolom** berisi semua desain dengan data ride asli; ketuk untuk memilih (bingkai aksen + centang). Di bawahnya: opsi gaya terpilih (warna poster, tombol foto, atau catatan stiker/peta), tombol **app langsung** (Instagram, WhatsApp — hanya bila terpasang; membuka app tersebut dengan gambar terlampir, pengguna memilih Story/Feed/Pesan di sana; tanpa Facebook App ID), lalu **Sesuaikan**, **Simpan**, **Bagikan** (share sheet).
- Desain: Peta; Tiket tanggal; 14 tata letak **stiker transparan** (rute + statistik vertikal di sisi, statistik vertikal, daftar bertitik, rute + daftar bertitik, tumpuk tengah, tumpuk di atas rute, pil, baris 3 statistik, kisi, rute + angka di samping, rute + baris, angka besar, rute + logo, angka + rute kecil); Peta berbingkai; lalu Poster, Foto, Stiker, Klasik. Logo **RideComp** (bukan merek pihak lain).
- Statistik: hingga **6** (jarak, tanjakan, waktu, avg power, avg speed, kalori, avg HR — hanya yang ada datanya); tata letak baris memakai 3 pertama, gaya lama 4 pertama.
- **Kartu peta** (Peta, Peta berbingkai): hanya bila ada peta (online diizinkan Setelan → Peta, atau region offline). Gambar peta dibuat sekali per layar dengan `MapSnapshotter` di luar layar, garis dipotong 500 m awal/akhir seperti kartu lain, atribusi peta tercetak di kartu. Kartu peta **memperlihatkan lokasi asli**; catatan privasi tampil saat dipilih. Tombol share terkunci sampai peta siap.

---

## 12. Backup & restore data (BK) — Fase 2 (MVP)

**Alasan:** semua data hanya ada di HP. Tanpa backup, HP hilang/rusak/di-reset (atau keystore rilis hilang sehingga app harus di-uninstall) = seluruh riwayat, rekor, segmen, dan tren latihan hilang.

### 12.1 Isi file backup
Satu file `RideComp_backup_YYYYMMDD_HHMM.rcbackup` (arsip ZIP):
```
manifest.json   app_version, db_schema_version, created_at, jumlah aktivitas/rute/segmen,
                encrypted (bool), SHA-256 setiap entri
db/ridecomp.db  snapshot database yang konsisten (mis. `VACUUM INTO` atau checkpoint WAL lalu salin)
fit/*.fit       file FIT aktivitas (bisa dibuat ulang dari DB; tetap disertakan agar backup berguna di luar app)
routes/*.gpx    file GPX asli rute
```
**Tidak disertakan:** token/secret Strava, Client Secret, dan **kontak darurat** (data pribadi pihak ketiga; diisi ulang setelah restore, SEC-SMS-05). Peta offline tidak disertakan (bisa diunduh ulang; ukurannya besar).

### 12.2 Enkripsi
File backup berisi lokasi (alamat rumah tersirat) dan data HR, dan biasanya disimpan di Google Drive/WhatsApp. Maka:
- Opsi **"Lindungi dengan kata sandi"** (rekomendasi: aktif). Enkripsi standar terautentikasi (mis. AES-256-GCM, kunci diturunkan dari kata sandi dengan KDF yang kuat seperti PBKDF2/scrypt/Argon2 dengan salt acak). **Agent mengajukan pilihan library & parameter ke pemilik** dan tidak membuat algoritma kripto sendiri.
- Peringatan jelas: kata sandi lupa = backup tidak bisa dibuka.

### 12.3 Membuat backup
- Layar **Backup & restore** (Pengaturan). Tombol **"Buat backup"** → pilih lokasi via Storage Access Framework (`ACTION_CREATE_DOCUMENT`) atau **share sheet** (Drive, WhatsApp "Pesan untuk diri sendiri", dll.).
- Proses di `Dispatchers.IO`, ditulis secara **streaming** (tidak memuat seluruh DB/FIT ke memori), dengan progres. Tidak boleh saat ride sedang direkam.
- Mencatat tanggal backup terakhir.

### 12.4 Pengingat backup (tanpa kerja background)
Saat app dibuka (bukan lewat AlarmManager/WorkManager, BAT-05): jika backup terakhir > 30 hari **dan** ada ≥ 5 aktivitas baru sejak itu → kartu di Home "Backup terakhir 34 hari lalu · Buat sekarang". Bisa ditutup; muncul lagi 7 hari kemudian.

### 12.5 Restore
1. Pilih file (`ACTION_OPEN_DOCUMENT`) → baca `manifest.json` → tampilkan ringkasan (tanggal, jumlah aktivitas, versi). Minta kata sandi bila terenkripsi.
2. Validasi: checksum setiap entri; `db_schema_version` ≤ versi app (lebih lama → dimigrasi dengan migrasi Room yang sama; lebih baru → tolak dengan pesan "Perbarui RideComp dulu").
3. **Mode: ganti semua data** (merge tidak didukung di MVP). Konfirmasi 2 langkah. Sebelum mengganti, app otomatis membuat **backup darurat lokal** dari data saat ini (agar restore yang salah bisa dibatalkan).
4. Restore ke folder sementara → validasi DB (buka & cek integritas) → baru tukar dengan data aktif secara atomik. Gagal di langkah mana pun → data lama utuh.
5. Setelah restore: profil, sensor tersimpan, rute, segmen, rekor, tren pulih; app memberi tahu bahwa **kontak darurat** perlu diisi ulang.
- Tidak bisa restore saat ride sedang direkam.

### 12.6 Keamanan arsip (wajib)
- Cegah **zip-slip**: setiap nama entri divalidasi; hanya path yang diharapkan (`manifest.json`, `db/…`, `fit/…`, `routes/…`) dan tanpa `..`/path absolut.
- Batas ukuran total & per entri (cegah zip bomb); jumlah entri maksimum.
- File dari luar diperlakukan sebagai input tidak tepercaya (SEC-IN-*).
