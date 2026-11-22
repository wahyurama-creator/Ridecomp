# Feature Report — Tanda celah GPS & topik Bantuan (D-149)

**Tanggal:** 2026-11-22
**Branch:** `feature/gps-gaps`
**Status:** Selesai (belum dirilis)

## 1. Ringkasan
Bagian ride yang direkam tanpa sinyal GPS (hutan, terowongan) kini digambar **putus-putus** di peta dan sketsa rute halaman detail ride, sehingga terlihat bahwa garis itu tebakan lurus. Topik Bantuan baru **Sinyal GPS hilang** menjelaskan apa yang dilakukan rekaman dan cara menjaga sinyal.

## 2. Perubahan per lapisan
| Lapisan | File | Keterangan |
|---|---|---|
| `:core` | `ChartUseCases.routeGaps()`, `GuideTopic.GPS_SIGNAL` | Indeks garis tanpa fix; topik baru |
| `:ui-common` | `RouteGaps.splitAtGaps`, `RouteMap(gaps)`, `OfflineMapStyle` (`route_gaps`), `TrackSketch(gaps)` | Garis putus-putus di peta MapLibre dan sketsa |
| `:feature-history` | `ActivityDetailViewModel.routeGaps`, `ActivityDetailScreen` | Meneruskan celah |
| `:feature-settings` | `GuideUi` (`GUIDE_TEXTS`), strings id/en | Topik Bantuan; tabel teks + test |

## 3. Dependency baru
Tidak ada.

## 4. Resource & siklus hidup
Tidak berubah (satu source/layer GeoJSON tambahan di style peta yang sudah ada).

## 5. Checklist kode
Konstanta dash di satu tempat per lapisan; logika celah murni di `:core` dan `:ui-common` dengan test; `GUIDE_TEXTS` dijaga test kelengkapan.

## 6. Hasil testing
- Test baru: `RouteGapsTest` (`:core`, 2), `RouteGapsTest` (`:ui-common`, 2), `GuideTextsTest` (1).
- `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug`: ✅.
- Perangkat (realme RMX3830, build debug): topik "Sinyal GPS hilang" tampil; peta detail ride tetap tampil normal dengan layer baru. **Garis putus-putus belum dilihat di perangkat**: belum ada ride dengan sinyal hilang (GPS simulasi tidak punya celah).

## 7. Keamanan
§D: tidak ada input/komponen/izin/dependency/data baru.

## 8. Penyimpangan
Tidak ada.

## 9. Risiko & bug yang diketahui
Celah lebih pendek dari satu titik chart (≈ durasi ride / 300) tidak terlihat.

## 10. Cara menguji
Rekam ride yang melewati terowongan atau hutan → detail ride: bagian tanpa sinyal putus-putus. Setelan → Bantuan → "Sinyal GPS hilang".
