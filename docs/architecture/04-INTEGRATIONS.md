# 04 — Integrations Specification

Dokumen ini mencakup sensor BLE, format file FIT & GPX, peta, dan Strava.

---

## A. Sensor Bluetooth Low Energy (BLE)

Semua UUID adalah UUID 16-bit standar Bluetooth SIG (base `0000xxxx-0000-1000-8000-00805f9b34fb`). Semua angka multi-byte **little-endian**.

### A.1 Heart Rate Service — `0x180D`

**Karakteristik:** Heart Rate Measurement `0x2A37` (Notify)

| Byte | Field | Keterangan |
|---|---|---|
| 0 | Flags | bit0: 0 = HR uint8, 1 = HR uint16; bit1–2: sensor contact; bit3: Energy Expended ada; bit4: RR-Interval ada |
| 1 (atau 1–2) | Heart rate | bpm |
| berikut | Energy expended (uint16) | jika bit3 |
| berikut | RR-Interval (uint16, satuan 1/1024 dtk), bisa beberapa | jika bit4 |

Validasi: abaikan nilai 0 atau > 240 bpm.

### A.2 Cycling Speed and Cadence Service — `0x1816`

**Karakteristik:** CSC Measurement `0x2A5B` (Notify). Juga CSC Feature `0x2A5C` (Read).

| Field | Tipe | Ada jika |
|---|---|---|
| Flags | uint8 | — bit0: Wheel Revolution Data; bit1: Crank Revolution Data |
| Cumulative Wheel Revolutions | uint32 | bit0 |
| Last Wheel Event Time | uint16, satuan 1/1024 dtk | bit0 |
| Cumulative Crank Revolutions | uint16 | bit1 |
| Last Crank Event Time | uint16, satuan 1/1024 dtk | bit1 |

**Perhitungan (pseudo):**
```
dRev  = (rev  - prevRev)  mod 2^N      // N=32 roda, 16 crank (tangani rollover)
dTime = (time - prevTime) mod 65536    // dalam 1/1024 dtk
if dTime == 0: tidak ada event baru → jika > 3 dtk tanpa event baru, nilai = 0
cadence_rpm = dRev / (dTime / 1024) * 60
speed_mps   = dRev * wheelCircumference_m / (dTime / 1024)
```
- Sensor magnet-less sering hanya mengirim salah satu (speed **atau** cadence); deteksi lewat flags.
- Buang lonjakan tidak masuk akal (cadence > 200 rpm, speed > 120 km/h).

### A.3 Cycling Power Service — `0x1818`

**Karakteristik:** Cycling Power Measurement `0x2A63` (Notify). Juga Cycling Power Feature `0x2A65` (Read).

| Field | Tipe | Ada jika (flags uint16) |
|---|---|---|
| Flags | uint16 | — |
| Instantaneous Power | sint16, watt | selalu |
| Pedal Power Balance | uint8, satuan 1/2 % | bit0 |
| Accumulated Torque | uint16, 1/32 Nm | bit2 |
| Cumulative Wheel Revs + Last Wheel Event Time | uint32 + uint16 (**1/2048 dtk**) | bit4 |
| Cumulative Crank Revs + Last Crank Event Time | uint16 + uint16 (1/1024 dtk) | bit5 |
| Extreme Force Magnitudes | sint16 + sint16 | bit6 |
| Extreme Torque Magnitudes | sint16 + sint16 | bit7 |
| Extreme Angles | 3 byte | bit8 |
| Top Dead Spot Angle | uint16 | bit9 |
| Bottom Dead Spot Angle | uint16 | bit10 |
| Accumulated Energy | uint16, kJ | bit11 |

- Parser harus melewati field opsional sesuai urutan di atas untuk sampai ke data crank.
- Cadence dari power meter dihitung dari crank data (bit5) dengan rumus seperti A.2.
- Bila power meter dan CSC sama-sama memberi cadence, prioritaskan sesuai pengaturan (default: power meter).
- Pedal balance (bit1 = referensi kiri/kanan) disimpan bila ada (opsional untuk ditampilkan).

### A.4 Battery Service — `0x180F`
Battery Level `0x2A19` (Read/Notify), uint8 0–100%. Baca saat connect dan tiap 10 menit. Peringatan bila < 15%.

### A.5 Perilaku koneksi
- Scan dengan filter service UUID (`0x180D`, `0x1816`, `0x1818`), timeout 15 detik.
- Simpan `remoteId` sensor yang dipairing; auto-connect di pre-ride.
- Satu perangkat bisa menyediakan beberapa service (mis. power meter + CSC); daftarkan semua yang relevan.
- Tidak ada ANT+.

