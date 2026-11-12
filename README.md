# RideComp — Paket Dokumen Spesifikasi

> **RideComp** adalah nama kerja (boleh diganti). Aplikasi Android (iOS menyusul) yang mengubah HP murah/low-end menjadi *cycling computer* ala Garmin: merekam GPS, membaca sensor Bluetooth (heart rate, speed/cadence, power), navigasi mengikuti rute GPX, dan menghasilkan file FIT yang bisa di-upload ke Strava.

## Cara memakai paket ini dengan Claude Code

1. Buat folder repo baru, salin **seluruh isi paket ini** ke root repo (struktur `CLAUDE.md` + `../docs` dipertahankan).
2. Jalankan Claude Code di root repo.
3. Prompt pertama yang disarankan:
   ```
   Baca CLAUDE.md dan semua file di docs/. Lalu kerjakan Fase 0 dari docs/06-ROADMAP.md.
   Sebelum menulis kode, tampilkan rencana kerja Fase 0 dan tunggu persetujuan saya.
   ```
4. Kerjakan **satu fase per sesi**. Setiap task menghasilkan Feature Report (`docs`) yang Anda review. Setelah fase lolos Phase Gate di `../docs`, lanjut ke fase berikutnya.

## Isi paket

| File | Isi |
|---|---|
| `CLAUDE.md` | Aturan kerja untuk Claude Code (dibaca otomatis oleh agent) |
| `docs` | Product Requirements: tujuan, persona, fitur, prioritas, batasan |
| `docs` | Desain UI/UX: layar, data field, design system, aturan hemat baterai |
| `docs` | Arsitektur teknis: stack, modul, foreground service, alur data |
| `docs` | Spesifikasi sensor BLE, format FIT/GPX, Strava (manual & API opsional) |
| `docs` | Skema database dan rumus perhitungan metrik |
| `docs` | Fase pengerjaan, task per fase, definition of done |
| `docs` | Strategi uji, uji lapangan, acceptance criteria |
| `docs` | Aturan kode wajib: Clean Architecture, penamaan, Kotlin, coroutines, Compose, data, testing, git |
| `docs` | Pencegahan memory leak, battery leak, resource leak, ANR & crash + cara verifikasinya |
| `docs` | Alur kerja per fitur: rencana → checklist kode → checklist testing → Feature Report |
| `docs` | Keamanan library (supply chain, scan CVE, lisensi) & kode (OWASP MASVS), checklist review manual, threat model |
| `docs` | Spesifikasi fitur tambahan: jeda/lanjut otomatis, pengingat, mode baterai kritis, profil aktivitas, deteksi insiden + SMS, ClimbPro, peringatan belokan, PR, segmen, beban latihan, kartu share, backup & restore |
| `docs` | Template laporan yang diisi agent di akhir setiap task |
| `.editorconfig` | Konfigurasi format kode (ktlint & IDE) |
| `.gitignore` | Mencegah rahasia (keystore, local.properties, token) ikut ter-commit |

## Keputusan utama (sudah disepakati)

- **Platform:** Android dulu, iOS menyusul (kode disiapkan lintas platform).
- **Stack:** Native Android, Kotlin + Jetpack Compose. Logika inti di modul Kotlin murni agar siap Kotlin Multiplatform (iOS) kelak.
- **Navigasi:** mengikuti rute GPX + peringatan keluar jalur (tanpa turn-by-turn routing).
- **Sinkron Strava:** export file FIT + upload manual (gratis) sebagai fitur inti; upload otomatis via Strava API adalah modul opsional (memerlukan langganan Strava, lihat `docs`).
- **Peta:** OpenStreetMap, mendukung offline.

## Perangkat keras yang dibutuhkan pengguna

- HP Android 8.0+ dengan GPS dan Bluetooth Low Energy (BLE).
- Holder HP di setang (wajib, dan sebaiknya yang kokoh).
- Opsional: HR strap BLE, sensor speed/cadence BLE, power meter BLE. Sensor harus mendukung **Bluetooth (BLE)**, bukan hanya ANT+.
- Disarankan: power bank kecil untuk ride panjang.
