# Feature Report — Fase 3: Navigasi rute GPX + desain canvas (T3.1–T3.13, U0–U3a)

**Tanggal:** 2026-11-13
**Branch:** satu branch per task (`feature/T3.x-…`, `feature/U…`, `fix/…`), semua di-merge ke `main` dengan `--no-ff`.
**Status:** Menunggu review pemilik. Uji perangkat dengan **GPS simulasi** di realme RMX3830 sudah dilakukan; **uji lapangan (DoD Fase 3) belum**.

## 1. Ringkasan
- **Rute GPX (T3.1–T3.2):**
  - impor dari tombol Import atau "Buka dengan RideComp", dengan parser milik sendiri yang aman (D-43, D-44, D-51);
  - daftar dan detail rute: statistik, profil elevasi, daftar tanjakan, "balik arah".
- **Navigasi saat ride (T3.6–T3.10):**
  - route tracker: proyeksi ke rute, sisa jarak, dan ETA;
  - alert off-route dan kembali ke rute (getar + bunyi + banner);
  - breadcrumb tanpa peta, serta profil elevasi dengan penanda posisi (D-46, D-47).
- **Tanjakan & belokan (T3.11–T3.13):**
  - deteksi saat impor;
  - peringatan tanjakan 500 m sebelumnya, dengan panel tanjakan ala ClimbPro (profil per 100 m berwarna pita grade);
  - peringatan belokan 150/30 m, plus field "Belokan berikutnya" (D-49, D-50, D-52).
