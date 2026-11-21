# Fase 11 — Pengukuran build release (sebagian)

**Tanggal:** 2026-11-21 · **Build:** release 0.9.3 (versionCode 93, R8, ditandatangani kunci rilis), **hanya lokal**: tidak ada GitHub Release, kenaikan versi, maupun catatan rilis. Rilis berikutnya digabung dengan Fase 12–13 (permintaan pemilik).
**HP:** realme RMX3830. Build debug dicopot atas izin pemilik; data aplikasi disalin lebih dulu ke `~/RideComp-phone-backup-2026-11-21/app-data.tar` (2 ride, diverifikasi). Release dipasang dalam keadaan bersih, lalu onboarding diselesaikan.

| Anggaran §8 | Hasil | Status |
|---|---|---|
| Ukuran APK naik < 1 MB per ABI | arm64-v8a 14.429.686 B (0.9.3: 14.306.668, **+123 KB**); armeabi-v7a 11.442.226 B (+123 KB); keduanya < 20 MB | ✅ |
| Layar ride tidak berubah, tanpa kerja latar/GPS baru | Tidak ada WorkManager/alarm/service baru; unduhan peta hanya saat aplikasi terbuka (kode + uji T11.12) | ✅ |
| Buka aplikasi (indikasi umum) | Cold start 5×: 300/279/284/278/283 ms, rata-rata **285 ms** | ✅ (tidak ada batas khusus) |
| Kelancaran scroll (≥ 90% frame < 16 ms) | Setelan, Peta, dan tab Riwayat/Latihan/Rute: 1.170 frame, janky 2,74%, persentil 50/90/95/99 = 11/16/21/36 ms (sekitar 90% < 16 ms, batas pas) | ✅ dengan catatan |
| Memori | Beranda setelah onboarding PSS 57,7 MB; setelah membuka layar-layar lalu kembali 84,6 MB | ℹ️ |
| Buka detail ride < 1 dtk (ride simulasi 34 mnt, 11,7 km; jumlah titik grafik dibatasi ±300 sehingga ride 1 jam setara) | Rekaman layar release: konten tampil ±0,2 dtk setelah ketuk, peta lengkap ±0,5 dtk | ✅ |
| Tidak ada frame > 700 ms; scroll detail ≥ 90% frame < 16 ms | 1.842 frame (buka detail + 3× scroll penuh): janky 2,33%, persentil 50/90/95/99 = 11/16/18/26 ms, frame terburuk **53 ms** | ✅ |
| Memori saat detail terbuka naik < 20 MB | Daftar Riwayat 85,6 MB → detail terbuka (semua bagian sudah di-scroll) 104,7 MB = **+19,1 MB**; kembali ke daftar setelah 10 dtk 95,7 MB (±10 MB tertahan, terutama native/grafik: cache peta & gambar) | ⚠️ pas di batas; diukur terhadap layar daftar karena belum ada angka "sebelum Fase 11" di release |
| Rekor durasi < 300 ms untuk ride 3 jam | JVM (laptop) median **3,8 ms** untuk 10.800 sampel (semua jendela jarak/kecepatan/power); perkiraan di HP low-end 10–30× lebih lambat ≈ 40–110 ms | ✅ (indikasi) |
| Tanpa crash | Tidak ada FATAL | ✅ |

**MobSF:** ditunda ke APK rilis gabungan Fase 11–13 (pilihan pemilik; butuh Docker).
**Uji lapangan disimulasikan** (pemilik sakit, atas permintaannya): lihat [`phase11-field-sim.md`](phase11-field-sim.md). Data ride dipindahkan ke release lewat fitur Backup/Restore aplikasi; data uji dihapus setelahnya.
**Keadaan HP akhir:** build release 0.9.3 dengan dua ride asli pemilik (Gowes Pagi, Gowes Siang), sensor tersimpan, dan profil hasil restore; tanpa data uji. Salinan mentah data debug lama tetap ada di `~/RideComp-phone-backup-2026-11-21/`.
