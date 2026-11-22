# 05 — Data Model & Metric Formulas

Semua nilai disimpan dalam satuan SI. Waktu disimpan sebagai epoch milidetik UTC.

## 1. Skema database (Room / SQLite)

### `profile` (1 baris)
| Kolom | Tipe | Keterangan |
|---|---|---|
| id | int PK | selalu 1 |
| weight_kg | real | berat badan |
| bike_weight_kg | real | default 9.0; sejak v8 hanya nilai cadangan — saat ride diganti nilai sepeda terpilih (`bikes`, D-89) |
| wheel_circumference_mm | int | default 2105; idem |
| hr_max | int? | |
| hr_rest | int? | |
| ftp_w | int? | |
| birth_year | int? | untuk rumus kalori HR |
| sex | text? | `male`/`female`, opsional, untuk rumus kalori HR |
| riding_position | text | `tops`/`hoods`/`drops` (untuk estimated power) |
| surface | text | `road`/`gravel Skema v3. Kolom setting ride bernilai null = ikuti pengaturan umum; `auto_lap_m = 0` = auto-lap mati untuk profil ini; `data_pages_json` null = halaman data umum (D-32). Profil bawaan di-seed oleh migrasi 2→3 / `onCreate`; profil terakhir di `settings` `app.last_profile_id`.` (Crr) |

### `sensors`
| Kolom | Tipe |
|---|---|
| id | int PK |
| remote_id | text unique (alamat BLE) |
| name | text |
| services | text (CSV: `hr,csc,cp`) |
| last_battery | int? |
| auto_connect | bool |
| created_at | int |

### `activities`
| Kolom | Tipe | Keterangan |
|---|---|---|
| id | int PK | |
| uuid | text unique | dipakai sebagai `external_id` Strava |
| name | text | |
| status | text | `recording`/`paused`/`finished`/`discarded` |
| started_at / ended_at | int / int? | |
| elapsed_s / moving_s | int | |
| distance_m | real | |
| ascent_m / descent_m | real | |
| avg_speed / max_speed | real | m/s |
| avg_hr / max_hr | int? | |
| avg_cadence | int? | |
| avg_power / max_power / np_power | int? | hanya power meter |
| est_avg_power | int? | estimasi, terpisah |
| calories | int? | |
| route_id | int? FK | |
| profile_id | int FK | profil aktivitas yang dipakai |
| training_load | real? | beban latihan (`12-FEATURE-SPECS.md` §10) |
| load_method | text? | `power`/`hr` |
| intensity_factor | real? | hanya metode power |
| fit_exported_at | int? | untuk status "FIT diekspor" di riwayat |
| fit_path | text? | |
| strava_activity_id | text? | |
| last_checkpoint_at | int | untuk recovery |

### `track_points` (1 baris per detik)
| Kolom | Tipe |
|---|---|
| activity_id | int FK (index) |
| t | int (epoch ms) |
| lat / lon | real? |
| alt_m | real? (setelah smoothing) |
| gps_accuracy_m | real? |
| distance_m | real (kumulatif) |
| speed_mps | real? |
| hr | int? |
| cadence | int? |
| power | int? |
| est_power | int? |
| temp_c | real? |

PK komposit `(activity_id, t)`. Insert batch tiap 5 detik.

### `events`
`activity_id, t, type` (`start`, `pause`, `resume`, `auto_pause`, `auto_resume`, `stop`, `gap`, `sensor_lost`, `sensor_back`).

### `laps`
`activity_id, index, start_t, end_t, distance_m, moving_s, avg_speed, max_speed, avg_hr, max_hr, avg_cadence, avg_power, max_power, ascent_m, trigger` (`manual`/`distance`).

### `routes` & `route_points`
- `routes`: `id, name, source_file, distance_m, ascent_m, created_at`
- `route_points`: `route_id, seq, lat, lon, ele?, cum_dist_m` (index `route_id, seq`)

### `settings` (key-value)
Satuan, bahasa, sumber cadence prioritas, ambang mode baterai (§BM), setting keselamatan (aktif, nama pengendara), profil aktivitas terakhir, batas cache peta (`app.map_cache_limit`: small|medium|large = 50/150/300 MB desimal, default medium) dan penanda `map.cache_filled` (T11.11, D-128; tile-nya di database MapLibre `mbgl-offline.db`, bukan di Room), tema aplikasi (`app.theme`: dark|light|system, default dark, T11.9; salinan cepat di SharedPreferences `ridecomp_theme` untuk frame pertama, D-126), dll. Setting yang bisa berbeda per jenis ride disimpan di `activity_profiles`.

