# Feature Report — Jarak mengikuti rute saat GPS hilang (D-150)

**Tanggal:** 2026-11-22
**Branch:** `feature/gps-gap-route-distance`
**Status:** Selesai (belum dirilis)

## 1. Ringkasan
Saat mengikuti rute GPX dan sinyal GPS hilang (hutan, terowongan), jarak yang terlewat kini dihitung **sepanjang rute** begitu sinyal kembali, bukan garis lurus yang memotong tikungan. Tanpa rute, perilaku tidak berubah.

## 2. Perubahan per lapisan
| Lapisan | File | Keterangan |
|---|---|---|
| `:core` | `AlongRoute` (baru), `RouteTracker.alongRouteM`, `RouteFollower.alongRoute` | Jarak sepanjang rute antara dua posisi |
| `:core` | `DistanceTracker`, `RideMetricsAccumulator`, `RideRecorder` | Dipakai setelah celah ≥ 5 dtk bila masuk akal |
| `:core` | `GpsConfig.ROUTE_GAP_MIN_MS`, `ROUTE_GAP_MAX_FACTOR` | Konstanta |
| `:feature-settings` | strings | Teks Bantuan "Sinyal GPS hilang" |
| Dokumen | 05-DATA-MODEL §3.1, D-150 | Spesifikasi |

## 3. Dependency baru
Tidak ada.

## 4. Resource & siklus hidup
Tidak berubah. Proyeksi ke seluruh rute hanya saat fix kembali setelah celah (jarang), bukan tiap detik.

## 5. Checklist kode
Konstanta di `GpsConfig`; tanpa `!!`; batas masuk akal mencegah jarak membengkak bila proyeksi salah (rute bolak-balik).

## 6. Hasil testing
- `RideRecorderTest`: rute L, sinyal hilang 300 m di tikungan: perilaku lama **918,6 m**, baru dalam 980–1.010 m (sebenarnya 1.000 m).
- `DistanceTrackerRouteGapTest` (2): rute dipakai setelah celah; garis lurus tanpa celah, bila rute > 3× garis lurus, atau di luar rute.
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`: ✅.
- Perangkat: belum (butuh ride sungguhan mengikuti GPX yang kehilangan sinyal).

## 7. Keamanan
§D: tidak ada input/komponen/izin/dependency/data baru.

## 8. Penyimpangan
Tidak ada.

## 9. Risiko & bug yang diketahui
Pada rute yang bolak-balik di jalan yang sama, proyeksi bisa memilih kaki yang salah; batas ≤ 3× dan "tidak mundur" membatasi kesalahannya, sisanya jatuh ke garis lurus.

## 10. Cara menguji
Ikuti rute GPX yang melewati terowongan/hutan; bandingkan jarak dengan panjang rute di bagian itu.