- **Peta offline (T3.3–T3.5):**
  - MapLibre + file region PMTiles yang diimpor (D-64);
  - manajer Peta offline (#26), peta di detail rute (#7), dan halaman peta saat ride (#12) dengan follow mode;
  - breadcrumb tetap menjadi cadangan bila tidak ada peta atau mode hemat baterai aktif.
- **Desain canvas (U0–U3a, prioritas pemilik):**
  - design system `:ui-common` (D-54);
  - layar ride, Home, Pre-ride, Cari GPS, Ringkasan, Riwayat, Detail, onboarding 3 langkah, Setelan, editor halaman data, Sensor, Keselamatan, Backup, serta halaman tanjakan/belokan/peta (D-55–D-63).
- **Perbaikan keandalan:** pelanggaran StrictMode saat startup dihapus (D-53). Metadata verifikasi kini lengkap untuk clone baru (D-48).

## 2. Perubahan per lapisan
| Lapisan | Isi utama |
|---|---|
| `:core` | `engine.route`: `GpxParser` (streaming, tanpa DOCTYPE), `RouteBuilder`, `PolylineSimplifier`, `RouteTracker`, `ClimbDetector`, `TurnDetector`, `riddenUntil`. `engine.map`: `PmTilesHeader`, `OfflineMap`, batas impor. Use case rute, panduan, dan peta offline. Fake di `testFixtures` |
| `:data` | Skema **v5** (`routes`, `route_points`, `route_climbs`, `route_turns`) dengan migrasi eksplisit + test SQL; repository rute |
| `:platform` | Pembaca file GPX dari content URI; `AndroidOfflineMapStore` (salin tervalidasi ke `files/maps` via file `.part`); alert rute di service; perbaikan I/O startup (D-53) |
| `:ui-common` | Design system (token warna/dimensi/tipografi, BigButton, kartu, TopBar, BottomNav, toggle, chip, field teks, `GradeProfile`, status strip), `OfflineRouteMap` + style peta di kode |
| `:feature-routes` | Daftar, detail (peta, status offline, tanjakan), dan impor rute |
| `:feature-ride` | Halaman navigasi (peta bertile / breadcrumb), panel tanjakan, kartu belokan, banner off-route; semua layar ride versi desain |
| `:feature-settings` | Layar Peta offline; Setelan versi desain |
| `:app` | Route baru (rute, peta offline), intent-filter GPX, penghapusan izin jaringan dari library, wiring Koin |

## 3. Dependency baru
| Library | Versi | Scope | Alasan | Lisensi |
|---|---|---|---|---|
| `org.maplibre.gl:android-sdk-opengl` | 13.7.1 | runtime (`:ui-common`) | Renderer peta vektor offline (D-64). Varian OpenGL dipilih karena varian default mewajibkan Vulkan | **BSD-2-Clause** |
| ↳ transitif: `android-sdk-geojson`, `android-sdk-turf` 6.0.1; `maplibre-android-gestures` 0.0.4; OkHttp 4.12.0; Okio 3.6.0; Timber 5.0.1; Gson 2.10.1 | — | runtime | Dibawa MapLibre. OkHttp tidak pernah dipakai: app tanpa izin INTERNET dan `setConnected(false)` | Apache-2.0 (gestures: BSD-2/Apache-2.0) |

- Evaluasi SUP-07 tercatat di D-64.
- Lockfile hanya bertambah, tanpa versi yang turun.
- `verification-metadata.xml` dibuat ulang dengan `GRADLE_USER_HOME` kosong (+202 baris, tanpa penghapusan), lalu diverifikasi ulang dengan home kosong lain: **BUILD SUCCESSFUL** (§A.3).
- **Ukuran APK release per-ABI:** **13,6 MB** arm64 / **10,6 MB** armeabi-v7a (Fase 2: ±1,9 MB). Hampir seluruh tambahan berasal dari `libmaplibre.so` (11,0 / 8,1 MB tak terkompresi). Target < 20 MB tetap terpenuhi.

## 4. Resource & siklus hidup
| Resource | Diakuisisi | Dilepas | Cara verifikasi |
|---|---|---|---|
| `MapView` (MapLibre) | Composable peta tampil (detail rute, halaman peta ride) | Mengikuti lifecycle layar (`onStart/onResume/onPause/onStop`); `onDestroy` saat composable keluar | LeakCanary 0 leak setelah ride + simpan di RMX3830 |
| File impor `.part` | Impor peta | Rename atomik saat sukses; sisa `.part` dihapus setelah impor gagal (IOException/SecurityException) | Review; uji impor di perangkat |
| Pembacaan GPX | Impor | Stream ditutup (`use`) | `GpxParserTest`, uji perangkat |
| Alert getar/bunyi rute | Selama ride berpanduan | Bersama service rekaman | Uji perangkat (banner, getar) |

Setelah ride disimpan di perangkat: `dumpsys activity services` tidak menampilkan service, wakelock 0, dan GPS `ProviderRequest[OFF]` (BAT-01).

## 5. Checklist kode
- **Arsitektur:**
  - semua logika rute, tracker, deteksi, dan validasi PMTiles ada di `:core` (Kotlin murni, ber-test);
  - MapLibre hanya dipakai di `:ui-common`, sehingga modul feature tidak bergantung langsung padanya;
  - `MapPoint` membuat `:ui-common` bebas dari tipe `:core`.
- **Konstanta:**
  - `RouteConfig`, `GpxLimits`, `OfflineMapConfig`, `OfflineMapZoom`, dan `PmTilesHeader` (offset bernama);
  - warna peta di token `MapColors`;
  - teks en/id di `strings.xml`, kecuali atribusi OSM (`translatable=false`).
- **Memori:**
  - parser GPX streaming dengan batas byte/titik/kedalaman;
  - garis rute disederhanakan (Douglas-Peucker) sebelum digambar;
  - file peta dibaca langsung oleh renderer, tidak dimuat ke RAM.
- **Data:** satu migrasi eksplisit (v4→v5) dengan test, tanpa destructive migration.

## 6. Hasil testing
- **Unit test:** **411 test**, 0 gagal (Fase 2: 365). Yang baru:
  - `GpxParserTest` — DOCTYPE/entity ditolak, batas ukuran, nilai tidak valid;
  - `RouteTrackerTest` — rute bersilangan/berputar, timing off-route;
  - `ClimbsAndTurnsTest`, `ClimbTurnTrackerTest` — GPX referensi: belok 90°, hairpin, tikungan landai, noise GPS, rute tanpa elevasi;
  - `PmTilesHeaderTest`, `OfflineMapUseCasesTest`, `OfflineMapsViewModelTest`, `RiddenUntilTest`;
  - test ViewModel layar baru dan migrasi v5.
- **`./gradlew ktlintCheck detekt lint test koverVerify assembleDebug assembleRelease`:** ✅ (dengan `GRADLE_USER_HOME` kosong).
- **Coverage `:core`:** 96,7% baris.
- **Uji perangkat (realme RMX3830, GPS simulasi, GPX uji segi-7 dengan tanjakan 6%):**
  - banner belokan dan tanjakan, panel tanjakan, dan pola getar;
  - impor `.pmtiles` (6,6 MB), peta di detail rute, halaman peta saat ride (follow, geser, tombol ikuti posisi);
  - semua layar desain, kecuali onboarding (butuh hapus data app) dan hitung mundur insiden (akan mengirim SMS nyata);
  - service berhenti setelah simpan; LeakCanary 0 leak.
- **Bug yang ditemukan dan diperbaiki selama fase:**
  1. picker GPX tidak bisa memilih `.gpx` bertipe `octet-stream` (D-51);
  2. satu titik tanpa `<ele>` menghilangkan semua tanjakan (D-52);
  3. rute lama tanpa deteksi (D-52);
  4. disk I/O di main thread saat startup (D-53);
  5. clone baru gagal sync karena metadata verifikasi tidak lengkap (D-48);
  6. jejak "sudah dilalui" di peta berhenti di verteks terakhir, bukan di posisi (`riddenUntil`).
- **Belum: uji lapangan DoD** — ride ≥ 20 km mengikuti GPX dengan sengaja keluar jalur 2×, peta tanpa internet di mode pesawat, dan ketepatan waktu peringatan tanjakan/belokan (L7).

## 7. Keamanan
- **Checklist manual §D:**
  1. **Input luar:**
     - GPX diproses parser tanpa DOCTYPE/entity eksternal, dengan batas ukuran/titik/kedalaman dan nilai koordinat/elevasi divalidasi (SEC-IN-01..04);
     - header PMTiles divalidasi (magic, versi 3, tile MVT, bounds masuk akal) sebelum file disalin;
     - ukuran dibatasi 2 GB dan sisa ruang ≥ 200 MB;
     - nama file disanitasi (`[A-Za-z0-9_-]`, maks 60).
  2. **Komponen:**
     - intent-filter VIEW GPX ada di launcher, hanya `content://`, tanpa BROWSABLE (D-44);
     - MapLibre tidak menambah komponen;
     - yang ter-export tetap hanya launcher dan `ProfileInstallReceiver` (izin `DUMP`).
  3. **Data sensitif:** rute dan peta hanya di internal storage. Lokasi tidak di-log.
  4. **File:** peta disalin ke `files/maps` lewat `.part`, rename atomik. Tidak ada akses external storage.
  5. **PendingIntent:** tidak ada yang baru.
  6. **Jaringan:**
     - **tidak ada**: izin INTERNET dan ACCESS_WIFI_STATE dari MapLibre dihapus dari merged manifest (dicek: 0 kemunculan INTERNET);
     - `MapLibre.setConnected(false)`;
     - style tanpa layer teks, sehingga tidak butuh glyph/sprite online.
  7. **SQL:** bind args / konstanta.
  8. **Rahasia:** tidak ada (MapLibre tidak memakai API key).
  9. **Izin baru:** tidak ada izin berbahaya. ACCESS_NETWORK_STATE (normal) dipertahankan karena MapLibre memeriksanya.
  10. **Dependency:** MapLibre + transitif (§3), dievaluasi di D-64.
  11. **Pesan error:** impor peta/GPX menampilkan alasan umum (bukan file peta, versi, terlalu besar, ruang tidak cukup), tanpa detail teknis.
  12. **Fitur debug:** simulasi GPS tetap hanya di source set debug.
  13. **SMS & kontak:** tidak berubah (layar desain hanya menyamarkan nomor, D-61).
- **Scan CVE (OSV, `security/phase3-osv-scan.txt`):**
  - 643 koordinat dipindai, **0 temuan di kode yang masuk APK**;
  - 14 High/Critical ada di tool build saja, **identik dengan Fase 2**;
  - `org.json:json` (test saja) memiliki advisory Moderate GHSA-ghc9-756g-vrcv, diperbarui ke 20260814 yang memperbaikinya (`chore(deps)`, SUP-10).
- **Update dependency (SUP-10):**
  - semua library di version catalog sudah versi stabil terbaru, kecuali `org.json` (diperbarui);
  - MapLibre 13.7.1 adalah rilis stabil terbaru (rilis `*-globe` adalah pratinjau).
- **Lisensi:**
  - `generateLicenseReport` sukses;
  - runtime Apache-2.0, **ditambah BSD-2-Clause (MapLibre)** dan data peta **ODbL** (OpenStreetMap, atribusi tampil di setiap peta).
- **Threat model §E:** ditambah baris file GPX/intent, file peta PMTiles, dan renderer peta native.
- **Belum:**
  - MobSF atas APK release dan Dependency-Check NVD (butuh API key, D-16) menunggu pemilik;
  - **halaman lisensi open source di app belum ada** (lihat §9).

## 8. Penyimpangan / keputusan
D-43…D-64 di `DECISIONS.md`. Yang paling perlu ditinjau:
- **D-64:** peta **tidak bisa diunduh dari app** (desain #26 "pilih area & unduh") karena app sengaja tanpa izin internet. Region dibuat di komputer (`pmtiles extract` dari build Protomaps), lalu diimpor. Bila unduhan dalam app diinginkan, butuh izin INTERNET, sumber tile yang mengizinkan bulk download, dan keputusan baru.
- **D-47/D-55:** halaman peta/navigasi adalah halaman ke-2 di layar ride (desain), bukan pertama.
- **D-49:** aturan gabung tanjakan diperjelas (nilai 500 m untuk dikalibrasi).
- **D-46:** ambang off-route adalah nilai awal untuk dikalibrasi di L7.
- **D-54–D-63:** penyesuaian desain canvas ke spesifikasi. Elemen Fase 4 disembunyikan sampai fasenya.

## 9. Risiko & bug yang diketahui
- **Belum ada uji lapangan:** ambang off-route, jarak peringatan belokan berbasis kecepatan, dan deteksi tanjakan belum dikalibrasi dengan ride nyata.
- **Atribusi lisensi:**
  - BSD-2-Clause (MapLibre) mewajibkan teks copyright ikut didistribusikan bersama binary, dan Apache-2.0 meminta NOTICE;
  - app belum punya layar "Lisensi open source". **Perlu diputuskan sebelum rilis** (lihat pertanyaan di bawah).
- **Renderer native:** file PMTiles yang lolos validasi header tetap diurai oleh kode C++ MapLibre. File hanya berasal dari pilihan pengguna sendiri, dan MapLibre wajib diperbarui tiap fase (SUP-10).
- **Peta dan baterai:** peta bertile memakai GPU lebih banyak daripada breadcrumb. Konsumsi baterai belum diukur (L2). Mode hemat baterai sudah otomatis kembali ke breadcrumb.
- Tidak ada label nama jalan di peta (tanpa glyph offline).
- Onboarding dan hitung mundur insiden versi desain belum dicoba di perangkat.

## 10. Cara menguji (untuk pemilik proyek)
1. **Rute:** Rute → Import GPX (atau buka `.gpx` dari file manager → RideComp) → detail: statistik, profil, tanjakan, "balik arah".
2. **Peta offline:**
   - Di komputer: `pmtiles extract https://build.protomaps.com/<tanggal>.pmtiles jogja.pmtiles --bbox=110.30,-7.90,110.50,-7.70`.
   - Salin ke HP → Setelan → Peta offline → Impor.
   - Detail rute menampilkan peta + "tersedia offline".
3. **Navigasi:**
   - Mode pesawat + GPS → Mulai rute → geser ke halaman Peta: posisi di tengah, rute oranye, jejak biru.
   - Geser peta → tombol "ikuti posisi" → ketuk.
   - Keluar jalur ±100 m selama >10 dtk: getar panjang + banner.
   - Kembali: getar ganda.
4. **Tanjakan/belokan:** pada rute bertanjakan, banner muncul ±500 m sebelum tanjakan. Halaman Grade berganti panel tanjakan. Belokan tajam diberi peringatan 150 m dan 30 m sebelumnya.
5. **Setelah simpan:** jalankan `dumpsys` di `docs/testing/field-tests.md` (L11). Tidak boleh ada service, wakelock, atau GPS aktif.