### `strava_uploads` (Fase 5)
`activity_id, upload_id, status, error, created_at, updated_at`.

### `activity_profiles`
`id, name, kind (road|gravel|mtb|indoor|commute|custom), gps_enabled, data_pages_json, auto_pause_enabled, auto_pause_speed_mps, auto_pause_delay_s, idle_prompt_min, drink_interval_min?, eat_interval_min?, reminder_by_distance_m?, auto_lap_m?, screen_mode, surface, riding_position, fit_sport, fit_sub_sport, sort_order`.

### `bikes` (skema v8, D-89)
`id, name, profile_id, weight_kg, wheel_circumference_mm`. Sepeda tersimpan yang dipilih sebelum ride. `profile_id` = jenis sepeda (id `activity_profiles`, tanpa foreign key: profil kustom yang terhapus jatuh ke profil default saat ride mulai). `name` kosong = "Sepedaku"/"My bike" (sepeda pertama yang dibuat app). Migrasi 7→8 / `onCreate` membuat sepeda pertama dari `profile.bike_weight_kg`/`wheel_circumference_mm` dan profil terakhir dipakai (atau Road). Minimal satu sepeda selalu ada. Sepeda terakhir di `settings` `app.last_bike_id`; ride yang dipulihkan memakai sepeda terpilih itu.

### Skema v10 (T10.8, D-102)
Tanpa perubahan tabel: migrasi 9→10 hanya menambah baris profil bawaan `FOLDING` (sepeda lipat) di `activity_profiles` untuk instalasi lama; instalasi baru mendapat semua profil di `onCreate`. Migrasi 2→3 hanya mengisi lima profil awal agar tidak ada duplikat.

### `bike_components` (skema v9, T7.3, D-93)
`id, bike_id (FK bikes, CASCADE), type (chain|tires|brake_pads|cassette|service|other), name, installed_at, initial_distance_m, limit_m?`. Jarak komponen = `initial_distance_m` + jumlah `distance_m` ride `finished` dengan `activities.bike_id` = sepedanya dan `started_at >= installed_at` (dihitung di SQL, tidak disimpan; menghapus ride ikut mengurangi). "Baru diganti" = `installed_at` sekarang, `initial_distance_m` 0. `activities.bike_id` (v9, tanpa FK, berindeks) = sepeda ride; ride sebelum v9 diberi sepeda pertama.

### `emergency_contacts`
`id, display_name, phone_e164, sort_order` (maks 3). **Data pribadi pihak ketiga**: tidak pernah di-log atau ikut export/share (`11-SECURITY-RULES.md` SEC-SMS-*).

### `incidents`
`id, activity_id, detected_t, peak_g, speed_before_mps, outcome (cancelled|sent|manual_sos|failed), sms_sent_count, last_status`.

### `route_climbs`
`route_id, idx, start_dist_m, end_dist_m, length_m, gain_m, avg_grade, max_grade, grade_profile_json` (grade per 100 m).

### `route_turns`
`route_id, dist_m, angle_deg, direction (left|right|uturn), severity (normal|sharp|uturn), source (geometry|waypoint)`.

### `best_efforts`
`activity_id, profile_kind, type (distance|speed|power|other), key (mis. distance_10000 / speed_60 / power_1200 / longest_ride), value, start_t, end_t, is_pr`. Index `(profile_kind, type, key, value)`. `speed` (T11.8): m/s rata-rata terbaik selama durasi `key`, lebih tinggi lebih baik. Tambahan jenis/kunci tidak butuh migrasi: langkah analisa rekor diberi versi baru (`AnalysisConfig.BEST_EFFORTS_VERSION`, kini 4) sehingga ride lama dihitung ulang sekali (D-125).

### `segments`, `segment_points`, `segment_efforts`
- `segments`: `id, name, length_m, gain_m, avg_grade, start_lat, start_lon, end_lat, end_lon, bbox (min/max lat/lon), created_from (activity|climb), created_at`
- `segment_points`: `segment_id, seq, lat, lon, cum_dist_m`
- `segment_efforts`: `segment_id, activity_id, start_t, end_t, elapsed_s, avg_speed, avg_hr?, avg_power?, is_pr`

