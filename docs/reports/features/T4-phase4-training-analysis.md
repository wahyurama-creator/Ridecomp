# Feature Report — Fase 4: Latihan & analisis (T4.0–T4.6)

**Tanggal:** 2026-11-14
**Branch:** satu branch per task (`feature/T4.x-…`, `docs/…`), semua di-merge ke `main` dengan `--no-ff` dan di-push.
**Status:** Menunggu review pemilik. Uji perangkat dengan **GPS simulasi** di realme RMX3830 sudah dilakukan; **uji DoD dengan 3 ride nyata di rute yang sama belum**.

## 1. Ringkasan
- **Lisensi open source (T4.0):** Setelan → Lisensi open source. Isinya teks resmi MapLibre (BSD-2), komponen C++ yang dibundel, MapLibre Gestures, library Apache-2.0, dan ODbL OpenStreetMap (D-65).
- **Rekor (T4.1):**
  - jarak tercepat 1–100 km;
  - power terbaik 5 dtk–60 mnt (power meter saja);
  - ride terjauh, tanjakan terbanyak, rata-rata tercepat (≥ 20 km).
  - Pause tidak dihitung, dan rekor dipisah per jenis profil.
  - Tampil di tab **Rekor** dan sebagai kartu **"PR baru"** di Ringkasan.
  - Ride lama dianalisis sekali (D-66, D-67, D-69).
- **Segmen pribadi (T4.2):**
  - dibuat dari detail aktivitas (slider di grafik, D-68) atau dari tanjakan rute ("Jadikan segmen");
  - dicocokkan otomatis setelah ride disimpan, termasuk ke ride lama saat segmen baru dibuat;
  - tab **Segmen** dan detail segmen berisi rekor, 5 effort tercepat, tren, dan tombol hapus (D-70).
- **Live segmen (T4.3):**
  - banner "mulai" saat mendekati start;
  - halaman segmen #17 berisi selisih vs PR, progres, waktu, PR, HR, dan speed;
  - banner hasil di akhir dan getar saat mulai/selesai (D-71).
- **Beban latihan (T4.4):**
  - TSS (power + FTP) atau TRIMP (HR) per ride;
  - Fitness/Kelelahan/Form harian, tab **Latihan** dengan tren 8 minggu, minggu ini, dan zona HR;
  - label "estimasi, bukan nilai medis" (D-72).
- **Kartu share (T4.5):**
  - Story 1080×1920 / Kotak 1080×1080, berisi garis rute tanpa peta dan tanpa koordinat, maksimal 4 statistik, dan jumlah PR baru;
  - ujung rute 500 m disembunyikan secara default;
  - dibagikan lewat share sheet dan dihapus saat app dibuka berikutnya (D-73).
- **Test (T4.6):** ditulis bersama tiap task (lihat §6).
- **Permintaan pemilik (dicatat, belum dikerjakan):** panduan pemakaian untuk pengguna awam dan pilihan level pesepeda saat onboarding, sebagai **Fase 4G** setelah fitur inti selesai.

## 2. Perubahan per lapisan
| Lapisan | Isi utama |
|---|---|
| `:core` | `engine.track.RideTrack` (track ride dalam array primitif, maks 24 jam); `engine.efforts` (`BestEffortCalculator`); `engine.segments` (`SegmentBuilder`, `SegmentMatcher`, `LiveSegmentTracker`/`Follower`); `engine.training` (`TrainingLoadCalculator`, `TrainingTrend`, `ZoneTime`); `engine.share` (`ShareCardLine`). Kerangka analisis per langkah berversi (`AnalyzeActivitiesUseCase` + `AnalysisStep`: 1 rekor, 2 segmen, 3 beban), `AnalysisRequests`, `RecomputeTrainingUseCase`. Repository baru `BestEffort`, `Segment`, `Training`, `ActivityAnalysis`. `RideReminders` dipisah dari `RideRecorder` (KT-05) |
| `:data` | Skema **v6** satu migrasi (D-66): `best_efforts`, `segments`, `segment_points`, `segment_efforts`, `daily_training`, kolom `activities.analysis_version`; DAO dan repository; test SQL migrasi + test instrumented migrasi dan query rekor di perangkat |
| `:platform` | `AndroidShareCardFiles` (cache/share + FileProvider); service memuat segmen + PR saat ride GPS mulai dan memberi getar live segmen |
| `:ui-common` | `TrackSketch`, `TrendChart` (multi-seri, gaya garis), `LineChart.rangeX`, token dimensi baru |
| `:feature-training` | Modul kini terisi: rekor, kartu PR baru, segmen (daftar/detail/editor), latihan, kartu share, lisensi tidak (ada di settings) |
| `:feature-history` | Tab Aktivitas · Rekor · Segmen · Latihan (isi lewat slot dari `:app`, ARCH-02); aksi "Buat segmen" dan "Bagikan kartu" di detail |
| `:feature-ride` | Slot kartu PR di Ringkasan; halaman & banner live segmen |
| `:feature-routes` | "Jadikan segmen" pada tanjakan rute |
| `:feature-settings` | Layar Lisensi open source |
| `:app` | Rute baru (segmen, editor segmen, kartu share, lisensi), wiring Koin, `AppViewModel` menjalankan analisis & membersihkan kartu share |