### A.5a Jam tangan tanpa standar — Aolon GTR 3 / GloryFit (T12.5a, eksperimental)
Hasil pengamatan lalu lintas BLE jam tangan milik pemilik (HCI snoop log + uji nRF Connect, 2026-11-21, D-135). Clean-room: hanya dari rekaman perangkat sendiri, tanpa kode pihak ketiga. Bisa berubah dengan pembaruan firmware/aplikasi.

| Item | Nilai |
|---|---|
| Iklan | Nama `Aolon GTR3(ID-XXXX)`, tanpa UUID layanan standar |
| Layanan | `0x55FF` (perintah), juga `0x56FF` & `0xFF12` (tidak dipakai) |
| Karakteristik | `0x33F1` READ/WRITE (perintah, write dengan respons) · `0x33F2` NOTIFY (jawaban & data) |
| Keamanan | Tanpa bonding, tanpa kunci/akun; perintah pembuka aplikasi bawaan **tidak** diperlukan |
| Mulai olahraga | `FD 11 02 01` → dibalas gema `FD 11 02 01` lalu `E5 11` |
| Detak jantung | Notifikasi `FD 02 <bpm> 00×11` (14 byte); `bpm` uint8. Nilai pertama `0` (pemanasan sensor, ±15 dtk) → dibuang |
| Frekuensi | **Hanya saat nilai berubah**: median 2,7–4,2 dtk, terlama 19,8 dtk saat HR stabil |
| Penghitung waktu (aplikasi bawaan, tiap 1 dtk) | `FD 44 02 01 <jam> <menit> <detik> 00×6` (13 byte); tidak wajib (aliran HR tetap jalan 4 menit tanpanya) |
| Berhenti | `FD 00 02 01` → `E5 00 00 <bpm rata-rata>` dan rekaman `F7 03 …` |
| Lain | Hanya satu koneksi: aplikasi bawaan (GloryFit) harus terputus. Uji 7,5 menit berakhir dengan **Connection Timeout (0x08)** 5 dtk setelah paket HR terakhir |

Konsekuensi untuk RideComp (T12.5b): nilai HR terakhir ditahan dan dikirim ulang tiap detik selama paket terakhir masih baru (karena sampel dianggap usang setelah 3 dtk); penghitung waktu dikirim tiap detik seperti aplikasi bawaan; sambung ulang mengirim `FD 11` lagi; `FD 00` saat ride selesai.

### A.6 Unit test wajib
Siapkan byte array contoh untuk: HR uint8, HR uint16 + RR; CSC wheel-only, crank-only, keduanya; rollover uint16 crank time & uint32 wheel revs; CP power-only, CP dengan pedal balance + crank data, CP dengan wheel (1/2048) + crank.

---

## B. Format file FIT (export utama)

FIT (Flexible and Interoperable Data Transfer) adalah format biner dari Garmin, diterima Strava. Gunakan FIT SDK Profile sebagai acuan; Garmin FIT SDK (Java) di balik interface `FitWriter`, atau encoder Kotlin murni di `:core` (pilihan diajukan ke pemilik proyek; lihat `03-ARCHITECTURE.md` §1).

### B.1 Urutan pesan dalam file Activity
```
file_id        (type = activity, manufacturer = development(255), product, time_created, serial_number)
device_info    (opsional; info app & sensor)
event          (timer, start)
record ×N      (1 per detik)
event          (timer, stop_all) / (timer, start)   ← tiap pause/resume
lap ×M
session        (sport = cycling)
activity       (num_sessions = 1, total_timer_time, timestamp, local_timestamp)
```

### B.2 Field `record`
| Field | Satuan FIT | Konversi |
|---|---|---|
| timestamp | detik sejak FIT epoch (1989-12-31T00:00:00Z) | `unix_seconds - 631065600` |
| position_lat / position_long | semicircles (sint32) | `deg * (2^31 / 180)` |
| enhanced_altitude | meter | — |
| distance | meter (kumulatif) | — |
| enhanced_speed | m/s | — |
| heart_rate | bpm | null jika tidak ada |
| cadence | rpm | null jika tidak ada |
| power | watt | **hanya dari power meter sungguhan** |
| temperature | °C | jika tersedia |

Field tanpa nilai → ditulis sebagai "invalid" (tidak ditulis 0).

### B.3 Aturan penting
- **Estimated power TIDAK ditulis ke field `power`.** Strava menganggap field ini data power meter sungguhan dan menghitung estimasinya sendiri bila field kosong.
- `session` dan `lap` berisi total & rata-rata (total_timer_time = waktu bergerak, total_elapsed_time = waktu total, total_distance, total_ascent, avg/max speed/HR/cadence/power, normalized_power bila ada power meter).
- Periksa CRC header & file.
- Validasi: file hasil export harus terbaca oleh FIT SDK `FitCSVTool` atau parser FIT lain (lihat `07-TEST-PLAN.md`).