### `daily_training`
`date (yyyy-MM-dd lokal) PK, load, ctl, atl, tsb` — dihitung ulang inkremental dari tanggal ride yang berubah ke depan.

### `events` (tipe tambahan)
`auto_pause`, `auto_resume`, `reminder_drink`, `reminder_eat`, `reminder_ack`, `battery_mode_saver`, `battery_mode_critical`, `battery_mode_emergency`, `incident_detected`, `incident_cancelled`, `sos_sent`, `sms_failed`, `segment_start`, `segment_end`.

## 2. Konfigurasi halaman data (JSON di settings)
```json
{
  "pages": [
    {"id": "main", "layout": 7, "fields": ["speed","distance","moving_time","hr","cadence","power_3s","avg_speed"]},
    {"id": "map", "type": "map", "fields": ["distance_remaining","speed"]},
    {"id": "climb", "layout": 4, "fields": ["grade","altitude","ascent","elevation_profile"]},
    {"id": "lap", "layout": 5, "fields": ["lap_time","lap_distance","lap_avg_speed","lap_avg_hr","lap_avg_power"]}
  ]
}
```

## 3. Rumus metrik

### 3.1 Jarak (GPS)
Haversine, R = 6.371.000 m. Tambahkan jarak hanya bila titik valid (akurasi ≤ 30 m), status recording, dan bukan jitter diam (lihat `03-ARCHITECTURE.md` §5). Jika sensor roda aktif, jarak = akumulasi dari sensor roda (lebih akurat), GPS sebagai cadangan saat sensor putus.

- **Celah GPS saat mengikuti rute (D-150):** fix yang kembali setelah ≥ 5 detik tanpa fix disambung **sepanjang rute** bila kedua titik ≤ 50 m dari rute, titik baru tidak di belakang, jarak rute ≥ garis lurus dan ≤ 3× garis lurus, serta ≤ 120 km/j; selain itu garis lurus.

### 3.2 Kecepatan
- Sumber prioritas: sensor roda > GPS.
- Tampilan "Speed": EMA (α = 0.3) dari nilai per detik.
- Avg speed = distance / moving_time. Max speed: dari nilai yang dihaluskan (hindari spike GPS).

### 3.3 Waktu
- Elapsed = sekarang − started_at.
- Moving time = total detik dalam status `recording` (tidak termasuk pause manual & auto-pause).

### 3.4 Jeda & lanjut otomatis
Default: pause bila speed < 2 km/h (≈ 0.56 m/s) dan tidak ada perpindahan berarti selama **5 detik** (pilihan 3/5/10/30 dtk); resume bila speed > 3 km/h selama ≥ 2 detik **dan** (tanpa sensor roda) perpindahan > max(akurasi GPS, 10 m) dari titik jeda. Pause manual tidak di-resume otomatis. Detail: `12-FEATURE-SPECS.md` §1.

### 3.5 Elevasi, tanjakan, grade
- Sumber: barometer (bila ada sensor tekanan, dikalibrasi ke altitude GPS di awal) > altitude GPS. **Satu sumber per ride**: di mode barometer, detik tanpa bacaan barometer yang segar dilewati, tidak diganti altitude GPS (D-148).
- Smoothing: barometer moving average 10 detik; GPS **median 30 detik** lalu perubahan dibatasi **0,5 m per sampel 1 detik** (lonjakan GPS ratusan meter diabaikan, D-148).
- Total tanjakan dengan histeresis: simpan `anchor`; jika `alt − anchor ≥ threshold` → `ascent += alt − anchor`, `anchor = alt`; jika `anchor − alt ≥ threshold` → `descent += ...`, `anchor = alt`. Threshold: 2 m (barometer), 10 m (GPS). Tanpa barometer, tanjakan tetap kebesaran (altitude GPS melayang ±40 m dalam hitungan menit) → ditampilkan sebagai **perkiraan** di detail ride.
- Grade = Δalt / Δdist pada jendela jarak ~50 m (minimal 30 m ditempuh), dibatasi ±30%, tampil 0 decimal.