## 3. Dependency baru
Tidak ada. Fase 4 memakai library yang sudah ada: Compose `GraphicsLayer` untuk menangkap kartu share, dan Canvas untuk grafik. `org.json` (khusus test) sudah diperbarui di gerbang Fase 3.

## 4. Resource & siklus hidup
| Resource | Diakuisisi | Dilepas | Cara verifikasi |
|---|---|---|---|
| Analisis setelah ride (baca track, hitung) | `AppViewModel` saat app dibuka, saat jumlah ride berubah, dan saat segmen baru dibuat | Dibatalkan bersama `viewModelScope`; dilanjutkan di pembukaan berikutnya (`analysis_version`) | Uji perangkat: backfill 9–12 ride; `dumpsys` tanpa service/wakelock setelah simpan |
| Track di memori | Per ride saat analisis | Lepas setelah langkah selesai; batas 86.400 sampel (MEM-01) | Review; test |
| Bitmap kartu share | Saat tombol Bagikan | Salinan software di-`recycle` setelah encode (MEM-06) | Review; uji perangkat |
| File PNG kartu | `cacheDir/share` saat dibagikan | Dihapus saat app dibuka berikutnya | Uji perangkat: 1 file → 0 setelah restart |
| Segmen live | Dimuat saat ride GPS mulai | Bersama recorder saat ride selesai | Uji perangkat |

## 5. Checklist kode
- **Arsitektur:**
  - semua perhitungan ada di `:core` (Kotlin murni, ber-test);
  - modul feature tetap tidak saling bergantung (konten `:feature-training` disisipkan ke Riwayat/Ringkasan lewat slot dari `:app`);
  - `RideRecorder` tetap di bawah batas KT-05 (pemisahan `RideReminders`, `LiveSegmentFollower`).
- **Konstanta:** `BestEffortConfig`, `SegmentConfig`, `LiveSegmentConfig`, `TrainingConfig`, `ShareCardConfig`, `AnalysisConfig`; string en/id dengan `plurals`.
- **Konkurensi:**
  - analisis satu instance dengan mutex;
  - perhitungan berat di `dispatchers.default`, I/O di `dispatchers.io`;
  - pemicu memakai `collectLatest`.
- **Data:** satu migrasi eksplisit v5→v6 dengan test JVM dan test perangkat. Tidak ada destructive migration, dan data ride lama tetap utuh (dicek di HP).

## 6. Hasil testing
- **Unit test:** **461 test**, 0 gagal (Fase 3: 411).
- **Coverage `:core`:** 95,1%.
- **Test yang baru:**
  - **best efforts:** jalur sintetis, termasuk pause di tengah;
  - **pencocokan segmen:** lewat sebagian, jalan memutar, arah terbalik, noise GPS, dilewati dua kali, kotak di luar;
  - **live segmen:** mendekat, mulai, selisih vs PR, PR pertama, arah terbalik, keluar jalur;
  - **beban latihan:** nilai referensi TSS & TRIMP, rumus CTL/ATL, tren inkremental setelah ride dihapus, zona HR;
  - **kartu share:** pemotongan 500 m dan normalisasi;
  - semua ViewModel baru, plus `AppModulesTest`.
- **Instrumented test di perangkat (realme):** migrasi 5→6 dengan data, semua migrasi, dan query rekor (MIN/MAX per kunci, seri, idempoten) — 6/6 lolos.
- **`./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`:** ✅. `assembleRelease` ✅.
- **APK release per-ABI:** 13,8 MB arm64 / 10,9 MB armeabi-v7a (target < 20 MB).
- **Uji perangkat (GPS simulasi):**
  - migrasi pada 9 ride lama → rekor langsung terisi;
  - kartu "1 PR baru" di Ringkasan, dan tab Rekor ikut berubah setelah simpan;
  - segmen dari ride (slider) → otomatis cocok dengan 3 ride lama;
  - "Jadikan segmen" dari tanjakan;
  - live segmen: halaman #17 dengan selisih −0:11 → +0:16, lalu effort ke-4 tersimpan;
  - tab Latihan: penjelasan cara mendapat beban (simulasi tanpa HR);
  - kartu share PNG 1080×1920 identik dengan pratinjau, share sheet terbuka, file terhapus setelah restart;
  - setelah simpan: service dan wakelock tidak aktif, tidak ada error.
- **Bug yang ditemukan dan diperbaiki selama fase:**
  1. jalan memutar lolos pencocokan segmen karena garis segmen yang disederhanakan hanya punya 2 titik (D-70, ditemukan oleh test);
  2. segmen loop langsung "selesai" di titik start (dicegah: harus menempuh minimal 50% panjang);
  3. `profile_kind` hampir disimpan dengan format berbeda dari `activity_profiles.kind`;
  4. rute lama tanpa tanjakan tersimpan tidak bisa "Jadikan segmen" (kini memakai tanjakan yang ditampilkan).
