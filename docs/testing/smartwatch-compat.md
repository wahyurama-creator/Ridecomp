# Kompatibilitas Jam Tangan sebagai Sensor HR (T12.1)

Hasil uji jam tangan sungguhan sebagai sensor detak jantung RideComp (`15-PHASE12-PLAN.md` §5 T12.1). Dipakai sebagai acuan T12.2 (pemindaian luas) dan T12.3 (panduan per merek). Tambah satu baris per model yang diuji.

## Kasus

| Kasus | Arti | Yang dibutuhkan |
|---|---|---|
| **A1** | Berbagi HR menyala, jam tangan mengiklankan Heart Rate Service `0x180D` | Sudah didukung (tanpa perubahan kode) |
| **A2** | Berbagi HR menyala, tetapi `0x180D` baru terlihat setelah tersambung | T12.2 "Tampilkan semua perangkat Bluetooth" |
| **B** | Jam tangan tidak berbagi HR (fitur mati / tidak ada) | Tidak bisa dipakai; panduan T12.3, jalur eksperimental T12.5 |

## Tabel kompatibilitas

| Jam tangan | Nama BLE | Kasus | Muncul di Pindai | Data HR | Putus-sambung | Baterai jam tangan | Tanggal · HP · build |
|---|---|---|---|---|---|---|---|
| Huawei Watch Fit 5 Pro | `HUAWEI WATCH HR-E57` (akhiran = 3 digit terakhir alamat BLE) | **A1** | Ya, langsung (filter `0x180D`) | 608/612 sampel 1 Hz berisi HR (4 kosong = detik pertama sebelum tersambung); 78–91 bpm duduk | 0 kali dalam 10:38 dan ±14 mnt | 53 % → 52 % dalam ±11 mnt (dibaca via Battery Service) | 2026-11-19 · Sony Xperia XZ F8331, Android 8.0, RAM 2,8 GB · debug 0.9.3 |
| Huawei Watch Fit 5 Pro, berbagi HR **mati** | — | **B** | Tidak (pindai selesai 15 dtk, 0 hasil, pesan "Tidak ada sensor baru…") | — | — | — | sama |
| Aolon GTR 3 (aplikasi GloryFit) | `Aolon GTR3(ID-A05B)` (akhiran = 4 digit terakhir alamat BLE) | **B** | Tidak di pindai biasa (tidak mengiklankan `0x180D`); muncul di **Tampilkan semua perangkat Bluetooth** (T12.2) dengan sinyal kuat, hanya saat Bluetooth jam aktif & GloryFit ditutup | Tidak ada: setelah tersambung tidak ada Heart Rate Service (hanya layanan bawaan + layanan vendor GloryFit); RideComp menampilkan "tidak membagikan detak jantung" dan memutus | — | — | 2026-11-21 · realme RMX3830, Android 15 · release lokal 0.9.3 (T12.2) |

## Perilaku RideComp yang teramati (Huawei Watch Fit 5 Pro)

- **Sambung otomatis saat ride dimulai:** ±2,8 dtk sampai notifikasi `0x2A37` aktif.
- **Berbagi HR dimatikan saat jam tangan tersimpan:** ride tetap merekam tanpa crash; kolom HR & ikon hati disembunyikan; percobaan sambung bertimeout 20 dtk dengan jeda 2 → 4 → 8 → 15 dtk (`SensorConnection.RECONNECT_DELAYS_MS`, `RECONNECT_STEADY_DELAY_MS`).
- **Berbagi HR dinyalakan lagi di tengah ride:** tersambung kembali otomatis pada percobaan berikutnya, HR tampil lagi tanpa disentuh. Waktu pulih terburuk ±35 dtk (timeout 20 dtk + jeda 15 dtk).
- **Setelah ride selesai:** GATT `disconnect` + `close()` saat **Selesai** ditekan; tidak ada foreground service, wakelock, atau permintaan GPS milik RideComp (`dumpsys activity services`, `dumpsys power`, `dumpsys location`).
- Jam tangan juga mengirim notifikasi pada layanan **HID**; Android mengabaikannya (`bta_gattc_process_indicate, ignore HID ind/notificiation` di logcat). Tidak berdampak pada HR.
- Nilai HR yang sama pernah bertahan sampai 27 dtk saat duduk. Karena RideComp menyimpan nilai terakhir per detik, frekuensi kirim asli jam tangan belum terukur; dipastikan di uji lapangan W2 (ride sungguhan, HR naik-turun) atau dengan nRF Connect.

## Perilaku RideComp yang teramati (Aolon GTR 3)

- Pindai biasa (filter HR/CSC/CP/FTMS): 0 hasil, juga setelah GloryFit dihentikan paksa.
- Pindai luas: jam baru terlihat saat Bluetooth jam aktif dan tidak sedang dipegang GloryFit; pada percobaan sebelumnya (94–127 iklan mentah per pindai) jam tidak termasuk.
- **Periksa:** sambung → discover services ±0,6 dtk → `close()` + `GATT_Disconnect` → pesan "Aolon GTR3(ID-A05B) tidak membagikan detak jantung…". Tidak dipasangkan (bonding) dan tidak disimpan.
- Kesimpulan: GTR 3 tidak bisa dipakai sebagai sensor HR standar; satu-satunya jalur adalah protokol vendor GloryFit (T12.5a, go/no-go).
- **T12.5a (2026-11-21): lolos bersyarat.** Perintah `FD 11 02 01` ke `0x33F1` memulai HR real-time di `0x33F2` tanpa GloryFit, tanpa bonding/kunci (format di `04-INTEGRATIONS.md` §A.5a). Uji 4 mnt stabil; uji 10 mnt putus sendiri (Connection Timeout) di menit ke-7,5 → ditangani sambung ulang dan diuji di T12.5c.

## Cara menguji jam tangan berikutnya

1. Nyalakan berbagi/broadcast HR di jam tangan.
2. RideComp → Sensor → Cari sensor. Tidak muncul → nyalakan **Tampilkan semua perangkat Bluetooth**, cari lagi, lalu **Periksa** jam tangan (T12.2); atau **nRF Connect** → SCAN: ada `0x180D` di iklan (A1) atau baru setelah CONNECT (A2)?
3. Pasangkan, mulai ride, rekam ≥ 10 menit, simpan.
4. Debug build: salin DB (`adb exec-out run-as com.ridecomp.app cat databases/ridecomp.db`) lalu hitung `track_points.hr` kosong/terisi.
5. Matikan berbagi HR di tengah ride, lalu nyalakan lagi: rekaman jalan terus, HR pulih otomatis.
6. Setelah selesai: perintah `dumpsys` di `field-tests.md`.
