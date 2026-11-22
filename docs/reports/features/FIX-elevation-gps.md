# Feature Report — Perbaikan tanjakan (D-148)

**Tanggal:** 2026-11-22
**Branch:** `fix/elevation-barometer-fallback`
**Status:** Selesai

## 1. Ringkasan
Pemilik melaporkan ride pagi 9,5 km tercatat **698 m** tanjakan di RideComp (realme RMX3830), sedangkan Strava di iPhone 11 yang dibawa bersamaan mencatat **17 m**. Penyebab: HP ini tidak punya barometer dan altitude GPS-nya melayang puluhan meter serta melonjak ratusan meter. Diperbaiki: saringan altitude GPS yang jauh lebih ketat, bug pencampuran barometer/GPS (untuk HP dengan barometer), dan label "Perkiraan dari GPS" di detail ride bila HP tanpa barometer.

## 2. Perubahan per lapisan
| Lapisan | File | Keterangan |
|---|---|---|
| `:core` | `RollingMedian` (baru) | Median bergulir berbatas (MEM-05) |
| `:core` | `ElevationTracker`, `ElevationConfig` | GPS: median 30 dtk, perubahan maks 0,5 m/sampel, histeresis 10 m; barometer tidak berubah |
| `:core` | `RideRecorder.updateElevation` | Satu sumber per ride (tanpa fallback GPS di mode barometer) |
| `:core` | `HasBarometerUseCase` (baru) | Untuk label perkiraan |
| `:feature-history` | `ActivityDetailViewModel`/`Screen` | `isAscentEstimated` → catatan di bagian Elevasi |
| `:app` | `UseCaseModule`, test DI | Wiring; `FakePressureSource` di test fixtures |
| Dokumen | 05-DATA-MODEL §3.5, D-148 | Spesifikasi baru |

## 3. Dependency baru
Tidak ada.

## 4. Resource & siklus hidup
Tidak berubah (barometer tetap didaftarkan hanya selama merekam).

## 5. Checklist kode
Konstanta di `ElevationConfig`; buffer berbatas; `when` exhaustive; tanpa `!!`.

## 6. Hasil testing
- Bukti dari data nyata: FIT ride pemilik (diekspor lewat app di HP, dianalisis lokal, tidak di-commit dan dihapus sesudahnya): altitude −41,6…128,8 m, lonjakan sampai 70,8 m per detik setelah rata-rata 10 dtk. Simulasi offline saringan baru: ~717 → ~273 m.
- Test baru: `RideRecorderTest` (barometer telat di jalan datar: sebelum 22,5 m, sesudah < 5 m), `ElevationTrackerTest` (lonjakan GPS 300 m di jalan datar: kode lama **1.560 m**, baru **0 m**), `RollingMedianTest` (2), asersi `isAscentEstimated`.
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`: ✅.
- Perangkat: realme RMX3830 (tanpa barometer, `dumpsys sensorservice`), build debug: catatan "Perkiraan dari GPS…" tampil di bagian Elevasi.

## 7. Keamanan
§D: tidak ada input/komponen/izin/dependency baru. File FIT pemilik (berisi lokasi) hanya di folder sementara Mac, tidak di repo.

## 8. Penyimpangan dari spesifikasi / RULE-EXCEPTION
05-DATA-MODEL §3.5 diubah (histeresis GPS 4 → 10 m, median) sesuai D-148.

## 9. Risiko & bug yang diketahui
1. Tanpa barometer, tanjakan tetap kebesaran (~15× di ride pemilik); solusi penuh = koreksi DEM offline (rencana terpisah).
2. Batas 0,5 m/dtk membuat turunan sangat cepat tercatat terlambat (penurunan, bukan tanjakan).
3. Ride lama tidak dihitung ulang (altitude mentah tidak disimpan).

## 10. Cara menguji
Rekam ride di HP tanpa barometer → detail ride, bagian Elevasi: ada catatan perkiraan dan angka tanjakan jauh lebih kecil dari sebelumnya.
