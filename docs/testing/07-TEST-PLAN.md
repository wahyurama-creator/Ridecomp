# 07 — Test Plan & Acceptance Criteria

## 1. Lapisan pengujian

| Lapisan | Cakupan | Alat |
|---|---|---|
| Unit | Parser BLE, rumus metrik, filter GPS, FIT writer, GPX parser, route tracker (semua di `:core`) | JUnit / kotlin.test via `./gradlew test` (JVM, tanpa perangkat) |
| UI | DataField (auto-fit, `--`), layar ride, editor halaman | Compose UI test (`androidTest`) |
| Integrasi | Alur rekam → simpan → export dengan data GPS/BLE simulasi; Room migration test | `androidTest` + sumber data palsu |
| Performa | Cold start, jank layar ride di HP low-end (build release) | Macrobenchmark (opsional) + uji manual |
| Lapangan | Ride sungguhan di HP low-end | Checklist §4 |

## 2. Simulasi (wajib dibuat di Fase 1–2)
- **Fake GPS source:** memutar ulang file GPX berstempel waktu dengan kecepatan 1x/10x.
- **Fake BLE source:** memutar byte array HR/CSC/CP dari fixture, termasuk skenario putus-sambung dan rollover.
- Dipilih via setting developer (tersembunyi di build release).

## 3. Kasus uji unit penting

| Area | Kasus |
|---|---|
| HR | uint8, uint16, dengan RR, nilai 0 diabaikan |
| CSC | wheel-only, crank-only, keduanya, rollover crank time (uint16), rollover wheel revs (uint32), tidak ada event baru > 3 dtk → 0 |
| CP | power-only; dengan pedal balance; wheel time 1/2048; crank data setelah field opsional; power negatif ditolak |
| GPS | buang akurasi buruk; jitter saat diam tidak menambah jarak; spike speed dibuang |
| Jeda/lanjut otomatis | ambang & durasi (3/5/10/30 dtk); GPS drift saat diam tidak memicu resume; tanjakan 4–5 km/h tidak dijeda; pause manual tidak di-resume; sensor roda vs GPS; prompt diam > 15 mnt |
| Pengingat | dihitung dari waktu bergerak (tidak jalan saat jeda); Sudah/Tunda; maks 2 ulangan |
| Mode baterai | transisi 20/10/5% dengan fake battery; kembali normal saat mengisi; FIT darurat terbentuk di 5% |
| Deteksi insiden | data sintetis/rekaman: jatuh saat bergerak → terdeteksi; lubang jalan, polisi tidur, rem mendadak, HP jatuh saat diam (< 8 km/h) → tidak terdeteksi; hitung mundur dibatalkan → tidak ada SMS; batas 3 SMS; retry gagal kirim |
| Tanjakan | GPX referensi: jumlah & panjang tanjakan sesuai; dip kecil digabung; rute tanpa elevasi → fitur disembunyikan |
| Belokan | belok 90°, hairpin, putar balik terdeteksi dengan arah benar; tikungan landai & noise GPS tidak memicu alert; belokan berdekatan digabung |
| Best efforts | jendela jarak/waktu benar; pause di tengah tidak dihitung; estimated power tidak masuk PR |
| Segmen | lewat penuh → effort; lewat sebagian / arah terbalik → bukan effort; PR ditandai |
| Beban latihan | nilai referensi TSS-style & TRIMP; CTL/ATL inkremental konsisten setelah ride dihapus |
| Kartu share | opsi privasi memotong 500 m awal/akhir; tidak ada teks koordinat |
| Backup & restore | round-trip data identik (aktivitas, rute, segmen, rekor, profil); kata sandi salah ditolak; checksum rusak ditolak; skema lebih lama dimigrasi, lebih baru ditolak; gagal di tengah restore → data lama utuh; token & kontak darurat tidak ada di arsip; streaming tanpa memuat semua ke memori |
| Elevasi | histeresis tanjakan tidak menghitung noise ±3 m |
| NP | < 30 detik → null; nilai referensi dari dataset contoh |
| Estimated power | datar 30 km/h ≈ kisaran wajar (~150–200 W untuk 75 kg, hoods); turunan → 0 |
| FIT | konversi semicircles & FIT epoch; file terbaca parser FIT referensi; field kosong ditulis invalid |
| Route | proyeksi ke polyline; rute loop/bersilangan; off-route 50 m/10 dtk |

## 3b. Uji keamanan (lihat `11-SECURITY-RULES.md`)

