# Feature Report — TM.1: Peta online

**Tanggal:** 2026-11-17
**Branch:** `feature/TM.1-online-map`
**Status:** Diuji di perangkat, di-merge ke `main`

## 1. Ringkasan
Permintaan pemilik: "jika ada jaringan gunakan map, ketika tidak ada tidak usah menggunakan map" (D-90). Saat HP punya internet, peta jalan OpenFreeMap tampil di:
- halaman **Peta** saat ride — kini di **semua ride ber-GPS**, tidak hanya saat mengikuti rute; tanpa rute menampilkan jejak yang sudah dilalui + jarak & kecepatan;
- **detail rute**;
- **detail aktivitas** (jejak di atas peta, menggantikan sketsa).

Tanpa internet: garis rute/jejak saja (breadcrumb di ride, sketsa di riwayat). File peta offline (.pmtiles) tetap bisa dipakai sebagai opsi. Setelan → **Peta** → Peta online: Wi‑Fi & data (default) · Hanya Wi‑Fi · Mati.

## 2. Perubahan per lapisan
| Lapisan | File/kelas | Keterangan |
|---|---|---|
| Domain (`:core`) | `engine/map/OnlineMaps.kt` | `OnlineMapMode`, `NetworkStatus`, `MapTiles`, `RideTrail` (titik ≥ 10 m, maks 2.000, dipadatkan separuh) |
| | `source/PlatformSources.kt` `NetworkStatusSource` | Interface |
| | `usecase/MapUseCases.kt` | `ObserveMapTilesUseCase`, `LoadRideTrailUseCase` (sampel per halaman 1.000) |
| | `RideSettings.onlineMapMode` | Disimpan `app.online_map_mode` |
| Platform (`:platform`) | `map/AndroidNetworkStatusSource` | `registerDefaultNetworkCallback` di `callbackFlow`, lepas di `awaitClose`; online = INTERNET + VALIDATED; Wi‑Fi = NOT_METERED; izin `ACCESS_NETWORK_STATE` |
| UI (`:ui-common`) | `map/RouteMap.kt` (dulu `OfflineRouteMap`), `OfflineMapStyle` | Sumber vektor OpenFreeMap + layer OpenMapTiles dengan warna gelap RideComp, tanpa label; `MapLibre.setConnected(isOnline)`; atribusi online; jalan dibuat lebih kontras |
| Presentation | `RideViewModel`/`RideScreen`/`RideMapView` | Halaman Peta di semua ride ber-GPS, jejak dari sampel tersimpan + posisi live |
| | `RouteDetailViewModel`/`RouteDetailScreen` | Peta online, file offline, atau catatan "muncul saat ada internet" |
| | `ActivityDetailViewModel`/`ActivityDetailScreen` | Jejak di atas peta online |
| | `OfflineMapsViewModel`/`OfflineMapsScreen`, `SettingsScreen` | Pilihan Peta online; ringkasan "Online: Wi‑Fi & data"; impor file jadi tombol sekunder |
| App | manifest, `data_extraction_rules.xml`, DI | Cache tile `mbgl-offline.db` tidak ikut transfer perangkat |

## 3. Dependency baru
Tidak ada (MapLibre sudah dipakai sejak D-64). Layanan luar baru: `tiles.openfreemap.org` (tanpa akun/kunci, boleh komersial; ToS dibaca 2026-11-17).

## 4. Resource & siklus hidup
| Resource | Diakuisisi | Dilepas | Diverifikasi dengan |
|---|---|---|---|
| Callback jaringan | Layar berpeta tampil dan peta online tidak "Mati" | Koleksi berhenti (`WhileSubscribed` 5 dtk) → `unregisterNetworkCallback` | `MapUseCasesTest`, `RideViewModelsTest` (`activeCollectors` = 0) |
| MapView | Tidak berubah (09 §1), kini juga di detail aktivitas | `DisposableEffect` | LeakCanary: tidak ada notifikasi selama uji |
| Jejak ride | Halaman ride tampil | Bersama ViewModel; ≤ 2.000 titik | `MapUseCasesTest` (pemadatan) |

