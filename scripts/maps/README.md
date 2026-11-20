# Paket peta per provinsi (T11.13)

Skrip `build-regions` memotong build peta harian **Protomaps** (data OpenStreetMap, ODbL) menjadi satu file
PMTiles per provinsi untuk peta offline RideComp. Skrip ini dijalankan di Mac pemilik dan bukan bagian dari aplikasi.
Keputusan: D-112, D-113, D-129.

## Sekali di awal

```sh
brew install pmtiles        # CLI resmi Protomaps (BSD-3)
gh auth status              # untuk mengunggah ke GitHub Release
```

## Membuat paket (±3 bulan sekali)

```sh
# 1. (Opsional) perbarui bbox 38 provinsi dari Nominatim OSM → scripts/maps/provinces.json
scripts/maps/build-regions bboxes

# 2. Potong provinsi dari build Protomaps terbaru (hanya mengunduh potongan Indonesia lewat HTTP Range,
#    bukan planet ±140 GB). Hasil di ~/RideComp-maps/<id>.pmtiles + manifest.json (SHA-256, ukuran).
scripts/maps/build-regions extract                         # semua provinsi
scripts/maps/build-regions extract --only di-yogyakarta    # satu/beberapa provinsi
scripts/maps/build-regions extract --build 20261118        # build tertentu (YYYYMMDD)

# 3. Unggah ke GitHub Release publik (diminta konfirmasi "yes")
scripts/maps/build-regions upload --release maps-2026-11

# 4. Tulis indeks wilayah yang ikut APK → app/src/main/assets/maps/regions.json, lalu commit
scripts/maps/build-regions index --release maps-2026-11
```

- Zoom 0–14, sama dengan detail peta online (OpenFreeMap); MapLibre memperbesar dengan halus di atas zoom 14.
- Provinsi lebih dari 300 MB ditandai ⚠ dan perlu dipecah per kota/kabupaten. Format indeks sudah mendukung
  beberapa file per wilayah.
- File besar tidak pernah masuk git: folder keluarannya `~/RideComp-maps` (di luar repo).
- `index` hanya menulis provinsi yang ada di `~/RideComp-maps/manifest.json`. Folder itu dihapus setelah uji pertama, jadi
  saat menambah provinsi, jalankan `extract` untuk **semua** provinsi yang ingin ada di indeks (termasuk `di-yogyakarta`),
  lalu `upload` (file yang sama ditimpa) dan `index`.
- `pmtiles` dicopot dari Mac setelah T11.13 (permintaan pemilik); pasang lagi saat membuat paket, lalu copot dengan
  `HOMEBREW_NO_AUTOREMOVE=1 brew uninstall pmtiles`.
- Atribusi di peta dan catatan rilis: **© OpenStreetMap contributors, Protomaps** (ODbL 1.0).

## Uji pertama (2026-11-20)
DI Yogyakarta, build `20261118` (basemap v4.15.2): **21,3 MB**, ±22 detik, `pmtiles verify` lulus.
