# Dokumen RideComp

Peta seluruh dokumen proyek. **Baru bergabung? Mulai dari [`STATUS.md`](STATUS.md)**: status terkini, task berikutnya, dan cara setup.

Nama file dengan nomor (`08-CODING-RULES.md` dst.) tetap dipakai di kode dan dokumen sebagai rujukan singkat ("09 §1", "12-FEATURE-SPECS §11"), jadi nomornya tidak pernah diubah.

| Folder | Isi | Kapan dibaca |
|---|---|---|
| [`product/`](product/) | [01-PRD](product/01-PRD.md) tujuan & fitur · [02-DESIGN](product/02-DESIGN.md) UI/UX & aturan tampilan · [12-FEATURE-SPECS](product/12-FEATURE-SPECS.md) spesifikasi fitur tambahan · [13-UI-SCREENS](product/13-UI-SCREENS.md) desain tiap layar | Sebelum mengubah perilaku atau tampilan |
| [`architecture/`](architecture/) | [03-ARCHITECTURE](architecture/03-ARCHITECTURE.md) modul & alur data · [04-INTEGRATIONS](architecture/04-INTEGRATIONS.md) BLE, FIT/GPX, Strava · [05-DATA-MODEL](architecture/05-DATA-MODEL.md) skema DB & rumus | Sebelum menyentuh `:core`, `:data`, `:platform` |
| [`planning/`](planning/) | [06-ROADMAP](planning/06-ROADMAP.md) fase & daftar task bercentang · [14-PHASE11-PLAN](planning/14-PHASE11-PLAN.md) rencana Fase 11 · [15-PHASE12-PLAN](planning/15-PHASE12-PLAN.md) rencana Fase 12 (smartwatch) · [16-PHASE13-PLAN](planning/16-PHASE13-PLAN.md) rencana Fase 13 (layar kecil) | Memilih task berikutnya |
| [`rules/`](rules/) **(wajib)** | [08-CODING-RULES](rules/08-CODING-RULES.md) · [09-RELIABILITY-RULES](rules/09-RELIABILITY-RULES.md) · [10-FEATURE-CHECKLIST](rules/10-FEATURE-CHECKLIST.md) alur tiap task · [11-SECURITY-RULES](rules/11-SECURITY-RULES.md) | Setiap task |
| [`testing/`](testing/) | [07-TEST-PLAN](testing/07-TEST-PLAN.md) strategi uji & acceptance · [field-tests](testing/field-tests.md) catatan uji lapangan · [smartwatch-compat](testing/smartwatch-compat.md) kompatibilitas jam tangan (T12.1) | Tahap verifikasi |
| [`releases/`](releases/) | Catatan rilis `RELEASE-NOTES-x.y.z.md` · [PRIVACY](releases/PRIVACY.md) kebijakan privasi (ikut setiap rilis) | Membuat rilis |
| [`reports/`](reports/) | [DECISIONS](reports/DECISIONS.md) semua keputusan (D-xx) · `features/` Feature Report per task · `security/` hasil scan CVE per fase | Riwayat & alasan keputusan |
| [`templates/`](templates/) | [FEATURE_REPORT](templates/FEATURE_REPORT.md) | Menutup task |
| [`brand/`](brand/) | Ikon, poster, gambar pratinjau sosial | Materi publikasi |

Aturan kerja agent ada di [`../CLAUDE.md`](../CLAUDE.md); panduan pengguna & build di [`../README.md`](../README.md).
