# 15 — Rencana Fase 12: Detak Jantung dari Smartwatch

**Status:** ✅ ditutup 2026-11-21: 5/7 selesai, T12.4 & T12.5c ditunda (D-139). Disusun 2026-11-19 atas permintaan pemilik. Dikerjakan **setelah Fase 11** (`14-PHASE11-PLAN.md`), kecuali pemilik mendahulukan T12.1–T12.3 yang kecil.
**Acuan:** `06-ROADMAP.md`, `04-INTEGRATIONS.md` §A (BLE), `11-SECURITY-RULES.md` (input BLE), `09-RELIABILITY-RULES.md` §1 (BLE GATT), `10-FEATURE-CHECKLIST.md`.

---

## 1. Permintaan pemilik

1. "Kedepannya ada fitur broadcast Heart Rate dari Smart Watch."
2. Pemilik memiliki jam tangan **Aolon** yang mengukur HR tetapi **tidak punya fitur broadcast HR**; pemilik meminta protokolnya dipelajari (dibongkar) agar HR-nya bisa dipakai RideComp.

## 2. Kondisi saat ini

- RideComp membaca HR lewat standar **BLE Heart Rate Service 0x180D** sejak Fase 2 (sabuk dada/lengan). Jam tangan yang menyalakan **"broadcast HR"** memakai standar yang sama, jadi seharusnya sudah bisa dipakai tanpa perubahan kode.
- Pemindaian Sensor (`platform/ble/AndroidBleScanner`) memakai **filter UUID layanan di iklan BLE** (`ScanFilter.setServiceUuid`). Jam tangan yang baru memperlihatkan layanan HR **setelah tersambung** tidak akan muncul di daftar.
- Belum ada pengujian dengan jam tangan sungguhan.

### Perkiraan dukungan (diverifikasi di T12.1)

| Jam tangan | Broadcast HR bawaan | Perkiraan |
|---|---|---|
| Garmin, Polar, Coros, Suunto | Ya ("Broadcast Heart Rate" / "Share HR") | Jalan dengan standar 0x180D |
| Amazfit/Zepp, Xiaomi Smart Band 7+ | Ya pada model tertentu ("Heart rate sharing") | Kemungkinan jalan; sebagian butuh T12.2 |
| Huawei Watch GT | Tergantung model | Diuji |
| Huawei Watch Fit 5 Pro | Ya ("berbagi data detak jantung") | **Jalan dengan standar 0x180D** (T12.1, diuji 2026-11-19) |
| Wear OS (Galaxy Watch, Pixel Watch) | Tidak | Butuh aplikasi pendamping (T12.4) |
| Apple Watch | Tidak | Tidak didukung (RideComp hanya Android) |
| **Aolon** & jam tangan terjangkau lain | **Tidak** (milik pemilik) | **GTR 3: tidak ada `0x180D` setelah tersambung** (T12.1/T12.2, diuji 2026-11-21); protokol GloryFit → T12.5 eksperimental |

## 3. Keputusan

| # | Keputusan | Catatan |
|---|---|---|
| S1 | Fase 12 dikerjakan setelah Fase 11; T12.1–T12.3 boleh didahulukan bila pemilik minta | D-114 |
| S2 | Protokol jam tangan Aolon **dipelajari untuk interoperabilitas perangkat milik sendiri**, bertahap dengan titik berhenti (go/no-go) di T12.5a | D-114 |
| S3 | **Clean-room**: pengetahuan protokol boleh dari sumber publik (mis. Gadgetbridge), **kode tidak disalin** (Gadgetbridge AGPL ≠ Apache-2.0 RideComp); tanpa library baru | D-114 |
| S4 | Wear OS (T12.4) opsional dan butuh persetujuan dependency (Wearable Data Layer / Health Services) sebelum dimulai | terbuka |

## 4. Daftar centang Fase 12

Centang saat task selesai (juga di `06-ROADMAP.md` dan `docs/STATUS.md`).

**12A — Jam tangan dengan broadcast HR standar**
- [x] T12.1 Uji kompatibilitas dengan jam tangan sungguhan & catat hasilnya
- [x] T12.2 Pemindaian luas: "Tampilkan semua perangkat Bluetooth"
- [x] T12.3 UX jam tangan: label/ikon "Jam tangan" & panduan menyalakan broadcast per merek

**12B — Wear OS (opsional)**
- [ ] ⏸ T12.4 Aplikasi pendamping Wear OS (butuh persetujuan dependency, S4)