## 5. Checklist kode
- [x] Domain murni di `:core`; `:ui-common` tetap tanpa tipe `:core` (hanya `isOnline` + path).
- [x] Callback Android lewat `callbackFlow` + `awaitClose`; koleksi berbatas.
- [x] Konstanta di `TrailConfig`/`MapConfig`; string en/id.
- [x] Kalkulasi jarak jejak di `dispatchers.default`.

## 6. Hasil testing
- Unit test baru: `MapUseCasesTest` (6: online di data seluler & lepas saat offline, Hanya Wi‑Fi, Mati tanpa mendengar jaringan, jarak titik jejak, pemadatan, bangun ulang per halaman), `RideViewModelsTest` (jejak tersimpan + live di peta online, hilang saat offline, callback dilepas), `OfflineMapsViewModelTest` (mode disimpan).
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`: ✅ BUILD SUCCESSFUL.
- Uji perangkat (realme RMX3830, Android 15, Wi‑Fi):
  - Detail aktivitas "Morning ride": jejak di atas peta OpenFreeMap dengan atribusi; jalan awalnya terlalu gelap → warna jalan dinaikkan, diuji ulang (terbaca).
  - Ride tanpa rute (Sepedaku, Road): halaman 2 dari 4 = Peta online, posisi diikuti, jejak biru, Jarak & Kecepatan.
  - Wi‑Fi & data dimatikan sementara → halaman Peta berganti breadcrumb (garis oranye + titik posisi); dinyalakan lagi → peta online kembali otomatis. Wi‑Fi & data dikembalikan ke kondisi awal (keduanya aktif). Ride uji dibuang, service berhenti.
  - Setelan → Peta: pilihan Peta online + penjelasan; ringkasan "Online: Wi‑Fi & data".
  - Crash buffer kosong.
  - Detail rute: GPX uji "Uji peta online" (lingkaran 3,33 km, diimpor dari Downloads) tampil di atas peta online dengan atribusi; tombol "Ride rute ini" dan profil elevasi tetap ada.

## 7. Keamanan
- §D: request jaringan baru hanya HTTPS ke `tiles.openfreemap.org` (Network Security Config: HTTPS saja, trust anchor sistem); tanpa data pengguna selain koordinat tile yang diminta; tanpa telemetri; tidak ada komponen ter-export baru; izin baru `ACCESS_NETWORK_STATE` (normal); cache tile internal, tidak ikut backup cloud (aturan lama) maupun transfer perangkat (aturan baru).
- Threat model (11 §C) dan SEC-NET-03 diperbarui.
- Lint Security: 0 temuan.

## 8. Penyimpangan dari spesifikasi / RULE-EXCEPTION
- D-64 ("MapLibre offline, `setConnected(false)`") diganti D-90 atas permintaan pemilik.
- 13-UI-SCREENS #7: halaman Peta kini juga ada di ride tanpa rute.

## 9. Risiko & bug yang diketahui
- OpenFreeMap gratis tanpa SLA dan bisa berhenti; app otomatis kembali ke garis.
- Peta online tidak menampilkan nama jalan (tanpa layer teks, sama seperti peta offline).
- Detail aktivitas memakai sketsa bila hanya ada file offline (tanpa internet).

## 10. Cara menguji (untuk pemilik proyek)
1. Dengan internet, buka Riwayat → sebuah ride: jejak tampil di atas peta.
2. Mulai ride (sepeda Road), geser ke halaman ke-2: peta + jejak + posisi.
3. Matikan Wi‑Fi & data: halaman Peta jadi garis; nyalakan lagi: peta kembali.
4. Setelan → Peta → Hanya Wi‑Fi / Mati: perilaku sesuai pilihan.