### B.4 Penamaan file
`RideComp_YYYYMMDD_HHMM.fit` (waktu lokal mulai ride). Disimpan di direktori app; export via share sheet atau "Simpan ke Download" (Storage Access Framework).

---

## C. GPX

### C.1 Import rute
- Dukung `<trk>/<trkseg>/<trkpt>` dan `<rte>/<rtept>`; gabungkan semua segmen.
- Ambil `ele` bila ada. Waypoint `<wpt>` disimpan sebagai titik penanda (opsional ditampilkan).
- Sederhanakan titik (Douglas-Peucker, toleransi ~5 m) untuk tampilan peta; simpan versi asli untuk perhitungan jarak.
- Batas: ukuran file ≤ 20 MB dan ≤ 50.000 titik. Parser **wajib** menonaktifkan DOCTYPE/entity eksternal dan memvalidasi nilai (lihat `11-SECURITY-RULES.md` SEC-IN-01..04).
- Sumber GPX: export rute dari Strava/Komoot/RideWithGPS di situs mereka, atau share dari teman.

### C.2 Export GPX aktivitas
Sebagai alternatif FIT: track dengan `time`, `ele`, dan extension Garmin TrackPointExtension (hr, cad). Power di GPX tidak standar, jadi FIT tetap format utama.

### C.3 Logika mengikuti rute (Fase 3)
- Cari segmen rute terdekat dari posisi saat ini (cari di jendela sekitar indeks terakhir untuk efisiensi; pencarian global hanya saat tersesat).
- **Off-route:** jarak ke rute > 50 m selama ≥ 10 detik → peringatan (getar + suara + banner merah). **Back on route:** < 30 m selama 5 detik → notifikasi kembali.
- Jarak tersisa = panjang rute − jarak sepanjang rute pada titik proyeksi.
- Rute berputar/bersilangan: utamakan kandidat segmen yang indeksnya maju dari posisi terakhir.

---

## D. Peta

- Rendering: MapLibre (vector tiles, data OpenStreetMap).
- **Kewajiban lisensi:** tampilkan atribusi "© OpenStreetMap contributors" (dan penyedia tile) di peta.
- **Jangan** mengunduh massal dari `tile.openstreetmap.org`; kebijakan penggunaannya melarang hal itu.
- Sumber tile untuk offline. Agent harus menyajikan pilihan ini ke pemilik proyek dan memverifikasi ketentuan terbaru penyedia sebelum implementasi:
  1. **File region lokal (disarankan untuk offline):** file vektor (mis. PMTiles/MBTiles) untuk wilayah tertentu (mis. Jawa Tengah) yang dibuat dari ekstrak OSM, lalu disalin ke HP atau diunduh sekali. Tanpa ketergantungan server saat ride.
  2. **Penyedia tile vektor gratis** yang secara eksplisit mengizinkan cache/offline (cek ToS-nya), memakai offline region manager MapLibre.
- Mode breadcrumb (tanpa tile) selalu tersedia sebagai cadangan.

---

## E. Strava

### E.1 Jalur utama: upload manual (gratis, Fase 1)
1. Setelah ride disimpan, pengguna menekan **Export FIT** lalu menyimpan/membagikan file.
2. Di aplikasi ditampilkan panduan: buka situs web Strava → menu upload aktivitas → pilih file FIT → simpan.
3. Agent: cek alur upload terbaru di HP (browser mobile, mungkin perlu "Situs desktop") dan tulis panduan di layar bantuan sesuai hasil uji.

### E.2 Modul opsional: upload otomatis via Strava API (Fase 5)

**Prasyarat (kebijakan Strava sejak Juni 2026):** membuat aplikasi Strava API memerlukan **langganan Strava**. Aplikasi baru berada di mode *single-player* (hanya akun pemilik), dan cukup untuk pemakaian pribadi. Pengguna mendaftarkan app di halaman pengaturan API Strava, lalu memasukkan Client ID & Client Secret ke RideComp.

**Alur:**
1. OAuth 2.0 Authorization Code dengan scope `activity:write` (redirect ke custom scheme app; konfigurasi "Authorization Callback Domain" sesuai dokumentasi Strava).
2. Tukar code → access token + refresh token. Simpan terenkripsi (Android Keystore, lihat `03-ARCHITECTURE.md` §1). **Access token kedaluwarsa tiap 6 jam** → refresh otomatis sebelum request.
3. Upload: `POST /uploads` (multipart) dengan `file` (FIT), `data_type=fit`, `external_id` (unik, mis. UUID aktivitas), `name`, `description` opsional.
4. Polling status: `GET /uploads/{id}` hingga `activity_id` terisi atau `error` (interval 2–5 dtk, maks ~1 menit). Duplikat → tandai sebagai sudah ter-upload.
5. Antrian: jika offline, simpan di antrian dan coba lagi saat ada internet (manual retry dari riwayat).
6. Hormati header rate limit (`X-RateLimit-*`).

