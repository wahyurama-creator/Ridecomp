# Laporan Uji — Pengguna awam & sensor simulasi (TU.1–TU.3)

Tanggal: 2026-11-17 · Perangkat: realme RMX3830 (Android 15) · Keputusan: D-86, D-87

## 1. Cara uji
- Data aplikasi dihapus (atas permintaan pemilik). Onboarding sampai ride pertama dijalani seperti pemula.
- Sensor HR, speed/cadence, dan power diuji dengan **sensor simulasi debug** (Developer → Simulated sensors). Sensor ini mengirim paket BLE asli ke parser aplikasi, dan HR strap sengaja diputus 12 detik setiap 5 menit.
- Pemulihan diuji dengan dua cara: memasang APK baru saat ride dijeda, dan `force-stop` saat ride berjalan.

## 2. Temuan & perbaikan
| # | Temuan | Perbaikan |
|---|---|---|
| 1 | Onboarding: 3 tombol "Allow" tanpa keterangan wajib/opsional | Tombol "Izinkan & lanjut" (semua izin sekaligus), label Wajib/Opsional |
| 2 | Profil onboarding penuh istilah teknis | Hanya berat badan & sepeda; sisanya di "Pengaturan lanjutan (opsional)" |
| 3 | "Mulai" dan "Lewati" di langkah 4 melakukan hal yang sama | Tautan ganda dihapus |
| 4 | Beranda: GPS "Siap" sebelum ada sinyal; kotak sensor tidak bisa diketuk | "Diizinkan"; kotak sensor → tab Sensor, "Tambah" |
| 5 | Persiapan ride: sensor opsional sebagai peringatan; tombol START harus digulir | Netral (ikon info); tombol selalu terlihat |
| 6 | Layar ride tanpa sensor setengahnya "– –" | Kolom sensor diganti metrik HP (power → estimasi) |
| 7 | "Finish & save" lalu "Save" lagi; kotak kosong di ringkasan; tautan usang | "Selesai" → "Simpan ride"; kotak tanpa data disembunyikan |
| 8 | Riwayat: label kuning "Belum diekspor" seperti error | Hanya label positif |
| 9 | Sensor: "Scan again" sebelum pernah scan; izin Bluetooth butuh dua ketukan | "Cari sensor", langsung meminta izin |
| 10 | **Bug pemulihan:** rata-rata/maks HR, cadence, power, NP, kalori, dan lap berjalan hilang setelah crash/kill | Sampel tersimpan diputar ulang per halaman saat pemulihan; lap mulai dari akhir lap tersimpan; test regresi |
| 11 | Kartu pemulihan: label "Resum/e" terpotong; START tetap tampil | Lanjutkan lebar penuh; START disembunyikan selama ada ride yang belum disimpan |
| 12 | LAP tanpa umpan balik di halaman utama | Banner "✓ Lap n · waktu · jarak" 4 detik |
| 13 | Tren latihan kosong saat baru satu hari | Teks "garis tren muncul setelah ride di beberapa hari" |
| 14 | LeakCanary: `UniGestureDetector` (framework realme/ColorOS) | Ditandai sebagai library leak (RULE-EXCEPTION, bukan kode app) |

## 3. Fitur sensor yang terbukti bekerja (simulasi)
- Scan menemukan 3 perangkat; pairing berhasil; baterai sensor (82%) tersimpan dan tampil di beranda.
- Saat ride: HR beserta zona (Z1–Z4), cadence 80–91 rpm, dan power 3s. Saat berhenti, cadence dan power 0 dan HR turun. Ikon sensor tampil di bilah status.
- Sensor putus: banner "Heart rate sensor lost · Reconnecting… · still recording", kolom HR bertanda "lost", lalu tersambung kembali sendiri.
- Ringkasan & detail: rata-rata/maks HR, cadence, power, kalori; rekor power terbaik 5 dtk dan 1 menit; grafik HR, cadence, dan power; tabel lap dengan HR.
- File FIT (dibaca dengan parser independen): CRC benar; 94 record dengan HR (93), cadence (92), dan power (93); 2 lap; sesi lengkap (avg/max HR 143/164, power 180/268, NP 177, cadence 86, 17 kkal). Total lap sama dengan jarak ride.
- Beban latihan dari power meter (TSS); kartu share dengan zona HR dan lencana rekor.

## 4. Belum / catatan
- Strava: menghubungkan ulang gagal karena **HTTP 500 dari server Strava** di app Strava, setelah akses dicabut dan diberikan ulang beberapa kali hari ini. Upload ride bersensor belum diuji. Alur yang sama sebelumnya dua kali berhasil.
- Verifikasi: `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug` ✅.
