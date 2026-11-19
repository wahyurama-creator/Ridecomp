# 10 — Feature Workflow & Checklist (WAJIB untuk setiap task)

Setiap task di `06-ROADMAP.md` mengikuti 4 tahap. Agent **tidak boleh** melompati tahap atau menandai task selesai sebelum semua item checklist terisi.

```
① RENCANA ──(disetujui pemilik)──► ② IMPLEMENTASI + TEST ──► ③ VERIFIKASI ──► ④ FEATURE REPORT ──(diterima pemilik)──► selesai
```

---

## ① Rencana (ditampilkan sebelum menulis kode)

Agent menampilkan:
- [ ] Task ID & tujuan (1–2 kalimat).
- [ ] File/modul yang akan dibuat atau diubah, per lapisan (domain / data / platform / presentation).
- [ ] UseCase, interface, dan model baru.
- [ ] Dependency baru → tabel evaluasi SUP-07 (fungsi, alternatif, pemelihara, rilis terakhir, CVE, lisensi, ukuran, dampak manifest) → **minta persetujuan** (DEP-02).
- [ ] Resource baru yang perlu siklus hidup (GPS, BLE, wakelock, listener, dll.) → rencana pelepasan (`09-RELIABILITY-RULES.md` §1).
- [ ] Daftar test yang akan ditulis (unit, UI, instrumented, simulasi).
- [ ] Dampak keamanan: input luar baru, komponen manifest, data sensitif, file, jaringan, izin (ringkas dari checklist `11-SECURITY-RULES.md` §D).
- [ ] Hal ambigu → disajikan sebagai **pilihan** dengan trade-off.

⏸️ **Tunggu persetujuan pemilik proyek.**

---

## ② Checklist kode

### Arsitektur
- [ ] Dependency rule dipatuhi; `:core` tanpa import Android (ARCH-01).
- [ ] Feature tidak mengimpor `:data`/`:platform` (ARCH-02).
- [ ] Logika bisnis di UseCase/engine `:core`, bukan di ViewModel/Composable/Service (ARCH-04, ARCH-08).
- [ ] Mapper eksplisit antar model lapisan (ARCH-05).
- [ ] UDF: UI → event → ViewModel → UiState (ARCH-06).

### Penamaan & gaya
- [ ] camelCase variabel/fungsi, PascalCase class, SCREAMING_SNAKE konstanta (NAME-01..04).
- [ ] Sufiks peran & satuan konsisten (NAME-10, NAME-12).
- [ ] Tidak ada `!!`, magic number, kode mati, TODO tanpa task ID (KT-03, KT-06, KT-10).
- [ ] KDoc untuk API publik & rumus (KT-11).
- [ ] String & warna dari resource/tema (UI-05).

### Error & konkurensi
- [ ] Tidak ada catch kosong; `CancellationException` tidak ditelan (ERR-01, ERR-02).
- [ ] Error yang diharapkan dimodelkan sebagai tipe hasil dan tampil di UI (ERR-03).
- [ ] Tidak ada `GlobalScope`/`runBlocking`; scope jelas; dispatcher diinjeksi (CO-01..03).
- [ ] Operasi tak tentu punya timeout (CO-09).
- [ ] Tidak ada data race (CONC-01..03).

### Memori & baterai
- [ ] Setiap resource baru terdaftar di tabel siklus hidup + dilepas di tempat yang benar (REL-01).
- [ ] Tidak ada referensi Context/Activity/View di objek berumur panjang (MEM-01).
- [ ] Listener/callback di-unregister; `callbackFlow` punya `awaitClose` (MEM-02, MEM-03).
- [ ] Koleksi di memori berbatas (MEM-05).
- [ ] Wakelock dengan timeout & dilepas di `finally` (BAT-02) — jika relevan.
- [ ] Tidak ada polling/scan/jaringan/alarm yang tidak perlu (BAT-04..07).
- [ ] Flow di UI di-collect lifecycle-aware (CO-05).

### Data
- [ ] DB/file tidak di main thread (DATA-01).
- [ ] Perubahan skema → migrasi + test migrasi (DATA-02).
- [ ] Stream/cursor memakai `use {}` (DATA-05).
- [ ] Tidak ada rahasia/data pribadi di log (ERR-05, DATA-06).

