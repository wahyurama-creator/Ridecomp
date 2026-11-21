# Fase 11 — Pengukuran build release (sebagian)

**Tanggal:** 2026-11-21 · **Build:** release 0.9.3 (versionCode 93, R8, ditandatangani kunci rilis), **hanya lokal**: tidak ada GitHub Release, kenaikan versi, maupun catatan rilis. Rilis berikutnya digabung dengan Fase 12–13 (permintaan pemilik).
**HP:** realme RMX3830. Build debug dicopot atas izin pemilik; data aplikasi disalin lebih dulu ke `~/RideComp-phone-backup-2026-11-21/app-data.tar` (2 ride, diverifikasi). Release dipasang dalam keadaan bersih, lalu onboarding diselesaikan.

| Anggaran §8 | Hasil | Status |
|---|---|---|
| Ukuran APK naik < 1 MB per ABI | arm64-v8a 14.429.686 B (0.9.3: 14.306.668, **+123 KB**); armeabi-v7a 11.442.226 B (+123 KB); keduanya < 20 MB | ✅ |
| Layar ride tidak berubah, tanpa kerja latar/GPS baru | Tidak ada WorkManager/alarm/service baru; unduhan peta hanya saat aplikasi terbuka (kode + uji T11.12) | ✅ |
| Buka aplikasi (indikasi umum) | Cold start 5×: 300/279/284/278/283 ms, rata-rata **285 ms** | ✅ (tidak ada batas khusus) |
| Kelancaran scroll (≥ 90% frame < 16 ms) | Setelan, Peta, dan tab Riwayat/Latihan/Rute: 1.170 frame, janky 2,74%, persentil 50/90/95/99 = 11/16/21/36 ms (sekitar 90% < 16 ms, batas pas) | ✅ dengan catatan |
| Memori | Beranda setelah onboarding PSS 57,7 MB; setelah membuka layar-layar lalu kembali 84,6 MB (Java 15,7 / Native 26,6 / Graphics 14,6 MB, tanpa GC paksa) | ⏳ pembanding "detail terbuka vs sebelum Fase 11" belum bisa diukur |
| Buka detail ride 1 jam < 1 dtk; memori saat detail terbuka naik < 20 MB; rekor durasi < 300 ms untuk ride 3 jam | Belum ada ride di HP (release tanpa GPS simulasi) | ⏳ diukur setelah uji lapangan A1–A7 pemilik |
| Tanpa crash | Tidak ada FATAL | ✅ |

**MobSF:** ditunda ke APK rilis gabungan Fase 11–13 (pilihan pemilik; butuh Docker).
**Catatan:** HP kini memakai build release. Data debug lama tidak dikembalikan, dan bisa dikembalikan dari salinan bila build debug dipasang lagi untuk pengujian.