**Perubahan yang sudah dijadwalkan Strava (wajib diikuti):**
- Token otorisasi dikirim lewat header `Authorization: Bearer <token>` (jangan lewat query string).
- Base URL baru `https://api-v3.strava.com` tersedia mulai 4 Januari 2027; migrasi wajib sebelum 1 Juni 2027. Simpan base URL sebagai konfigurasi.
- Disconnect memakai `POST /oauth/revoke` (pengganti `oauth/deauthorize` yang pensiun 1 Juni 2027).
- Sebelum mengimplementasikan, agent **wajib** membaca dokumentasi & changelog developer Strava terbaru dan melaporkan perbedaan dengan spesifikasi ini.

**Hasil pemeriksaan T5.0 (2026-11-17, dokumentasi & changelog developers.strava.com):**
| Topik | Spesifikasi lama | Temuan | Keputusan |
|---|---|---|---|
| Halaman otorisasi Android | Custom Tabs | `https://www.strava.com/oauth/mobile/authorize` membuka **aplikasi Strava** bila terpasang (versi ≥ 75), jika tidak membuka browser | Dibuka dengan `Intent.ACTION_VIEW` (browser eksternal/app Strava, SEC-AUTH-01) — tanpa dependency Custom Tabs (DEP-02) |
| PKCE | "bila didukung" | **Tidak didukung**; `client_secret` wajib saat tukar code dan refresh | `state` acak 256-bit + redirect cocok persis; code tanpa secret tidak berguna (risiko sisa didokumentasikan, SEC-AUTH-04) |
| Redirect | custom scheme | Host redirect harus sama dengan *Authorization Callback Domain*; `localhost` selalu diizinkan | Callback Domain = `localhost`; redirect `ridecomp://localhost/strava-auth` |
| Refresh token | — | **Rotasi**: refresh token lama langsung tidak berlaku; access token 6 jam, refresh mengembalikan token yang sama bila sisa > 1 jam; respons berisi `scope` yang disetujui | Token baru disimpan sebelum dipakai; refresh bila sisa < 5 menit; satu refresh pada satu waktu (mutex); scope tanpa `activity:write` ditolak |
| Upload | `POST /uploads` | Multipart `file`, `data_type=fit`, `name`, `description`, `trainer`, `commute`, `external_id`, `sport_type` | Kirim `name`, `external_id` (UUID ride), `sport_type=Ride` |
| Polling | 2–5 dtk, ~1 menit | Maks 1 kali/detik; status teks "still being processed" / "ready" / "error" / "deleted"; duplikat: `error` berisi "duplicate of activity <id>" | Interval 2 dtk, maks 60 dtk lalu dilanjutkan pada proses antrian berikutnya; duplikat → tersambung ke aktivitas yang ada |
| Revoke | `POST /oauth/revoke` | `https://www.strava.com/oauth/revoke`, HTTP Basic `client_id:client_secret`, `token` + `token_type_hint`; `/oauth/deauthorize` pensiun 1 Jun 2027 | Revoke refresh token, token lokal dihapus walau revoke gagal (offline) |
| Base URL | dapat dikonfigurasi | `https://api-v3.strava.com` mulai 4 Jan 2027, wajib sebelum 1 Jun 2027; header `Authorization: Bearer` didukung | Base URL dipilih menurut tanggal (`StravaConfig`), OAuth tetap di `www.strava.com` |

**Perubahan D-82 (2026-11-17, permintaan pemilik):** pengguna tidak lagi membuat app API sendiri. RideComp memakai satu app Strava milik pemilik; Client Secret disimpan di **server token** kecil (`server/strava-token-server`, Cloudflare Worker) yang meneruskan tukar code, refresh, dan revoke ke Strava. App hanya memuat Client ID dan alamat server (konfigurasi build `ridecomp.strava.clientId` / `ridecomp.strava.tokenServerUrl`); tanpa keduanya Strava disembunyikan. Agar akun selain pemilik bisa terhubung, pemilik mengajukan kenaikan kapasitas atlet ke Strava.

**Domain yang dihubungi (SEC-NET-03):** `www.strava.com` (OAuth), alamat server token RideComp (`*.workers.dev`), `www.strava.com/api/v3` hingga 3 Jan 2027 lalu `api-v3.strava.com`. Tidak ada domain lain; izin `INTERNET` diaktifkan kembali hanya untuk modul ini.