- **Belum:**
  - DoD: PR & segmen pada 3 ride nyata di rute yang sama, grafik latihan dengan ride ber-HR sungguhan;
  - tampilan tab Latihan ketika sudah berisi data belum terlihat di perangkat (simulator tidak punya HR), baru diverifikasi lewat test ViewModel.

## 7. Keamanan
- **Checklist manual §D:**
  1. **Input luar:** tidak ada input baru dari luar. Nama segmen dipotong 60 karakter. Nilai tersimpan dibaca toleran (kunci/jenis yang tidak dikenal diabaikan).
  2. **Komponen:** tidak ada yang baru ter-export.
  3. **Data sensitif:** HR/beban dan titik segmen hanya di DB internal. Lokasi tidak di-log.
  4. **File:** kartu share di `cacheDir/share`, nama dibuat app, dihapus saat app dibuka berikutnya.
  5. **PendingIntent:** tidak ada yang baru.
  6. **Jaringan:** tidak ada (tanpa izin INTERNET, dicek di merged manifest).
  7. **SQL:** konstanta + bind args.
  8. **Rahasia:** tidak ada.
  9. **Izin baru:** tidak ada.
  10. **Dependency:** tidak ada yang baru.
  11. **Pesan error:** generik (panjang segmen tidak valid, tanpa posisi GPS).
  12. **Fitur debug:** tidak ada yang baru.
  13. **SMS & kontak:** tidak berubah.
  - **FileProvider:** ditambah `cache-path share/` saja, dengan grant baca sementara (SEC-PLT-04).
- **Merged manifest release:** INTERNET 0. Komponen ter-export hanya launcher dan `ProfileInstallReceiver` (izin `DUMP`).
- **Scan CVE (OSV, `security/phase4-osv-scan.txt`):** 642 koordinat, **0 temuan di kode yang masuk APK**. Ada 14 High/Critical, tapi hanya di tool build dan sama dengan Fase 3. Dua temuan lama hilang: okio 2.2.2 di classpath lint dan org.json lama.
- **Threat model §E:** baris Kartu share diperbarui, ditambah baris Rekor/segmen/beban.
- **Belum:** MobSF dan Dependency-Check NVD (menunggu pemilik, D-16).

## 8. Penyimpangan / keputusan
D-65…D-73 di `DECISIONS.md`. Yang paling perlu ditinjau:
- **D-69:** rekor per jenis profil; ride tanpa profil = Road.
- **D-70:** waktu segmen = elapsed (berhenti ikut dihitung), sesuai kebiasaan Strava.
- **D-71:** halaman live segmen menggantikan halaman pertama.
- **D-72:**
  - HR istirahat default 60 dan HR maks 220 − umur bila kosong;
  - daftar rekor tidak diulang di layar Latihan.
- **D-73:** kartu share hanya dari detail aktivitas (Ringkasan langsung membuka detail setelah Simpan). Zona privasi (SEC-STO-05) belum ada.

## 9. Risiko & bug yang diketahui
- **Ambang segmen dan live segmen belum dikalibrasi di jalan nyata:** start/end 25 m, jalur 30 m, keluar jalur 50 m/15 dtk. GPS di bawah pohon atau gedung bisa membuat effort tidak tercatat.
- **TRIMP bergantung pada HR maks/istirahat:** nilai default ditandai sebagai estimasi tapi tanda per ride belum ditampilkan.
- **Event FIT `segment_start/end`** belum ditulis.
- **Analisis ulang besar:** setiap segmen baru memicu pencocokan ulang semua ride. Untuk ratusan ride ini butuh beberapa detik, tapi tetap berjalan di background dan bisa dilanjutkan.
- **Kartu share** hanya memuat logo teks "RideComp" (belum ada logo grafis).

## 10. Cara menguji (untuk pemilik proyek)
1. **Rekor:** simpan 2–3 ride → Riwayat → Rekor. Ride yang lebih cepat/jauh menampilkan kartu "PR baru" di Ringkasan.
2. **Segmen:**
   - Detail aktivitas → "Buat segmen dari ride ini" → geser dua pegangan → Simpan.
   - Ride lagi melewati bagian itu (searah). Saat mendekati start muncul banner "Segmen: … · mulai"; halaman utama berganti ke selisih vs PR; di akhir muncul banner hasil.
   - Riwayat → Segmen → detail: effort tercepat dan tren.
3. **Tanjakan → segmen:** detail rute → daftar tanjakan → "Jadikan segmen".
4. **Latihan:** isi HR maks (atau tahun lahir) di profil, ride dengan sensor HR → Riwayat → Latihan. Kartu Fitness/Kelelahan/Form, tren, minggu ini, dan zona akan terisi.
5. **Kartu share:** detail aktivitas → "Bagikan kartu" → pilih Story/Kotak, statistik, dan tema → Bagikan → WhatsApp. Pastikan awal/akhir rute tidak tampil.
6. **Setelah simpan:** jalankan `dumpsys` di `docs/testing/field-tests.md`. Tidak boleh ada service, wakelock, atau GPS aktif.