### Keamanan (detail: `11-SECURITY-RULES.md`)
- [ ] Checklist review keamanan manual §D (13 poin) dijawab untuk diff task ini.
- [ ] Input luar divalidasi (panjang, rentang, ukuran, tipe); XML tanpa DOCTYPE/entity (SEC-IN-01..04).
- [ ] Komponen manifest baru `exported=false` atau alasan terdokumentasi; PendingIntent `FLAG_IMMUTABLE` (SEC-PLT-01, 02).
- [ ] Data sensitif hanya di internal storage, rahasia di Keystore, tidak di log (SEC-STO-*).
- [ ] Share file lewat FileProvider (SEC-PLT-04); jaringan HTTPS saja (SEC-NET-*).
- [ ] Tidak ada credential/URL debug ter-hardcode; fitur debug tidak aktif di release (SEC-BLD-04).

### UI (jika ada)
- [ ] Route/Screen terpisah; Screen stateless dengan Preview (UI-01, UI-06).
- [ ] Recomposition terbatas pada field yang berubah (UI-04).
- [ ] Target sentuh & contentDescription (UI-07).
- [ ] Teks UI tersedia dalam `id` & `en`.

---

## ③ Checklist testing & verifikasi

### Otomatis
- [ ] Unit test baru untuk setiap UseCase/parser/rumus/mapper/state transition (TEST-01).
- [ ] Kasus tepi: null/data hilang, nilai ekstrem, rollover, input rusak (STB-09).
- [ ] Test ViewModel event → UiState (TEST-06).
- [ ] Test DAO/migrasi bila menyentuh DB (TEST-07).
- [ ] Test pelepasan resource dengan fake (mis. collector dibatalkan → `unregister` terpanggil).
- [ ] Bug yang ditemukan selama pengerjaan punya regression test (TEST-05).
- [ ] `./gradlew ktlintCheck detekt lint test` **lulus tanpa warning** (atau perintah padanan yang disetujui).
- [ ] Coverage `:core` tidak turun di bawah 80%.
- [ ] Android Lint kategori Security: 0 temuan; secret scan (gitleaks) bersih.
- [ ] Bila ada dependency baru: scan CVE (SUP-06) bersih dari High/Critical; merged manifest ditinjau (SUP-08); lockfile & verification-metadata diperbarui (SUP-03, SUP-04).
- [ ] Test input rusak/berbahaya untuk setiap parser/handler input baru (SEC-IN-06).

### Manual / perangkat
- [ ] Alur fitur dicoba di **build release** pada HP fisik (sebutkan model & versi Android).
- [ ] LeakCanary bersih setelah alur fitur diulang ≥ 5x (build debug) (MEM-07).
- [ ] Logcat bebas pelanggaran StrictMode (STB-01).
- [ ] Bila menyentuh perekaman/GPS/BLE: uji simulasi + verifikasi `dumpsys` bahwa resource dilepas setelah ride selesai (BAT-01).
- [ ] Skenario uji lapangan terkait di `07-TEST-PLAN.md` / `09-RELIABILITY-RULES.md` §6 dijalankan (bila fase mensyaratkan).

---

## ④ Feature Report

Agent membuat laporan menggunakan template `docs/templates/FEATURE_REPORT.md`, menyimpannya di `docs/reports/features/<TaskID>-<slug>.md`, mencentang task (`- [x]`) di `docs/planning/06-ROADMAP.md` + kolom Status fase, di rencana fasenya, dan di `docs/STATUS.md`, dan menampilkannya ke pemilik proyek. Task dianggap **selesai** hanya setelah pemilik proyek menerima laporan.

---

## Checklist akhir fase (Phase Gate)

- [ ] Semua task fase punya Feature Report yang diterima.
- [ ] Acceptance criteria fase di `07-TEST-PLAN.md` §5 lulus.
- [ ] Uji reliabilitas yang relevan (R1, R2, L11–L13) lulus.
- [ ] Dokumen `docs/` diperbarui sesuai perilaku terbaru.
- [ ] Pemeriksaan keamanan akhir fase (`11-SECURITY-RULES.md` §C): scan CVE, lisensi, update dependency, merged manifest, MobSF atas APK release, threat model diperbarui.
- [ ] Tidak ada temuan keamanan High/Critical terbuka.
- [ ] Tidak ada bug P0/P1 terbuka.