**12C — Jam tangan tanpa broadcast (eksperimental, Aolon)**
- [x] T12.5a Identifikasi protokol Aolon (go/no-go)
- [x] T12.5b Implementasi sensor "Jam tangan (eksperimental)" — hanya bila T12.5a lolos
- [ ] ⏸ T12.5c Uji ride sungguhan ±30 menit

**Penutup fase**
- [ ] Threat model diperbarui (input BLE baru, protokol tidak resmi)
- [ ] Uji lapangan W1–W4 (`07-TEST-PLAN.md` §4c)
- [ ] Rilis beta berikutnya

## 5. Spesifikasi per task

#### T12.1 Uji kompatibilitas
- [x] **Status:** selesai 2026-11-21: Huawei Watch Fit 5 Pro didukung tanpa perubahan kode (A1); Aolon GTR 3 tidak membagikan HR standar (B, diuji dengan T12.2) · Feature Report: [`T12.1-smartwatch-compat.md`](../reports/features/T12.1-smartwatch-compat.md) · Hasil: [`testing/smartwatch-compat.md`](../testing/smartwatch-compat.md) · D-123
- Jam tangan yang tersedia (minimal Aolon milik pemilik; Garmin/Amazfit/Xiaomi bila ada pinjaman): nyalakan broadcast bila ada, pindai di Sensor, pasangkan, ride/uji 10 menit.
- Catat per model: muncul di pindaian? (dengan/tanpa T12.2), nama perangkat, frekuensi data, putus-sambung, baterai jam tangan.
- Hasil di tabel kompatibilitas `docs/testing/smartwatch-compat.md` (dibuat di task ini) dan panduan T12.3.
- Alat bantu pemeriksaan: aplikasi **nRF Connect** (lihat layanan GATT setelah tersambung).

#### T12.2 Pemindaian luas
- [x] **Status:** selesai 2026-11-21 · Feature Report: [`T12.2-wide-scan.md`](../reports/features/T12.2-wide-scan.md) · D-134
- Di layar Sensor, opsi **"Tampilkan semua perangkat Bluetooth"** (default mati): pindai tanpa filter UUID, tetap **bertimeout** (aturan 09), daftar disaring nama kosong/sinyal lemah.
- Saat pengguna memilih perangkat yang tidak mengiklankan 0x180D: sambung → **discover services** → bila ada Heart Rate Service, simpan sebagai sensor HR biasa; bila tidak ada, tampilkan "Perangkat ini tidak membagikan detak jantung" dan putuskan (`disconnect()` + `close()`).
- Tanpa izin baru (memakai izin Nearby devices yang ada); tetap `neverForLocation`.

#### T12.3 UX jam tangan
- [x] **Status:** selesai 2026-11-21 · Feature Report: [`T12.3-watch-guide.md`](../reports/features/T12.3-watch-guide.md) · D-137
- Sensor HR yang terdeteksi sebagai jam tangan (nama/appearance BLE) diberi label & ikon "Jam tangan".
- Panduan **"Cara menyalakan broadcast HR"** per merek di Bantuan (topik baru) dan di pesan "tidak ditemukan" saat pindai (TS.3): Garmin, Polar, Coros, Suunto, Amazfit/Zepp, Xiaomi, Huawei; catatan bahwa jam tangan tanpa fitur ini tidak bisa dipakai kecuali yang didukung T12.5.
- Teks en/id.

#### T12.4 Aplikasi pendamping Wear OS (opsional)
- [ ] **Status:** ⏸ **ditunda** 2026-11-21 atas keputusan pemilik (D-139): butuh dependency baru dan jam Wear OS untuk uji, yang belum ada. Dibuka lagi bila ada jam Wear OS · Feature Report: —
- Modul baru `:wear` (aplikasi kecil di jam tangan) membaca HR lewat Health Services dan mengirim ke HP; RideComp menerima sebagai sumber HR.
- **Butuh persetujuan** dependency (Wear Compose, Health Services, Wearable Data Layer yang memerlukan Google Play Services) dan jam tangan Wear OS untuk uji. Alternatif tanpa dependency HP: aplikasi jam tangan menjadi periferal BLE 0x180D sendiri (perlu dicek dukungan peripheral mode di jam tangan).
- Distribusi: APK Wear OS terpisah di GitHub Release.

