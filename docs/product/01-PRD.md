# 01 — Product Requirements Document (PRD)

**Produk:** RideComp (nama kerja)
**Versi dokumen:** 1.0
**Platform:** Android (fase awal), iOS (menyusul)

## 1. Latar belakang

Cycling computer (Garmin Edge, Wahoo, dll.) memberi data real-time seperti kecepatan, heart rate, cadence, power, dan navigasi. Harganya mahal bagi banyak pesepeda. Hampir semua pesepeda sudah punya HP, meski sering HP low-end. HP sudah punya GPS dan Bluetooth Low Energy, sehingga bisa membaca sensor sepeda standar (BLE).

**Masalah:** aplikasi seperti Strava lebih fokus pada rekaman dan sosial, bukan tampilan cycling computer real-time yang lengkap, ringan, dan nyaman dilihat di setang.

**Solusi:** aplikasi yang menjadikan HP (termasuk low-end) sebagai cycling computer lengkap, hemat baterai, andal saat perekaman, dan menghasilkan file FIT standar untuk Strava.

## 2. Tujuan & metrik sukses

| Tujuan | Metrik sukses |
|---|---|
| Andal merekam | 0 ride hilang dalam 20 ride uji; ride bisa dipulihkan setelah crash/kill |
| Hemat baterai | Ride 3 jam dengan layar redup-otomatis menghabiskan ≤ 35% baterai di HP uji low-end (target, diverifikasi di uji lapangan) |
| Ringan | Cold start < 3 detik; layar ride tetap responsif di HP RAM 2 GB |
| Data lengkap | Speed, HR, cadence, power, jarak, elevasi tampil real-time dan tersimpan di FIT |
| Kompatibel Strava | 100% file FIT hasil export diterima upload Strava tanpa error |

## 3. Persona

**Raka, 27, pesepeda roadbike/gravel hobi.** HP Android entry-level (RAM 3 GB). Budget terbatas; sudah atau berencana membeli HR strap dan sensor cadence murah. Rutin gowes pagi 40–100 km, kadang rute baru dari teman (file GPX). Upload ke Strava untuk mencatat progres.

**Kebutuhan:** angka besar yang terbaca di bawah matahari, tidak ribet saat berhenti di lampu merah, ride tidak hilang, dan baterai HP cukup sampai pulang.

## 4. Ruang lingkup

### 4.1 Fitur inti (MVP: Fase 1–2)

| ID | Fitur | Prioritas |
|---|---|---|
| F-01 | Rekam aktivitas: start, pause, resume, stop & save, discard | P0 |
| F-02 | Perekaman GPS 1 Hz di foreground service; tetap jalan saat layar mati | P0 |
| F-03 | **Jeda & lanjut otomatis**: rekaman dijeda saat berhenti/tidak bergerak selama waktu yang dipilih (3/5/10/30 dtk) dan lanjut otomatis saat bergerak lagi; tahan GPS drift; bisa dimatikan (`12-FEATURE-SPECS.md` §1) | P0 |
| F-04 | Layar ride: halaman data field yang bisa dikustom (1–8 field per halaman), geser antar halaman | P0 |
| F-05 | Metrik: speed (now/avg/max), jarak, waktu bergerak, waktu total, elevasi, total tanjakan, grade, jam, kalori | P0 |
| F-06 | Pairing sensor BLE: Heart Rate, Cycling Speed & Cadence, Cycling Power. Simpan sensor, auto-connect | P0 |
| F-07 | Metrik sensor: HR (now/avg/max, zona), cadence (now/avg), power (now, 3s, avg, max, NP), speed dari sensor roda | P0 |
| F-08 | Pemulihan ride setelah crash/app dibunuh sistem | P0 |
| F-09 | Riwayat aktivitas + ringkasan + grafik sederhana | P0 |
| F-10 | Export file FIT (dan GPX) lalu share/simpan untuk upload manual ke Strava | P0 |
| F-11 | Profil: berat badan, berat sepeda, keliling roda, HR max, FTP, zona | P0 |
| F-12 | Lap manual (tombol) + auto-lap per jarak (opsional) | P1 |
| F-13 | Estimated power bila tanpa power meter (ditandai "estimasi", tidak masuk FIT) | P1 |
| F-14 | Peringatan sensor putus / baterai sensor rendah | P1 |
| F-15 | Mode layar: selalu nyala / redup otomatis / mati (data via notifikasi) | P0 |
| F-16 | Pengingat minum & makan berbasis waktu bergerak/jarak (§2) | P1 |
| F-17 | Mode baterai kritis otomatis (20% / 10% / 5%), menjamin ride tidak hilang saat HP hampir mati (§3) | P0 |
| F-18 | Profil aktivitas: Road, Gravel, MTB, Indoor (tanpa GPS), Commute, masing-masing dengan setting sendiri (§4) | P1 |
| F-19 | **Backup & restore** seluruh data ke satu file (opsi kata sandi), pengingat backup, restore aman dengan backup darurat otomatis (§12) | P0 |

### 4.2 Navigasi (Fase 3)

| ID | Fitur | Prioritas |
|---|---|---|
| N-01 | Import rute GPX (track/route) dari file atau share dari app lain | P0 |
| N-02 | Peta OSM dengan garis rute + posisi + jejak yang sudah dilalui | P0 |
| N-03 | Peta offline: unduh area (mis. per kota/provinsi) | P0 |
| N-04 | Peringatan keluar jalur (getar + suara) dan kembali ke jalur | P0 |
| N-05 | Jarak tersisa, estimasi waktu tiba, sisa tanjakan | P1 |
| N-06 | Profil elevasi rute dengan penanda posisi saat ini | P1 |
| N-07 | Mode "breadcrumb" tanpa peta (hanya garis rute di latar gelap) untuk HP sangat lemah | P1 |
| N-08 | Info tanjakan otomatis ala ClimbPro: daftar tanjakan rute, peringatan sebelum tanjakan, sisa jarak & gain, profil berwarna per grade (§6) | P1 |
| N-09 | Peringatan belokan dari geometri rute (150 m & 30 m sebelum belok), tanpa routing engine (§7) | P1 |

