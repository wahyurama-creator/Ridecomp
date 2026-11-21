# 13 — Spesifikasi UI per Layar (dari Desain Canvas)

**Sumber:** canvas desain "RideComp — Alur Desain Aplikasi" (https://claude.ai/artifact/H5V17F3xfk3DYZLnoEpo9o), 31 artboard dalam 5 baris, dibaca apa adanya. Dokumen ini mendeskripsikan **apa yang tampil di desain**; perilaku detail & algoritma tetap mengacu ke `02-DESIGN.md`, `05-DATA-MODEL.md`, dan `12-FEATURE-SPECS.md`.

**Cara pakai (untuk agent):**
- Setiap layar di sini adalah acuan visual untuk implementasi Compose. Implementasi harus **mengikuti struktur, urutan elemen, teks, dan hierarki ukuran** yang tertulis; detail piksel boleh disesuaikan dengan design token.
- **Semua angka, nama, tanggal, dan nama sensor/kontak di desain adalah data contoh**, bukan nilai tetap. Teks label & tombol adalah teks UI sebenarnya (masuk string resources `id`, dengan padanan `en`).
- Bila desain dan spesifikasi berbeda, ikuti spesifikasi dan laporkan di Feature Report (lihat §5 "Catatan ketidaksesuaian").

---

## 1. Konvensi global

### 1.1 Frame & tata letak
- Frame acuan: ponsel **390 × 844 dp**, potret. Layout harus benar di 320 dp; 360 dp ke atas memakai ukuran normal (Fase 13, D-124). Ukuran yang berbeda di bawah 360 dp ada di `AdaptiveDimens` (`RideCompTheme.dimens`).
- Padding layar non-ride: atas 48–56, kiri/kanan 20–24, bawah 24–32.
- Layar ride memakai lebar penuh (grid data menempel ke tepi), padding hanya pada strip status dan bar kontrol.
- Jarak antar blok: 14–20; antar elemen dalam kartu: 8–12.

### 1.2 Warna (tema gelap, default)
| Token | Hex | Terlihat di desain sebagai |
|---|---|---|
| `bg` | `#000000` | Latar semua layar |
| `surface` | `#121212` | Kartu, baris daftar, chip tidak aktif |
| `surfaceAlt` | `#0A0A0A` / `#0E0E10` | Bottom nav, latar peta |
| `divider` | `#2A2A2A` | Garis antar data field & baris daftar, border kartu |
| `textPrimary` | `#FFFFFF` | Nilai data, judul |
| `textSecondary` | `#A0A0A0` | Label, satuan, keterangan |
| `textTertiary` | `#C8C8C8` | Paragraf penjelasan, chip tidak aktif |
| `disabled` | `#6E6E6E` / `#4A4A4A` | Nilai tidak aktif (speed 0.0 saat jeda, `--`), indikator halaman tidak aktif |
| `accent` | `#FC6A1E` | Tombol utama (teks **hitam**), garis rute, tab aktif, link |
| `accentTint` | `#1F1208` | Latar chip/opsi terpilih (border 2 dp `accent`) |
| `track` / info | `#2FA8FF` | Jejak yang sudah dilalui, banner pengingat minum, garis Fitness |
| `ok` | `#3DDC84` | Status tersambung/siap, toggle aktif, selisih lebih cepat |
| `warn` | `#FFC400` | Jeda, mode hemat, sensor putus, peringatan |
| `warnTint` | `#1A1500` | Latar kartu peringatan |
| `danger` | `#FF4D4D` | REC, keluar jalur, Buang, SOS |
| `dangerDeep` | `#7A0E0E` | Latar layar hitung mundur insiden |
| Pita grade | `#FFE08A` (0–6% ringan), `#FFB020` (6–9%), `#F0672A` (9–12%), lebih gelap untuk >12% | Profil tanjakan |

Tema terang: lihat layar **#31** dan tabel di `02-DESIGN.md` §4.1.

### 1.3 Tipografi
Font: Inter (implementasi: font sistem bila Inter tidak dibundel; angka **tabular** di semua nilai data).

| Peran | Ukuran (sp) | Berat |
|---|---|---|
| Nilai hero layar ride (Speed, sisa jarak tanjakan, waktu lap) | 96–112 | 800 |
| Nilai data sekunder | 40–52 | 800 |
| Angka besar khusus (hitung mundur insiden, selisih segmen) | 96 | 900 |
| Judul layar | 24–28 | 800 |
| Judul kartu/item | 15–17 | 700 |
| Isi/paragraf | 14–15 | 400–600, line-height 1.5 |
| Label data field (kapital, letter-spacing 0.5) | 12–14 | 600 |
| Keterangan kecil | 11–13 | 400–600 |

### 1.4 Ukuran sentuh
- Tombol utama besar: 64–88 dp tinggi (MULAI RIDE 88, PAUSE 72, LANJUTKAN 88, SAYA BAIK-BAIK SAJA 120).
- Tombol sekunder: 52–64. Tombol kecil/ikon: **minimal 44 dp**.
- Toggle: 56 × 34. Chip pilihan: tinggi 40–52.

### 1.5 Ikon
Ikon garis (stroke 2–2.5) sederhana: lokasi, hati (HR), lingkaran+jarum (cadence), petir (power), Bluetooth, baterai, lonceng, rute, riwayat, gear, panah, silang, piala (PR), share, unduh/unggah, segitiga peringatan, botol minum, peta. Implementasi: vector drawable (tanpa `material-icons-extended` utuh).

---

## 2. Komponen bersama

| Komponen | Dipakai di | Spesifikasi dari desain |
|---|---|---|
| **RideStatusStrip** | #6–8, #14–17, #25, #30, #31 | Tinggi 40. Kiri: jam. Kanan: ikon status (GPS/HR/cadence, berwarna `ok`/`warn`), % baterai HP (warna `warn` saat mode hemat), indikator **● REC** (`danger`) atau **● JEDA** (`warn`). |
| **DataField** | Semua layar ride | Baris atas: LABEL kiri + satuan/zona/tag kanan (mis. `km/h`, `Z3`, `EST` kuning, `terputus` kuning). Nilai di tengah, auto-fit. Pemisah antar field 1 dp `divider` (2 dp di tema terang). Nilai tak tersedia = `--` warna `disabled`. |
| **RideControlBar** | Semua layar ride | Dua tombol: **PAUSE** (oranye, tinggi 72, ikon jeda, mengisi sisa lebar) + **LAP** (outline putih 2 dp, lebar 120). |
| **PageIndicator** | #6–8, #25, #31 | 4 titik; aktif = pil putih 20×6, lainnya titik 6×6 `#4A4A4A`. Urutan halaman: Utama · Peta · Tanjakan · Lap. |
| **RideBanner** | #7, #14, #15, #16, #30 | Kartu di bawah status strip, tidak menutupi grid data kecuali di atas peta. Varian: `danger` (keluar jalur), `warn` (jeda otomatis, sensor putus), `info` biru (pengingat), putih (belokan), strip `warnTint` (mode hemat). Teks gelap di atas latar terang. |
| **BottomNav** | #3, #11, #28 (dan #20 sebagai bagian Riwayat) | 5 tab: **Ride · Riwayat · Rute · Sensor · Setelan**; aktif = `accent` + label tebal. |
| **SegmentedTabs** | #11, #20, #23, #28 | Latar `surface`, tab aktif putih dengan teks hitam. Riwayat: Aktivitas · Rekor · Segmen · Latihan. |
| **OptionChips** | #2, #5, #21, #24 | Pilihan tunggal; terpilih = border 2 dp `accent` + `accentTint`. |
| **Toggle** | #19, #21, #26, #27 | 56×34; aktif = latar `ok`, knob hitam di kanan; `aria-pressed`/semantik switch. |
| **ListCard** | #13, #19, #26, #27 | Kartu `surface`, baris tinggi 56–64 dengan pemisah; kiri judul, kanan nilai/status. |
| **WarningCard** | #1, #3, #13, #27 | Border 1 dp `warn`, latar `warnTint`, judul `warn` tebal + keterangan. |
| **TopBarBack** | Layar sekunder | Tombol kembali 44×44 `surface` + judul 22–24. |
| **OnboardingProgress** | #1, #2, #22 | 3 bar 4 dp; terisi `accent` sesuai langkah + "LANGKAH n DARI 3". |
| **MapAttribution** | #7, #12, #16, #26 | "© OpenStreetMap contributors", 10 sp, kiri bawah, latar semi transparan. **Wajib.** |

---

## 3. Peta alur

```
#1 Onboarding izin → #2 Profil → #22 Langkah 3 (sensor opsional, tes GPS) → #3 Home
#3 Home
 ├─ MULAI RIDE → #5 Pre-ride check ──(GPS belum fix)──► #29 Mencari GPS ─► (fix / "Mulai tanpa menunggu")
 │                    └─ MULAI → Layar ride: #6 Utama ⇄ #7 Peta ⇄ #8 Tanjakan ⇄ #25 Lap  (geser)
 │                                  state kontekstual: #14 Jeda otomatis/mode hemat · #15 Pengingat
 │                                                     #16 Belokan · #17 Live segmen · #30 Sensor putus
 │                                  insiden → #18 Hitung mundur
 │                                  PAUSE → #9 Pause ─ LANJUTKAN → ride
 │                                                   ├─ SOS (tahan 3 dtk) → #18
 │                                                   └─ SELESAI & SIMPAN → #10 Ringkasan
 │                                                                            ├─ Export FIT / panduan Strava
 │                                                                            └─ Bagikan → #21 Kartu share
 ├─ Kartu "Ride belum tersimpan" → lanjutkan / simpan / buang (recovery)
 ├─ Kartu rute aktif → #12 Detail rute
 └─ BottomNav
     ├─ Riwayat → #11 (Aktivitas) / #28 (kosong) ─ item → #23 Detail aktivitas
     │            tab Latihan/Rekor → #20 ; tab Segmen → daftar segmen
     ├─ Rute → daftar rute → #12
     ├─ Sensor → #4
     └─ Setelan → #13 → Profil aktivitas → #24 Editor halaman data
                       → Keselamatan → #19 · Peta offline → #26 · Backup → #27
                       → "HP saya mematikan app" → panduan per merek
```

---

## 4. Spesifikasi per layar

Format: **Tujuan** · **Isi (urutan atas → bawah)** · **Aksi** · **Catatan & state** · **Acuan spesifikasi**.

### Baris 1 — Persiapan

#### #1 Onboarding & izin (`Main.dc.html`)
- **Tujuan:** meminta izin & mencegah app dibunuh sistem.
- **Isi:** progres langkah 1/3 → judul "Siapkan HP-mu jadi cycling computer" → paragraf "…Semua data tetap di HP-mu." → 3 kartu izin: **Lokasi** (status "Diizinkan" hijau), **Perangkat sekitar** (tombol Izinkan), **Notifikasi** (tombol Izinkan); tiap kartu: ikon berwarna, judul, alasan singkat → **WarningCard** "Jangan biarkan HP mematikan RideComp" dengan merek terdeteksi (contoh: Xiaomi) + instruksi Autostart & penghemat baterai + link "Buka pengaturan baterai" → tombol **Lanjut** (64).
- **Aksi:** Izinkan → dialog izin sistem; status kartu berubah jadi "Diizinkan". Link → intent setelan baterai/autostart sesuai merek.
- **Catatan:** kartu merek hanya muncul untuk merek yang dikenal agresif; teks instruksi per merek. Lanjut tetap bisa walau izin opsional ditolak; izin lokasi wajib.
- **Acuan:** 02 §3.1, 03 §2 & §7.

#### #2 Profil pengendara (`Profile.dc.html`)
- **Isi:** progres 2/3 → judul + penjelasan → baris 2 kolom: **Berat badan** (kg), **Berat sepeda** (kg) → **Ukuran ban** (dropdown: "700 × 25c" + "2105 mm") → baris 2 kolom: **HR max · opsional** (bpm), **FTP · opsional** (W, placeholder "—") → **Posisi tangan**: chip Tops / **Hoods** (terpilih) / Drops → catatan usia & jenis kelamin bisa diisi nanti → **Simpan & lanjut**.
- **Validasi:** angka dengan koma desimal lokal; rentang wajar (berat 30–200 kg, sepeda 5–30 kg, HR max 120–230).
- **Acuan:** 05 §1 `profile`, 12 §4.

#### #22 Onboarding langkah 3 (`OnboardingStep3.dc.html`)
- **Isi:** progres 3/3 → "Hampir siap gowes" + paragraf (tanpa sensor tetap merekam GPS…) → kartu **Hubungkan sensor** (tombol Scan → #4) → kartu **Tes GPS** (status "Sinyal didapat · akurasi ±5 m" + "OK") → kartu **Tips pemasangan HP** (3 poin) → **Mulai pakai RideComp** → tombol teks "Lewati, hubungkan sensor nanti".
- **State Tes GPS:** mencari (kuning) / OK (hijau) / izin ditolak (merah + aksi).

#### #3 Home (`Home.dc.html`)
- **Isi:** header logo "Ride**Comp**" + "Baterai HP 86%" → **kartu recovery** (bila ada ride belum tersimpan): judul `warn`, info waktu · jarak · durasi · alasan, tombol **Lanjutkan** (kuning), **Simpan** (outline kuning), **Buang** (teks merah) → **4 tile status**: GPS (±akurasi), HR (% baterai sensor), Cadence (%), Power ("Estimasi" bila tidak ada power meter, abu-abu) → **MULAI RIDE** (88, ikon play) → **kartu Rute aktif** (nama, jarak · tanjakan, tombol hapus 44) → **kartu Ride terakhir** (jarak, bergerak, rata-rata) → **BottomNav** (Ride aktif).
- **State:** tanpa recovery → kartu hilang; tanpa rute aktif → kartu hilang (opsional: ajakan pilih rute); tanpa ride → kartu ride terakhir hilang. Kartu pengingat backup (#27, 12 §12.4) muncul di posisi yang sama dengan kartu recovery bila relevan (recovery lebih prioritas).

#### #4 Sensor (`Sensors.dc.html`)
- **Isi:** TopBarBack "Sensor" → seksi **Tersimpan**: kartu per sensor (ikon tipe berwarna, nama, "tipe · baterai %", status "● Tersambung" hijau) → seksi **Ditemukan di sekitar** + "Mencari… 9 dtk" (hitung mundur scan) → kartu hasil scan (nama, layanan, kekuatan sinyal; tombol **Pair** — oranye penuh untuk sinyal kuat, outline untuk lemah) → info box (hanya BLE; ANT+ tidak; kalibrasi via app pabrikan) → **Scan ulang**.
- **State:** scan selesai tanpa hasil → pesan + tips; Bluetooth mati → banner + aksi nyalakan; sensor tersimpan offline → status abu "Tidak terlihat".
- **Acuan:** 04 §A.5, 02 §3.8.

#### #5 Pre-ride check (`PreRide.dc.html`)
- **Isi:** TopBarBack (ikon silang = Batal) "Siap berangkat?" → **daftar cek** dalam satu kartu: GPS ("Siap · ±4 m"), Heart rate (bpm saat ini), Speed & cadence ("Tersambung"), Power meter (ikon `warn`, "Tidak ada · pakai estimasi"), Baterai HP ("86% · ±4 jam") → **Profil aktivitas**: chip Road (terpilih) · Gravel · MTB · Indoor · Commute → **Rute** (nama · jarak + link Ganti) → **Mode layar**: Selalu nyala · **Redup otomatis** (terpilih) · Mati → **MULAI** (88).
- **State:** GPS belum fix → layar #29; profil Indoor → baris GPS disembunyikan/"tidak dipakai".
- **Acuan:** 02 §3.3, 12 §4.

### Baris 2 — Saat ride

#### #6 Ride: halaman utama (`RideMain.dc.html`)
- **Isi:** RideStatusStrip → grid 2 kolom, 4 baris (baris pertama 1,5× tinggi): **SPEED** (lebar penuh, 112) · JARAK · BERGERAK (h:m:s) · HEART RATE (tag zona `Z3` kuning) · CADENCE · **POWER 3s** (tag `EST` kuning bila estimasi) · AVG SPEED → PageIndicator (halaman 1) → RideControlBar.
- **Catatan:** ini konfigurasi default halaman Utama profil Road (6 field + hero); isi field mengikuti editor #24.

#### #7 Ride: navigasi rute (`RideMap.dc.html`)
- **Isi:** status strip → area peta (sisa tinggi): jalan abu, **garis rute oranye** (7), **jejak biru**, garis putus-putus biru dari titik terakhir di rute ke posisi saat ini, **panah posisi** putih dengan halo merah saat keluar jalur → **banner `danger`** di atas peta: ikon segitiga, "Keluar jalur", "74 m dari rute · putar balik ke barat" → tombol mengambang **Ikuti posisi** (48) kanan bawah → atribusi OSM → 2 field (SISA JARAK, SPEED, tinggi 92) → PageIndicator (halaman 2) → RideControlBar.
- **State:** normal (tanpa banner); kembali ke rute → banner hijau singkat; mode breadcrumb (tanpa tile) memakai latar polos dengan garis yang sama.
- **Acuan:** 04 §C.3, 12 §7.

#### #8 Ride: tanjakan ala ClimbPro (`RideClimb.dc.html`)
- **Isi:** status strip → hero: label oranye **"TANJAKAN 2 / 4"** + "sisa jarak · km", nilai **1.4** (104) → 2 field: **SISA GAIN** (m) · **GRADE** saat ini (warna sesuai pita, keterangan "avg sisa 6,1%") → panel **PROFIL TANJAKAN** ("2,1 km · 6,6% · ↑ 138 m"): batang per 300 m berwarna pita grade dengan **angka grade tertulis** di atas tiap batang, bagian yang sudah dilalui digelapkan, garis putus-putus + titik posisi saat ini → PageIndicator (halaman 3) → RideControlBar.
- **State:** di luar tanjakan → tampil "Tanjakan berikutnya: x km lagi" + profil elevasi rute; tanpa rute/elevasi → halaman tanjakan biasa (grade, elevasi, total tanjakan).
- **Acuan:** 12 §6.

#### #9 Layar pause (`Pause.dc.html`)
- **Isi:** "● DIJEDA" (`warn`) → waktu bergerak besar (64) + "waktu bergerak · total …" → 4 kartu ringkas 2×2: JARAK, AVG SPEED, TANJAKAN, AVG HR → (spacer) → **SOS · tahan 3 detik** (outline merah 56, ikon segitiga) → **LANJUTKAN** (88, oranye, ikon play) → **SELESAI & SIMPAN** (outline putih 64) → **Buang ride** (teks merah) → catatan "Buang butuh konfirmasi 2 langkah · tombol Stop hanya ada di sini".
- **Aksi:** SOS → tahan 3 dtk dengan indikator progres → #18 (hitung mundur 5 dtk, 12 §5.3). Buang → dialog konfirmasi 2 langkah.

### Baris 3 — Setelah ride, rute & pengaturan

#### #10 Ringkasan & export FIT (`Summary.dc.html`)
- **Isi:** input **NAMA AKTIVITAS** (default sesuai waktu: "Gowes Pagi") → peta kecil jejak (statis) → grid 3×3 statistik: JARAK · BERGERAK · TANJAKAN · AVG/MAX speed · AVG HR (+max) · CADENCE · **POWER EST** (label kuning, "W · tidak masuk FIT") · KALORI ("kcal (dari HR)") · LAP (jumlah, "lihat detail") → **kartu PR** (border oranye, ikon piala: "2 PR baru · …") → **Simpan** (64) → baris **Export FIT** (outline, ikon unduh) + **Bagikan** (ikon share) → link "Cara upload file FIT ke Strava".
- **State:** tanpa PR → kartu PR hilang; dengan power meter → label "POWER" biasa (tanpa EST) dan nilai masuk FIT.

#### #11 Riwayat (`History.dc.html`)
- **Isi:** judul "Riwayat" → SegmentedTabs (**Aktivitas** aktif · Rekor · Segmen · Latihan) → kartu ringkasan **BULAN INI**: jarak, jumlah ride, tanjakan → daftar kartu aktivitas: nama + status ekspor ("FIT diekspor" hijau / "Belum diekspor" kuning), waktu relatif, jarak · jam · m↑ → BottomNav (Riwayat aktif).
- **State kosong:** #28. Pagination lazy (20 per halaman).

#### #12 Detail rute GPX (`Routes.dc.html`)
- **Isi:** TopBarBack: nama rute + "file.gpx · diimpor 3 hari lalu" → peta rute (garis oranye, titik start hijau) + atribusi → 3 kartu: JARAK · TANJAKAN · TERTINGGI → kartu **PROFIL ELEVASI** → info hijau "Peta area rute tersedia **offline**" → **Ride dengan rute ini** (64) → baris **Balik arah** + **Import GPX lain**.
- **State:** peta belum offline → info kuning + aksi "Unduh peta area ini" (→ #26); GPX tanpa elevasi → profil diganti teks "Rute ini tidak punya data elevasi".

#### #13 Pengaturan (`Settings.dc.html`)
- **Isi:** judul → **WarningCard** "HP saya mematikan app" (panduan merek terdeteksi, chevron) → ListCard 1: Profil pengendara (ringkasan) · **Profil aktivitas** ("5 profil · halaman data") · Layar & baterai · **Jeda & lanjut otomatis** ("Aktif · diam 5 dtk", hijau) · **Pengingat minum & makan** ("20 / 45 mnt") · **Keselamatan & kontak darurat** ("Aktif · 2 kontak", hijau) → ListCard 2: Peta offline ("1 area · 186 MB") · **Tampilan** ("Gelap", T11.9: Tema Gelap · Terang · Ikuti HP) · Satuan & bahasa · Upload otomatis Strava ("Opsional · nonaktif") · **Backup & export data** → footer "RideComp · data tersimpan lokal, tanpa iklan & tracking".
- **Catatan:** setting per profil (auto-lap, tombol volume, halaman data, auto-pause, pengingat) diakses di dalam **Profil aktivitas**.

### Baris 4 — Fitur tambahan

#### #14 Jeda otomatis + mode hemat (`RideAutoPause.dc.html`)
- **Isi:** status strip (baterai **19%** kuning, "● JEDA" kuning) → strip `warnTint` "Mode hemat aktif · layar redup, peta jadi breadcrumb" → blok kuning penuh **"JEDA OTOMATIS"** (30, 900) + "Lanjut sendiri saat kamu bergerak · diam 1:42" → grid 2×3: SPEED **0.0** (abu), JARAK, BERGERAK (tag "berhenti" kuning), TOTAL (tag "berjalan"), HEART RATE, AVG SPEED → RideControlBar.
- **Catatan:** kedua kondisi (jeda & mode hemat) dapat muncul terpisah; desain menampilkannya bersamaan. Blok jeda tidak bisa ditutup; hilang otomatis saat bergerak. **Acuan:** 12 §1, §3.

#### #15 Pengingat minum (`RideReminder.dc.html`)
- **Isi:** status strip → **banner biru** (latar `#2FA8FF`, teks hitam): ikon botol 40, "Waktunya minum" (24, 900), "20 menit bergerak sejak terakhir", tombol **Sudah** (hitam, teks putih) + **Tunda 5 mnt** (outline hitam), tinggi 56 → grid: SPEED (hero 96), JARAK, BERGERAK, HEART RATE, **MAKAN** ("25 mnt" lagi) → RideControlBar.
- **Varian:** pengingat makan memakai banner yang sama dengan ikon & teks makanan. **Acuan:** 12 §2.

#### #16 Peringatan belokan (`RideTurn.dc.html`)
- **Isi:** status strip → peta: rute lurus lalu belok kiri tajam di persimpangan (lingkaran putus-putus menandai titik belok) → **kartu putih** di atas peta: panah belok besar (64) + **"150 m"** (44, 900) + "Belok kiri tajam" → atribusi → 2 field: SISA JARAK, SPEED → RideControlBar.
- **Varian teks:** Belok kiri/kanan, Belok kiri/kanan tajam, Putar balik; jarak 150 m lalu 30 m. **Acuan:** 12 §7.

#### #17 Live segmen pribadi (`RideSegment.dc.html`)
- **Isi:** status strip → label oranye "SEGMEN PRIBADI" + nama segmen (22) → kartu hijau (border 2 `ok`, latar gelap): "SELISIH VS PR", **"−0:06"** (96, hijau), "lebih cepat dari rekor" → **progres segmen** (bar oranye 68% + "0,42 km lagi") → grid 2×2: WAKTU SEGMEN · PR SEGMEN (abu) · HEART RATE (zona) · SPEED → RideControlBar.
- **Varian:** lebih lambat → "+0:xx", warna `warn`/oranye, teks "lebih lambat dari rekor" (bedakan juga dengan tanda +/−, bukan hanya warna). Selesai → banner hasil "PR baru!" / "+0:08 dari PR". **Acuan:** 12 §9.3.

#### #18 Hitung mundur insiden (`Incident.dc.html`)
- **Isi (layar penuh, latar `dangerDeep`):** ikon segitiga 56 → "Terdeteksi benturan keras" (28, 900) → "Apakah kamu baik-baik saja?" → **lingkaran hitung mundur** (border putih 10, angka 96 + "detik") → teks penerima SMS (nama kontak tebal) → (spacer) → **SAYA BAIK-BAIK SAJA** (putih, teks hitam, tinggi **120**, lebar penuh) → **KIRIM SEKARANG** (outline putih 60) → "Alarm berbunyi & HP bergetar · rekaman ride tetap berjalan".
- **State lanjutan (belum digambar):** setelah terkirim → status per kontak + tombol "Saya aman"; gagal kirim → status retry. **Acuan:** 12 §5.3.

#### #19 Keselamatan & kontak darurat (`Safety.dc.html`)
- **Isi:** TopBarBack "Keselamatan" → kartu toggle **Deteksi insiden** + penjelasan alur → input **NAMA PENGENDARA (UNTUK ISI SMS)** → **KONTAK DARURAT (MAKS 3)**: baris kontak (avatar inisial, nama, nomor **disamarkan** "0812 •••• 4521", tombol hapus 44) + link "+ Tambah kontak dari buku telepon" → kotak putus-putus **CONTOH SMS** → disclaimer keterbatasan → **Kirim SMS uji** (outline).
- **Catatan:** nomor lengkap tidak pernah ditampilkan penuh di daftar (privasi). Toggle meminta izin SMS saat diaktifkan. **Acuan:** 12 §5.1, 11 SEC-SMS.

#### #20 Latihan & rekor (`Training.dc.html`)
- **Isi:** judul "Riwayat" + SegmentedTabs (**Latihan** aktif) → 3 kartu: **FITNESS** (biru, "+5 / 4 mgg"), **KELELAHAN** (oranye, "7 hari"), **FORM** (putih, keterangan status) → kartu **TREN 8 MINGGU** + metode ("estimasi dari HR (TRIMP)"): grafik 3 garis — Fitness (biru, garis penuh), Kelelahan (oranye, putus-putus), Form (abu terang, titik-titik) + legenda → kartu **MINGGU INI**: ride · km · jam · beban → daftar rekor: "10 km tercepat — 21:14 · PR baru" (oranye), Ride terjauh, Tanjakan terbanyak.
- **Catatan:** tab Rekor menampilkan daftar PR lengkap per profil; tab Segmen menampilkan daftar segmen (layar khusus belum digambar, ikuti pola ListCard). **Acuan:** 12 §8–§10.

#### #21 Kartu share WA/IG (`ShareCard.dc.html`)
- **Isi:** TopBarBack "Bagikan" → **pratinjau kartu** (rasio 9:16): tanggal, nama aktivitas, jejak rute oranye (tanpa peta tile), 4 statistik (JARAK, WAKTU, TANJAKAN, AVG), footer "2 PR BARU · RideComp" → chip **Story 9:16** (terpilih) / Kotak 1:1 → ListCard: toggle **Sembunyikan awal & akhir rute** ("Melindungi lokasi rumah (500 m)", aktif) · toggle **Tampilkan jejak rute** (aktif) · baris **Statistik** (pilihan saat ini) → **Bagikan ke WhatsApp / Instagram** (64).
- **Acuan:** 12 §11.

### Baris 5 — Layar pelengkap & state

#### #23 Detail aktivitas (`ActivityDetail.dc.html`)
- **Isi:** TopBarBack: nama + "Hari, tanggal · jam · Profil" → kartu ringkas 4 kolom: km · bergerak · m↑ · avg bpm → SegmentedTabs grafik: Speed · **HR** · Cadence · Power · Elevasi → kartu grafik ("HEART RATE vs JARAK", max) dengan area berwarna + sumbu jarak 0–42 km → **tabel lap**: LAP · JARAK · WAKTU · KM/H · HR (lap tercepat disorot oranye) → baris aksi 3: **Export FIT** · **Bagikan** · **Buat segmen** → tombol teks **Hapus aktivitas** (merah, konfirmasi).
- **State:** tab Power tanpa data → teks "Tidak ada data power" (atau grafik estimasi berlabel EST); grafik di-downsample (≤ 1000 titik).
- **Redesign (D-77):** sketsa rute (tanpa tile; titik start hijau, finish oranye) → StatGrid 3 kolom (jarak, waktu bergerak, rata/maks kecepatan, tanjakan, total waktu; HR/power/kadens/kalori bila ada; satuan kecil di samping angka) → kartu **Bagikan sebagai gambar** (border oranye) → tab grafik + kartu grafik: judul, ringkasan RATA/MAKS (dari total ride; elevasi MIN/MAKS), angka sumbu kiri (maks/tengah/min) dan bawah (jarak awal/tengah/akhir), area isi, **sentuh/geser untuk membaca nilai** → tabel lap → tips → **EKSPOR & STRAVA** (ListCard ber-ikon: Kirim FIT · Simpan FIT (status "Sudah diekspor") · Kirim GPX · Cara upload ke Strava) → **LAINNYA** (Buat segmen · Hapus aktivitas merah).

#### #24 Editor halaman data (`DataPageEditor.dc.html`)
- **Isi:** TopBarBack "Halaman data" + "Profil Road" → chip halaman: **Utama** (aktif, putih) · Peta · Tanjakan · Lap · **+** (tambah) → **JUMLAH KOTAK**: 1 · 2 · 3 · 4 · **6** · 8 → "Tap kotak untuk mengganti metriknya" → pratinjau grid layout (kotak pertama lebar penuh; kotak terpilih ber-border oranye) → **bottom sheet** "Ganti metrik kotak 6": chip kategori (Kecepatan · **Cadence** · Power · Heart rate · Navigasi) + daftar metrik kategori (Cadence ✓ · Avg cadence · Lap avg cadence).
- **Aksi:** perubahan disimpan otomatis per profil; bisa hapus/urutkan halaman (long-press chip). **Acuan:** 05 §2, 02 §5.

#### #25 Ride: halaman lap (`RideLap.dc.html`)
- **Isi:** status strip → "LAP 3" (oranye, 22) + "auto-lap tiap 5 km" → grid: **WAKTU LAP** (hero 96) · JARAK LAP · AVG LAP · AVG HR LAP · AVG POWER LAP (EST) → baris lebar penuh: **LAP 2 (5 km)** waktu vs **PROYEKSI LAP 3** ("12:42 · −0:13" hijau) → PageIndicator (halaman 4) → RideControlBar.
- **Catatan:** proyeksi hanya untuk auto-lap berbasis jarak.

#### #26 Peta offline (`OfflineMaps.dc.html`)
- **Isi:** TopBarBack "Peta offline" → kartu **Penyimpanan HP** (tersedia, bar pemakaian oranye/abu, "Peta RideComp memakai 186 MB") → **AREA TERSIMPAN**: kartu area (ikon peta hijau, nama, ukuran · diperbarui, tombol hapus merah 44) → **UNDUH AREA BARU**: peta dengan **kotak pilihan** putus-putus oranye (sudut bisa digeser) + atribusi → toggle **Hanya lewat Wi-Fi** → **Unduh area ini · ≈ 92 MB** → "Geser sudut kotak untuk memilih area · atau impor file peta".
- **State:** progres unduhan (bar + batal); ruang tidak cukup → tombol nonaktif + pesan. Sumber tile mengikuti keputusan T3.3.
- **Implementasi (T11.11, D-128):** di atas daftar: pilihan **Peta online** (Wi‑Fi & data · Hanya Wi‑Fi · Mati) lalu **Cache peta** (chip 50/150/300 MB, "Terpakai X", tombol **Hapus cache peta** dengan konfirmasi). Peta yang pernah tampil online dipakai lagi tanpa internet.

#### #27 Backup & restore (`Backup.dc.html`)
- **Isi:** TopBarBack → **WarningCard** "Backup terakhir 34 hari lalu" + tanggal · jumlah aktivitas · ride baru → kartu **Termasuk** / **Tidak termasuk** → ListCard: toggle **Lindungi dengan kata sandi** ("Disarankan: berisi lokasi & data HR") + input **KATA SANDI** (tersamar) + peringatan kuning "Lupa kata sandi = backup tidak bisa dibuka" → **Buat backup** (64, ikon unggah) + "Simpan ke Google Drive, WhatsApp, atau folder HP" → **Restore dari file** (56) + "Restore mengganti semua data · backup darurat dibuat otomatis dulu".
- **State:** backup baru-baru ini → kartu status hijau/netral; proses backup/restore → progres + tidak bisa saat ride berjalan; kata sandi perlu konfirmasi (field kedua, belum digambar). **Acuan:** 12 §12.

#### #28 State kosong: riwayat (`EmptyHistory.dc.html`)
- **Isi:** judul + SegmentedTabs → di tengah: ilustrasi garis sepeda (abu) → "Belum ada ride" (20, 800) → "Ride pertamamu akan muncul di sini…" → **MULAI RIDE PERTAMA** (64) → link "Punya backup? Restore dari file" → BottomNav.
- **Pola ini dipakai untuk state kosong lain:** Rute ("Import GPX"), Segmen ("Buat segmen dari aktivitas"), Rekor, Sensor.

#### #29 State menunggu: cari GPS (`GpsWaiting.dc.html`)
- **Isi:** TopBarBack (Batal) "Siap berangkat?" → indikator **statis**: dua lingkaran putus-putus (luar abu, dalam kuning) + ikon lokasi kuning → "Mencari sinyal GPS…" → "0:24 · akurasi saat ini **±48 m** (butuh ≤ 20 m)" → kartu **Biar lebih cepat** (3 tips) → **Mulai tanpa menunggu** (outline 64) + "Rekaman mulai sekarang, posisi menyusul saat sinyal didapat".
- **Catatan:** tanpa animasi berat (HP low-end); begitu akurasi ≤ 20 m → kembali ke #5 dengan status GPS "Siap".

#### #30 State error: sensor putus (`SensorLost.dc.html`)
- **Isi:** status strip (ikon HR kuning) → **banner kuning** (teks hitam): ikon Bluetooth dicoret, "HR Strap terputus", "Menyambung ulang… percobaan 3 · rekaman tetap jalan" → grid: SPEED (hero) · JARAK · BERGERAK · **HEART RATE** (label & tag "terputus" kuning, nilai `--` abu) · CADENCE → RideControlBar.
- **Aturan:** data lain tetap tampil; banner mengecil/hilang setelah 5 dtk dan muncul lagi saat sambung kembali ("HR Strap tersambung", hijau). Pola sama untuk GPS hilang/izin dicabut (banner `danger` + aksi). **Acuan:** 02 §3.13.

#### #31 Tema terang (`RideLight.dc.html`)
- **Isi:** identik dengan #6 tetapi: latar putih, teks hitam, label `#4A4A4A`, pemisah field 2 dp `#BDBDBD`, ikon status hijau tua `#1B7A3E`, REC `#B3261E`, tag zona/EST `#8A6100`, **PAUSE** latar `#C2410C` dengan teks **putih**, LAP outline hitam, indikator halaman hitam/abu.
- **Catatan:** semua layar harus punya varian terang dengan token yang sama (`02-DESIGN.md` §4.1).

---

## 5. Catatan ketidaksesuaian & hal yang belum digambar

Hal-hal di bawah ditemukan saat membaca desain; agent mengikuti kolom "Tindakan" dan menanyakan pemilik bila ragu.

| # | Temuan | Tindakan |
|---|---|---|
| 1 | #24 memilih **6 kotak**, tetapi pratinjau hanya menampilkan **5 kotak** (Speed lebar penuh + 4), sementara sheet menyebut "kotak 6". | Implementasi layout 6 = 1 hero + 5? atau 6 kotak setara? **Tanyakan pemilik**; usulan: hero + 5 kotak di layout 6 (konsisten dengan #6 yang menampilkan hero + 6 field = layout "7"). Samakan definisi jumlah kotak di 02 §3.4. |
| 2 | #6 menampilkan hero + 6 field (7 nilai), sedangkan konfigurasi default di 05 §2 menyebut halaman Utama `layout: 6`. | Selaraskan saat T1.8: definisikan apakah hero dihitung sebagai kotak. |
| 3 | Label tab bawah **"Setelan"** sedangkan judul layar **"Pengaturan"**. | Pakai satu istilah (usulan: "Setelan" di tab karena pendek, judul layar juga "Setelan"), atau biarkan bila disetujui. |
| 4 | Tab Rekor & Segmen (Riwayat), layar daftar rute, panduan baterai per merek, state setelah SMS insiden terkirim, konfirmasi kata sandi backup, dialog konfirmasi Buang/Hapus/Restore **belum digambar**. | Ikuti pola komponen di §2 dan spesifikasi 02/12; tampilkan screenshot di Feature Report untuk review. |
| 5 | #14 menampilkan jeda otomatis dan mode hemat sekaligus. | Keduanya independen; uji masing-masing sendiri dan gabungan. |
| 6 | Desain memakai Inter dari Google Fonts. | Di app: font sistem atau Inter dibundel (cek ukuran APK & lisensi OFL, DEP-02). |

---

## 6. Checklist kesesuaian UI (tambahan untuk Feature Report)
- [ ] Urutan & hierarki elemen sesuai layar terkait di dokumen ini.
- [ ] Semua teks UI dari string resources (`id` & `en`); data contoh tidak di-hardcode.
- [ ] Token warna & tipografi sesuai §1; angka tabular di nilai data.
- [ ] Target sentuh ≥ 44 dp (tombol utama sesuai §1.4); `contentDescription` untuk tombol ikon.
- [ ] State kosong/menunggu/error tersedia sesuai §4 dan 02 §3.13, dengan `@Preview`.
- [ ] Varian tema terang tersedia.
- [ ] Screenshot (build release, HP fisik) dilampirkan di Feature Report untuk dibandingkan dengan canvas.

#### Segmen baru (editor; T4.2, didesain ulang T11.15, D-133)
- **Isi:** TopBarBack "Segmen baru" → **1 · PILIH BAGIAN**: petunjuk singkat, peta jalan (online/cache/file wilayah; sketsa bila tidak ada) dengan seluruh ride `accent` dan bagian terpilih `track`, titik mulai `ok` & titik selesai `textPrimary`; chip **Pintasan** (Seluruh ride · Tanjakan N · panjang · %); grafik elevasi dengan rentang disorot + RangeSlider; baris "Mulai X km [−][+]" & "Selesai Y km [−][+]" (50 m per ketuk) → **2 · PERIKSA**: StatGrid Panjang · Kenaikan · Rata-rata · Waktumu · Kecepatan · Elevasi (min–maks), peringatan kuning bila di luar 200 m–50 km → **3 · BERI NAMA**: kolom nama, kartu ⓘ cara pencocokan, tombol **Simpan segmen** (nonaktif bila belum valid).