#### T12.5a Identifikasi protokol Aolon (titik go/no-go)
- [x] **Status:** selesai 2026-11-21, **lolos bersyarat** (GTR 3 / GloryFit) · Feature Report: [`T12.5a-aolon-protocol.md`](../reports/features/T12.5a-aolon-protocol.md) · Protokol: `04-INTEGRATIONS.md` §A.5a · D-135
- Data yang dibutuhkan dari pemilik: **model persis** jam tangan dan **nama aplikasi pendampingnya** (mis. Da Fit, FitCloudPro, GloryFit, Hryfine).
- Cek apakah protokolnya sudah didokumentasikan publik (mis. dukungan Gadgetbridge untuk keluarga aplikasi tersebut) — **hanya sebagai pengetahuan** (S3).
- Rekam lalu lintas BLE milik sendiri dengan **Bluetooth HCI snoop log** (opsi pengembang Android) saat aplikasi bawaan menampilkan HR langsung; analisis dengan Wireshark di Mac.
- **Lolos (go)** bila: HR real-time bisa diminta tanpa akun/kunci rahasia, **≥ 1 nilai per 1–2 detik**, stabil ≥ 10 menit. **Tidak lolos** → fase berhenti untuk Aolon, rekomendasi sabuk HR standar dicatat di panduan.
- Hasil (perintah, format data, frekuensi) dicatat di `docs/architecture/04-INTEGRATIONS.md` bagian baru "Jam tangan tanpa standar".

#### T12.5b Implementasi sensor "Jam tangan (eksperimental)"
- [x] **Status:** selesai 2026-11-21 · Feature Report: [`T12.5b-gloryfit-watch.md`](../reports/features/T12.5b-gloryfit-watch.md) · D-136
- Jenis sensor baru di `:core` (`SensorKind`/parser terpisah), koneksi memakai klien GATT yang ada (`platform/ble`), pemilik & pelepasan resource terdaftar di 09 §1.
- **Validasi input ketat** (SEC-IN): panjang paket, rentang HR 30–230 bpm, buang nilai di luar rentang; tidak pernah crash karena paket aneh.
- Ditandai **"Eksperimental"** di UI; pesan bila jam tangan masih tersambung ke aplikasi bawaannya ("Putuskan dulu dari aplikasi Aolon"); berhenti mengirim perintah ke jam tangan saat ride selesai (hemat baterai jam tangan).
- Tanpa dependency baru; parser diuji unit dengan rekaman paket asli.

#### T12.5c Uji ride sungguhan
- [ ] **Status:** ⏸ **ditunda** 2026-11-21 atas keputusan pemilik (D-139): pemilik sedang sakit. Fitur tetap berlabel eksperimental sampai uji ini selesai; uji dalam ruangan 10,5 mnt sudah lolos (T12.5b) · Feature Report: —
- Ride ±30 menit: bandingkan dengan sabuk HR (bila ada), catat jeda data, putus-sambung, baterai jam tangan & HP, zona HR & analisa T11.5 tetap benar.

## 6. Risiko

| Risiko | Mitigasi |
|---|---|
| Firmware/aplikasi Aolon berubah → protokol rusak | Label eksperimental, deteksi gagal yang jelas, tidak memengaruhi sensor lain |
| HR jam tangan tidak real-time | Kriteria go/no-go T12.5a, diubah D-135: setiap perubahan HR terkirim dalam beberapa detik + aliran ≥ 10 mnt (dibuktikan dengan sambung ulang di T12.5c) |
| Satu koneksi BLE per jam tangan | Pesan untuk memutus aplikasi bawaan; dokumentasi |
| Lisensi | Clean-room, tanpa menyalin kode AGPL |
| Baterai jam tangan | Perintah HR hanya selama ride; berhenti saat ride selesai |
| Pindai tanpa filter lebih boros | Opsi default mati, bertimeout, hanya di layar Sensor |

## 7. Anggaran performa

Sama dengan `14-PHASE11-PLAN.md` §8, ditambah: tidak ada pemindaian/koneksi BLE saat tidak merekam selain di layar Sensor (BAT-01); parser jam tangan O(1) per paket; tidak ada perubahan pada frekuensi update layar ride (≤ 1 Hz).

## 8. Definition of Done Fase 12

- T12.1–T12.3 selesai dengan Feature Report; T12.4 & T12.5 selesai **atau** dihentikan dengan alasan tercatat (D-1xx).
- Tabel kompatibilitas jam tangan terisi; panduan per merek tersedia.
- Uji lapangan W1–W4 lulus; `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug` lulus.
- Threat model diperbarui; rilis beta dengan catatan rilis.