### 4.2b Keselamatan (Fase 2)

| ID | Fitur | Prioritas |
|---|---|---|
| K-01 | Deteksi insiden/jatuh (akselerometer + kecepatan) dengan hitung mundur 30 dtk, lalu SMS lokasi ke 1–3 kontak darurat; tombol SOS manual; SMS uji (§5) | P1 |

### 4.2c Latihan & analisis (Fase 4)

| ID | Fitur | Prioritas |
|---|---|---|
| L-01 | Personal record & best efforts (jarak tercepat, power terbaik, rekor lain) (§8) | P1 |
| L-02 | Segmen pribadi offline + live segment saat ride dengan selisih vs PR (§9) | P2 |
| L-03 | Beban latihan per ride + tren Fitness/Kelelahan/Form + ringkasan mingguan (§10) | P2 |
| L-04 | Kartu share untuk WA/IG Story dengan opsi privasi (§11) | P1 |

Detail semua fitur bertanda § ada di `12-FEATURE-SPECS.md`.

### 4.3 Integrasi Strava (Fase 5, opsional)

| ID | Fitur | Prioritas |
|---|---|---|
| S-01 | Panduan upload manual file FIT ke Strava (di dalam app) | P0 (masuk Fase 1) |
| S-02 | Upload otomatis via Strava API (OAuth). Hanya aktif bila pengguna punya langganan Strava + app API terdaftar | P2 |

### 4.4 Di luar ruang lingkup (tidak dikerjakan)

- Turn-by-turn navigation dengan routing engine (yang ada: peringatan belokan dari geometri rute, N-09).
- LiveTrack/group ride real-time (butuh server), cuaca live, radar belakang.
- Fitur sosial online (feed, kudos, leaderboard, segmen Strava resmi). Segmen yang ada hanya segmen pribadi offline (L-02).
- ANT+ (mayoritas HP tidak mendukung).
- Kontrol smart trainer, workout terstruktur (bisa jadi ide fase lanjut).
- Server/backend milik sendiri. Semua data disimpan lokal di HP.

## 5. Persyaratan non-fungsional

| Kategori | Persyaratan |
|---|---|
| Perangkat minimum | Android 8.0 (API 26), RAM 2 GB, GPS, BLE 4.0 |
| Keandalan | Data di-flush ke DB ≤ 5 detik; recovery otomatis; reconnect sensor otomatis |
| Baterai | Lihat aturan di `02-DESIGN.md` §6 dan `03-ARCHITECTURE.md` §6 |
| Offline-first | Semua fitur inti berjalan tanpa internet |
| Privasi | Data hanya di perangkat; tidak ada analytics/telemetry pihak ketiga |
| Bahasa | Indonesia (default) & Inggris |
| Satuan | Metrik (default), imperial opsional |
| Aksesibilitas | Teks data minimum 32sp; target sentuh ≥ 56dp (dipakai saat bersarung tangan) |

## 6. Risiko & mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| OEM (Xiaomi/Oppo/Vivo/Realme dll.) membunuh app di background | Ride terputus | Foreground service, permintaan pengecualian optimasi baterai, panduan pengaturan per merek (lihat dontkillmyapp.com), recovery |
| Akurasi GPS HP murah buruk | Jarak/kecepatan meleset | Filter akurasi, smoothing, sensor speed roda sebagai sumber prioritas |
| Elevasi GPS tidak akurat; HP low-end jarang punya barometer | Total tanjakan meleset | Smoothing + histeresis; pakai barometer bila tersedia |
| HP panas/baterai habis di ride panjang | Ride terputus | Mode layar hemat, rekomendasi power bank, simpan berkala |
| Kebijakan Strava API berubah | Modul upload otomatis tidak bisa dipakai | Jalur utama tetap file FIT + upload manual; modul API terisolasi |
| Lisensi/ToS tile server peta | Fitur offline diblokir | Gunakan sumber tile yang mengizinkan offline/self-hosted (lihat `04-INTEGRATIONS.md`) |
| Deteksi insiden: alarm palsu atau kecelakaan tidak terdeteksi | Kontak panik tanpa sebab / bantuan tidak terkirim | Hitung mundur 30 dtk untuk batal, ambang dikalibrasi lewat uji, SOS manual, penjelasan jujur di layar setup |
| SMS gagal (tanpa sinyal/pulsa) atau kebijakan izin SMS | Pesan darurat tidak terkirim | Retry terbatas + status di layar; sideload; tinjau ulang bila masuk Play Store |
| HP hilang/rusak/di-reset, atau app harus di-uninstall | Seluruh riwayat & rekor hilang | Backup & restore (F-19) + pengingat backup |
| Angka latihan (beban, fitness) disalahartikan sebagai nilai medis | Keputusan latihan keliru | Label "estimasi", metode ditampilkan, tidak memakai estimated power untuk beban |

## 7. Asumsi

- Pengguna menyiapkan holder HP di setang.
- Sensor yang dipakai mendukung profil BLE standar (HRS, CSCS, CPS).
- Upload ke Strava manual memakai fitur upload file di situs web Strava.
