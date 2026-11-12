# 05 — Data Model & Metric Formulas

Semua nilai disimpan dalam satuan SI. Waktu disimpan sebagai epoch milidetik UTC.

## 1. Skema database (Room / SQLite)

### `profile` (1 baris)
| Kolom | Tipe | Keterangan |
|---|---|---|
| id | int PK | selalu 1 |
| weight_kg | real | berat badan |
| bike_weight_kg | real | default 9.0 |
| wheel_circumference_mm | int | default 2105 |
| hr_max | int? | |
| hr_rest | int? | |
| ftp_w | int? | |
| birth_year | int? | untuk rumus kalori HR |
| sex | text? | `male`/`female`, opsional, untuk rumus kalori HR |
| riding_position | text | `tops`/`hoods`/`drops` (untuk estimated power) |
| surface | text | `road`/`gravel` (Crr) |

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
| training_load | real? | beban latihan (`../12-FEATURE-SPECS.md` §10) |
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
Satuan, bahasa, sumber cadence prioritas, ambang mode baterai (§BM), setting keselamatan (aktif, nama pengendara), profil aktivitas terakhir, dll. Setting yang bisa berbeda per jenis ride disimpan di `activity_profiles`.

### `strava_uploads` (Fase 5)
`activity_id, upload_id, status, error, created_at, updated_at`.

### `activity_profiles`
`id, name, kind (road|gravel|mtb|indoor|commute|custom), gps_enabled, data_pages_json, auto_pause_enabled, auto_pause_speed_mps, auto_pause_delay_s, idle_prompt_min, drink_interval_min?, eat_interval_min?, reminder_by_distance_m?, auto_lap_m?, screen_mode, surface, riding_position, fit_sport, fit_sub_sport, sort_order`.

### `emergency_contacts`
`id, display_name, phone_e164, sort_order` (maks 3). **Data pribadi pihak ketiga**: tidak pernah di-log atau ikut export/share (`11-SECURITY-RULES.md` SEC-SMS-*).

### `incidents`
`id, activity_id, detected_t, peak_g, speed_before_mps, outcome (cancelled|sent|manual_sos|failed), sms_sent_count, last_status`.

### `route_climbs`
`route_id, idx, start_dist_m, end_dist_m, length_m, gain_m, avg_grade, max_grade, grade_profile_json` (grade per 100 m).

### `route_turns`
`route_id, dist_m, angle_deg, direction (left|right|uturn), severity (normal|sharp|uturn), source (geometry|waypoint)`.

### `best_efforts`
`activity_id, profile_kind, type (distance|power|other), key (mis. 10000 m / 1200 s / longest_ride), value, start_t, end_t, is_pr`. Index `(profile_kind, type, key, value)`.

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
    {"id": "main", "layout": 6, "fields": ["speed","distance","moving_time","hr","cadence","power_3s"]},
    {"id": "map", "type": "map", "fields": ["distance_remaining","speed"]},
    {"id": "climb", "layout": 4, "fields": ["grade","altitude","ascent","elevation_profile"]},
    {"id": "lap", "layout": 6, "fields": ["lap_time","lap_distance","lap_avg_speed","lap_avg_power","lap_avg_hr","clock"]}
  ]
}
```

## 3. Rumus metrik

### 3.1 Jarak (GPS)
Haversine, R = 6.371.000 m. Tambahkan jarak hanya bila titik valid (akurasi ≤ 30 m), status recording, dan bukan jitter diam (lihat `03-ARCHITECTURE.md` §5). Jika sensor roda aktif, jarak = akumulasi dari sensor roda (lebih akurat), GPS sebagai cadangan saat sensor putus.

### 3.2 Kecepatan
- Sumber prioritas: sensor roda > GPS.
- Tampilan "Speed": EMA (α = 0.3) dari nilai per detik.
- Avg speed = distance / moving_time. Max speed: dari nilai yang dihaluskan (hindari spike GPS).

### 3.3 Waktu
- Elapsed = sekarang − started_at.
- Moving time = total detik dalam status `recording` (tidak termasuk pause manual & auto-pause).

### 3.4 Jeda & lanjut otomatis
Default: pause bila speed < 2 km/h (≈ 0.56 m/s) dan tidak ada perpindahan berarti selama **5 detik** (pilihan 3/5/10/30 dtk); resume bila speed > 3 km/h selama ≥ 2 detik **dan** (tanpa sensor roda) perpindahan > max(akurasi GPS, 10 m) dari titik jeda. Pause manual tidak di-resume otomatis. Detail: `../12-FEATURE-SPECS.md` §1.

### 3.5 Elevasi, tanjakan, grade
- Sumber: barometer (bila ada sensor tekanan, dikalibrasi ke altitude GPS di awal) > altitude GPS.
- Smoothing: moving average 10 detik.
- Total tanjakan dengan histeresis: simpan `anchor`; jika `alt − anchor ≥ threshold` → `ascent += alt − anchor`, `anchor = alt`; jika `anchor − alt ≥ threshold` → `descent += ...`, `anchor = alt`. Threshold: 2 m (barometer), 4 m (GPS).
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
Deteksi insiden, tanjakan, belokan, best efforts, pencocokan segmen, beban latihan (TSS-style & TRIMP), dan CTL/ATL/TSB: lihat `../12-FEATURE-SPECS.md` §5–§10.

### 3.13 Backup
Format arsip, isi, dan aturan restore: `../12-FEATURE-SPECS.md` §12. `settings` menyimpan `last_backup_at` dan `backup_reminder_dismissed_at`. `db_schema_version` di manifest = versi database Room.