### 3.6 Power (power meter)
- Power 3s / 10s: rata-rata bergulir 3/10 detik (detik tanpa data dihitung 0 saat bergerak, diabaikan saat pause).
- Avg power: rata-rata semua detik moving (termasuk 0 saat coasting).
- **Normalized Power (NP):** rata-rata bergulir 30 detik → pangkat 4 → rata-rata → akar pangkat 4. Hanya bila ≥ 30 detik data.
- W/kg = power / weight_kg.

### 3.7 Estimated power (tanpa power meter)
```
m   = weight_kg + bike_weight_kg
θ   = atan(grade)
v   = speed (m/s), a = dv/dt (dihaluskan)
F_roll = Crr · m · g · cos θ
F_grav = m · g · sin θ
F_aero = 0.5 · ρ · CdA · v²
F_acc  = m · a
P = max(0, (F_roll + F_grav + F_aero + F_acc) · v / η)
```
Default: g = 9.81; ρ = 1.16 kg/m³ (≈30°C dataran rendah); η = 0.97; Crr road 0.005 / gravel 0.008; CdA tops 0.40 / hoods 0.32 / drops 0.27.
Jika cadence = 0 (coasting) dan tersedia sensor cadence → P = 0. Tampil dengan label **"EST"**, disimpan di `est_power`, **tidak** di FIT `power`.

### 3.8 Heart rate & zona
- Avg HR: rata-rata detik moving dengan HR valid. Max HR: nilai maksimum.
- Zona HR default (% HR max): Z1 < 60, Z2 60–70, Z3 70–80, Z4 80–90, Z5 ≥ 90. Bisa diedit.
- Jika HR max tidak diisi: tawarkan estimasi 220 − umur (ditandai sebagai perkiraan, sarankan uji lapangan).

### 3.9 Zona power (berdasarkan FTP)
Z1 < 55%, Z2 56–75%, Z3 76–90%, Z4 91–105%, Z5 106–120%, Z6 121–150%, Z7 > 150%.

Batas watt: atas zona = `floor(FTP × %)` (inklusif), bawah = atas zona sebelumnya + 1; Z1 mulai 0 W, Z7 tanpa batas atas.
Waktu di zona dihitung dari sampel 1 Hz **saat bergerak** (kecepatan > 0): power meter bila ride punya power, selain itu estimated power (§3.7, berlabel "Perkiraan", hanya untuk tampilan — tidak pernah ditulis ke FIT `power`).

**Perkiraan FTP** (urutan prioritas, T11.6):
1. FTP di profil.
2. 95 % × power rata-rata 20 menit terbaik dari **power meter** dalam 6 minggu terakhir (`best_efforts`, ride selesai).
3. Berat × W/kg sesuai level: pemula 2,0 · menengah 2,5 · berpengalaman 3,0 (level kosong = menengah; berat kosong = default profil 70 kg).

Sumber FTP selalu ditampilkan di bawah zona dengan tautan ke Profil.

### 3.10 Kalori (urutan prioritas)
1. **Power meter:** `kcal ≈ total_kJ` (asumsi efisiensi tubuh ~24%; 1 kJ mekanik ≈ 1 kcal metabolik).
2. **HR (Keytel et al.)**, butuh umur, berat, jenis kelamin, per menit:
   - Pria: `(-55.0969 + 0.6309·HR + 0.1988·W + 0.2017·A) / 4.184`
   - Wanita: `(-20.4022 + 0.4472·HR − 0.1263·W + 0.074·A) / 4.184`
3. **Estimated power** (seperti no. 1, ditandai estimasi).
4. Tidak ada data cukup → tampil `--`.

### 3.11 Cadence
Avg cadence: rata-rata detik moving dengan cadence > 0 (tidak termasuk coasting), konvensi umum cycling computer. Didokumentasikan di layar bantuan.

### 3.12 Rumus fitur tambahan
Deteksi insiden, tanjakan, belokan, best efforts, pencocokan segmen, beban latihan (TSS-style & TRIMP), dan CTL/ATL/TSB: lihat `12-FEATURE-SPECS.md` §5–§10.

### 3.13 Backup
Format arsip, isi, dan aturan restore: `12-FEATURE-SPECS.md` §12. `settings` menyimpan `last_backup_at` dan `backup_reminder_dismissed_at`. `db_schema_version` di manifest = versi database Room.
