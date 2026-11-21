# Fase 11 — Uji lapangan A1–A3 (disimulasikan)

**Tanggal:** 2026-11-21 · **HP:** realme RMX3830 · **Alasan:** pemilik sedang sakit dan meminta pengujian disimulasikan semirip mungkin. Uji lapangan sungguhan (matahari, GPS asli, mode pesawat) tetap disarankan saat pemilik sudah bisa bersepeda.
**Cara:** build debug dengan **GPS simulasi** (lingkaran 3 km di Yogyakarta, 7±2 m/s, berhenti 30 dtk tiap 2 mnt) dan **sensor simulasi** yang mengirim paket BLE sungguhan (HR dengan putus 12 dtk tiap 5 mnt, speed/cadence, power, smart trainer). Data asli pemilik dikembalikan lebih dulu, lalu data uji dihapus di akhir. Perubahan setelan sementara (HR maks, peta online) dikembalikan.

| Uji | Skenario | Hasil |
|---|---|---|
| A1 ride ≥ 30 mnt dengan HR | 34 mnt (dipersingkat dari 60 atas permintaan pemilik), 11,72 km, bergerak 28:07 | ✅ HR rata-rata 141/maks 164, power 172 W (NP 176), cadence 85; jeda otomatis bekerja; 16 rekor baru termasuk kecepatan 30 dtk–20 mnt & power 30 dtk/3 mnt (T11.8); zona power Z1–Z7 (FTP perkiraan 175 W); **zona HR** (HR maks 185 sementara): Z1 5%, Z2 8%, Z3 50%, Z4 36%, Z5 0%, batas 60/70/80/90% tepat |
| Sensor putus | HR simulasi putus ±12 dtk | ✅ ubin HR "--" + label "lost" kuning, ikon kuning, banner "Sensor HR terputus · tetap merekam"; tanpa angka basi; kembali normal otomatis. Bila smart trainer terpasang, HR diambil dari trainer (sesuai rancangan `SensorFusion`) |
| A2 rute sama 2× | Segmen "Uji Segmen" 0,99 km dari A1 (4 putaran = 4 usaha, rekor 2:47), lalu ride kedua 2,98 km | ✅ kartu segmen live saat ride ("lebih cepat dari rekor", selisih, sisa jarak), banner akhir "+0:02 dari rekormu"; kartu Hasil A2: usaha terbaik "Terbaik ke-2/ke-3", segmen tercatat (peringkat 4 → tanpa label, sesuai aturan T11.7: seri & putaran ride sebelumnya dihitung) |
| A3 navigasi + peta offline (pengganti mode pesawat) | Unduh peta DIY (21 MB, ±15 dtk), Peta online **Mati**, cache dikosongkan (66 kB), impor GPX lingkaran, ride mengikuti rute | ✅ detail rute: peta dari file wilayah + "Peta area rute tersedia offline"; halaman Peta saat ride: tile jalan dari file DIY, garis rute, bagian dilalui, titik posisi, atribusi "© OpenStreetMap contributors, Protomaps"; cache tetap 66 kB (tidak ada unduhan online) |

**Logcat selama semua uji:** tanpa FATAL, tanpa ANR, tanpa leak, **tanpa temuan StrictMode aplikasi** (`RideComp/StrictMode:W` kosong).
**Backup → restore ke release:** "Backup dibuat dengan 5 ride", restore di release memulihkan ride, sensor tersimpan, dan profil (kontak darurat sengaja tidak ikut, sesuai SEC-BK).
**Catatan kecil (bukan bug):** kata "Backup" sengaja dipakai sebagai kata serapan di UI Indonesia; tombol "Ke start" selalu ada di halaman Peta (D-92).
