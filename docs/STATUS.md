# Status proyek — mulai di sini

**Diperbarui:** 2026-11-19 · Perbarui file ini setiap kali task selesai (`rules/10-FEATURE-CHECKLIST.md`).

## Posisi saat ini
- **Rilis terakhir:** **0.9.3** (beta), 2026-11-18 · [GitHub Releases](https://github.com/wahyurama-creator/Ridecomp/releases). Skema DB **v10**.
- **Fase selesai:** 0–5, 7, 9, dan rilis beta & masukan pengguna (Fase 8/10). Ringkasan: [`planning/06-ROADMAP.md`](planning/06-ROADMAP.md).
- **Fase berjalan:** **Fase 11 — Analisa ride, usaha terbaik, tema & peta offline.** Rencana lengkap: [`planning/14-PHASE11-PLAN.md`](planning/14-PHASE11-PLAN.md). Keputusan K1–K5 sudah dijawab (D-109…D-113).
- **Task terakhir:** T11.7 — Bagian "Hasil" (selesai 2026-11-19, [Feature Report](reports/features/T11.7-results.md)); sebelumnya T11.6 zona power.
- **Task berikutnya:** **T11.8 — Usaha terbaik otomatis** (rekor durasi + tandai sprint, 14-PHASE11-PLAN §5). Belum dimulai.

## Daftar centang Fase 11
Centang di sini, di `planning/06-ROADMAP.md`, dan di `planning/14-PHASE11-PLAN.md` §4 setiap kali task selesai.

- [x] T11.1 Komponen grafik `RideChart`
- [x] T11.2 Tata letak halaman detail per bagian
- [x] T11.2a Perbaikan StrictMode peta (temuan uji T11.2)
- [x] T11.2b Perbaikan peta saat aplikasi dingin (regresi T11.2a)
- [x] T11.3 Analisa kecepatan
- [x] T11.4 Rombak elevasi
- [x] T11.5 Analisa detak jantung
- [x] T11.5a Perbaikan tautan perkiraan HR max di Profil
- [x] T11.6 Perkiraan zona power Z1–Z7
- [x] T11.7 Bagian "Hasil"
- [ ] T11.8 Usaha terbaik otomatis ← **berikutnya**
- [ ] T11.9 Pilihan tema
- [ ] T11.10 Audit tema terang
- [ ] T11.11 Cache peta otomatis
- [ ] T11.13 Paket peta per provinsi
- [ ] T11.12 Peta wilayah otomatis
- [ ] T11.14 Penyimpanan, baterai, keamanan peta

**Setelah Fase 11 — Fase 12: Detak jantung dari smartwatch** ([`planning/15-PHASE12-PLAN.md`](planning/15-PHASE12-PLAN.md)):
- [ ] T12.1 Uji kompatibilitas jam tangan · Huawei Watch Fit 5 Pro diuji (A1, didukung), Aolon menunggu pemilik · [Feature Report](reports/features/T12.1-smartwatch-compat.md) (menunggu review)
- [ ] T12.2 Pemindaian luas
- [ ] T12.3 UX & panduan jam tangan
- [ ] T12.4 Aplikasi pendamping Wear OS (opsional)
- [ ] T12.5a Identifikasi protokol Aolon (go/no-go) — butuh model & nama aplikasi pendamping dari pemilik
- [ ] T12.5b Sensor "Jam tangan (eksperimental)"
- [ ] T12.5c Uji ride sungguhan

Fase lain: lihat kolom Status di tabel atas `planning/06-ROADMAP.md` (Fase 0–5, 7, 9, 10 selesai; Fase 6 iOS belum mulai).

## Urutan Fase 11
1. **11A** T11.1 grafik → T11.2 tata letak halaman detail (wajib duluan).
2. **11B** T11.3 kecepatan · T11.4 elevasi · T11.5 detak jantung · T11.6 zona power Z1–Z7.
3. **11C** T11.7 bagian "Hasil" · T11.8 usaha terbaik otomatis (rekor durasi + tandai sprint).
4. **11D** T11.9 pilihan tema · T11.10 audit tema terang.
5. **11E** T11.11 cache peta → T11.13 paket peta per provinsi (skrip pemilik) → T11.12 peta wilayah otomatis → T11.14.

Bekerja berdua: setelah 11A selesai, 11B/11C (halaman detail) bisa paralel dengan 11D (tema) atau 11E (peta).
Setiap task wajib lolos **anggaran performa** (14-PHASE11-PLAN §8) dengan build rilis.

## Setup di device baru
1. `git clone https://github.com/wahyurama-creator/Ridecomp.git` (atau `git pull`).
2. JDK **21** + Android SDK (API 37). `./gradlew assembleDebug` harus berhasil.
3. `git config core.hooksPath scripts/git-hooks` (cek secret sebelum commit).
4. Baca berurutan: [`../CLAUDE.md`](../CLAUDE.md) → file ini → [`README.md`](README.md) (peta dokumen) → aturan di [`rules/`](rules/) → [`planning/14-PHASE11-PLAN.md`](planning/14-PHASE11-PLAN.md).
5. Dengan Claude Code: *"Baca CLAUDE.md dan docs/STATUS.md, lalu kerjakan task berikutnya. Tampilkan rencananya dulu."*

## Alur setiap task (ringkas)
Branch `feature/<TaskID>-<slug>` → rencana disetujui pemilik → kode + test → `./gradlew ktlintCheck detekt lint test koverVerify assembleDebug` → uji perangkat (HP low-end, build rilis untuk performa) → Feature Report di `reports/features/` → keputusan baru di `reports/DECISIONS.md` → perbarui file ini → merge `--no-ff` ke `main`.

## Hanya di laptop pemilik (tidak ada di repo)
- **Kunci rilis** `~/.ridecomp/ridecomp-release.jks` + `keystore.properties`: hanya pemegangnya yang bisa membuat APK rilis yang menimpa versi pengguna. Simpan cadangan di dua tempat; jangan pernah di-commit. Kolaborator cukup build debug.
- Login `gh` untuk membuat GitHub Release; secret token server Strava (Cloudflare Worker).

## Membuat rilis
Naikkan `versionCode`/`versionName` di `app/build.gradle.kts` → catatan rilis `releases/RELEASE-NOTES-x.y.z.md` → `./gradlew … assembleRelease` → cek sertifikat sama (`apksigner`, SHA-256 `F4:E9:68:90…:09:41`) → `SHA256SUMS.txt` → tag `vX.Y.Z` → `gh release create … --prerelease` dengan APK per ABI, checksum, `releases/PRIVACY.md`. Pengguna 0.9.1+ otomatis melihat kartu "Versi … tersedia".

## Catatan terbuka
- Data uji di HP: ride simulasi "Gowes Siang" (tanpa HR) ditambahkan atas izin pemilik saat uji T11.5.
- Anggaran performa Fase 11 (§8): T11.1–T11.2 baru diukur dengan build debug (indikasi); pengukuran build rilis + pembanding sebelum Fase 11 di akhir fase.
- Strava: kapasitas atlet 10 (self-service); pengajuan kenaikan menunggu 10 slot penuh (formulir sudah diisi, belum dikirim).
- Uji lapangan nyata (bukan simulasi) untuk smart trainer dan ride panjang belum dilakukan ([`testing/field-tests.md`](testing/field-tests.md)).