| # | Uji | Lulus jika |
|---|---|---|
| S1 | Fuzz parser BLE (HR/CSC/CP) dengan byte acak & terpotong (≥ 10.000 iterasi, seed tetap) | Tidak ada exception tak tertangani |
| S2 | GPX berbahaya: DOCTYPE/entity eksternal, entity bersarang ("billion laughs"), file > batas, titik > batas, nilai NaN/di luar rentang, XML terpotong | Ditolak dengan pesan jelas; tidak crash/hang; tidak ada akses jaringan/file dari entity |
| S3 | Nama file GPX berisi `../ridecomp-docs` atau karakter aneh | Tidak ada file ditulis di luar folder app |
| S4 | Komponen ter-export: `adb shell dumpsys package com.ridecomp.app` / MobSF | Hanya komponen yang terdokumentasi |
| S5 | Memicu `RecordingService` dari app/adb lain | Ditolak (tidak ter-export) |
| S6 | Request HTTP cleartext (uji dengan URL `http://`) | Diblokir oleh Network Security Config |
| S7 | Cek log release (`adb logcat`) selama ride simulasi | Tidak ada koordinat, token, atau data pribadi |
| S8 | `adb backup`/aturan backup (Fase 5) | Token tidak ikut ter-backup |
| S9 | Fase 5: redirect OAuth dengan `state` salah / host lain | Ditolak |
| S10 | Scan CVE dependency + MobSF atas APK release | Tidak ada High/Critical yang belum ditangani |
| S11 | Fitur SMS: nomor kontak tidak muncul di log/crash log/export; SMS hanya ke kontak terdaftar; batas jumlah SMS per insiden dipatuhi; izin SMS tidak diminta bila fitur nonaktif | Semua terpenuhi |
| S13 | Arsip backup berbahaya: entri `../ridecomp-docs`, path absolut, zip bomb, entri tak dikenal, manifest palsu | Ditolak tanpa menulis di luar folder sementara; tidak crash |
| S12 | Kartu share & export: tidak memuat koordinat teks, nama kontak, atau data di luar pilihan pengguna; file sementara terhapus | Semua terpenuhi |

## 4. Uji lapangan (checklist)

**Perangkat uji minimal:** 1 HP low-end (RAM 2–3 GB) dari merek yang dikenal agresif mematikan app (mis. Xiaomi/Redmi, Oppo/Realme, Vivo, atau Infinix), + 1 HP lain bila ada.

| # | Skenario | Lulus jika |
|---|---|---|
| L1 | Ride 1 jam, layar mode redup otomatis | Tidak ada gap > 5 dtk; jarak selisih ≤ 3% vs Strava app/rute referensi |
| L2 | Ride 3 jam | Ride utuh; konsumsi baterai dicatat (target ≤ 35%) |
| L3 | Layar dimatikan 20 menit di tengah ride | Rekaman jalan terus |
| L4 | Force stop app di tengah ride | Saat dibuka: recovery muncul, data sebelum kill utuh |
| L5 | Lampu merah / berhenti 2 menit | Auto-pause & resume benar; moving time tidak bertambah |
| L6 | Matikan HR strap 1 menit lalu nyalakan | Peringatan muncul, reconnect otomatis, HR tercatat lagi |
| L7 | Import GPX, keluar jalur 2x | Alert off-route & back-on-route tepat |
| L8 | Mode pesawat + GPS, peta offline | Peta & rute tampil |
| L9 | Upload FIT manual ke Strava | Diterima; speed, HR, cadence, power, elevasi tampil |
| L10 | Panas & sinar matahari | Angka tetap terbaca; app tidak lag |
| L14 | Berhenti di lampu merah 5x + istirahat 20 mnt di warung | Jeda & lanjut otomatis tepat; prompt "Selesai ride?" muncul; tidak ada resume palsu saat diam |
| L15 | Ride ≥ 1 jam dengan pengingat minum 20 mnt | Pengingat muncul sesuai waktu bergerak |
| L16 | Mulai ride dengan baterai ~22% hingga < 10% | Mode hemat & kritis aktif otomatis; ride tetap utuh |
| L17 | Uji insiden aman (HP di holder dijatuhkan ke permukaan empuk saat berjalan pelan, lalu diam) + 10 km jalan berlubang | Hitung mundur muncul pada uji jatuh; tidak ada alarm palsu di jalan berlubang; SMS uji diterima kontak |
| L18 | Ride mengikuti GPX dengan ≥ 2 tanjakan & ≥ 5 belokan | Peringatan tanjakan 500 m & belokan 150/30 m tepat; halaman tanjakan akurat |
| L19 | 3 ride di rute yang sama dengan segmen | Effort & PR terdeteksi; live segment menampilkan selisih vs PR |
| L20 | Backup → simpan ke Google Drive → uninstall → install → restore | Semua data kembali; kontak darurat diminta diisi ulang |

### 4b. Uji lapangan Fase 11 (`14-PHASE11-PLAN.md`)
| # | Skenario | Lulus bila |
|---|---|---|
| A1 | Ride ≥ 30 mnt dengan sensor HR, buka detail | Grafik kecepatan/elevasi/HR tampil < 1 dtk di HP 2 GB; angka sama dengan ringkasan; zona HR berjumlah 100% |
| A2 | Ride tanpa HR & tanpa power meter | Bagian HR berupa kartu ajakan; zona power berlabel "Perkiraan" dengan FTP & berat yang dipakai |
| A3 | Rute sprint yang sama 2 hari (contoh Jl. Soekarno Hatta, ±3 mnt) | Hari ke-2: bagian Hasil menampilkan segmen/usaha terbaik dengan PR bila lebih cepat |
| A4 | Tema Terang & Gelap, termasuk layar ride di bawah matahari | Semua teks terbaca (kontras ≥ 4.5:1), tidak ada elemen hilang |
| A5 | Lihat peta saat online, lalu mode pesawat di area yang sama | Peta tetap tampil dari cache |
| A6 | Setelah ride di Jawa Tengah, terima tawaran "Unduh peta wilayahmu" via Wi‑Fi, lalu navigasi rute dengan mode pesawat | Tawaran menyebut wilayah yang benar tanpa menyalakan GPS tambahan; peta & navigasi tampil penuh; ukuran sesuai indeks ±5% |
| A7 | Anggaran performa (`14-PHASE11-PLAN.md` §8) dengan build rilis | Semua batas terpenuhi; angka dicatat di Feature Report |

### 4c. Uji lapangan Fase 12 (`15-PHASE12-PLAN.md`)
| # | Skenario | Lulus bila |
|---|---|---|
| W1 | Jam tangan dengan broadcast HR menyala, Sensor → Pindai | Muncul (langsung atau via "Tampilkan semua perangkat"), terpasang sebagai sensor HR |
| W2 | Ride 30 mnt dengan HR dari jam tangan | Data HR ≥ 1 nilai/dtk rata-rata, putus-sambung pulih otomatis, zona & analisa HR benar |
| W3 | Perangkat Bluetooth tanpa layanan HR dipilih di pemindaian luas | Pesan "tidak membagikan detak jantung", koneksi ditutup, tidak crash |
| W4 | (Bila T12.5 lolos) Aolon eksperimental, ride 30 mnt | HR terbaca sepanjang ride; berhenti mengirim perintah saat ride selesai; tidak memengaruhi sensor lain |

## 5. Acceptance criteria per fase
- **Semua fase:** setiap task punya Feature Report yang diterima; perintah verifikasi lulus; LeakCanary & StrictMode bersih (`09-RELIABILITY-RULES.md`).
- **Fase 0:** app terpasang di HP fisik; tooling kualitas, penjaga arsitektur, dan fondasi keamanan (T0.13) berjalan; S4, S6, S10 lulus.
- **Fase 1:** unit test lulus; L1, L3, L4, L5, L9 (tanpa sensor), L14, L15, L16, R1, R2, L11, L12, S5, S7 lulus.
- **Fase 2:** semua tes parser lulus; S1 lulus; L6, L13 lulus; L9 dengan data sensor lulus; L17, L20, S11, S13 lulus; R1 diulang dengan sensor simulasi.
- **Fase 3:** tes route tracker, tanjakan & belokan lulus; S2, S3 lulus; L7, L8, L18 lulus.
- **Fase 4:** tes best efforts, segmen, beban latihan, kartu share lulus; L19, S12 lulus.
- **Fase 5:** upload otomatis + antrian offline berhasil 3x berturut-turut; S8, S9 lulus.
- **Rilis MVP (akhir Fase 2):** L1–L6, L9–L17, L20, R1, R2, S1–S7, S10, S11, S13 lulus di HP low-end.

## 6. Pencatatan hasil uji lapangan
Untuk setiap uji lapangan catat: tanggal, HP & versi Android, durasi, mode layar, sensor, % baterai awal/akhir, temuan/bug. Simpan di `docs/testing/field-tests.md` (dibuat agent di Fase 1).
