# Feature Report — <TaskID>: <Judul>

**Tanggal:** YYYY-MM-DD
**Branch:** `feature/<TaskID>-<slug>`
**Status:** Menunggu review pemilik / Diterima

## 1. Ringkasan
<Apa yang dibuat, 2–4 kalimat. Perilaku yang terlihat oleh pengguna.>

## 2. Perubahan per lapisan
| Lapisan | File/kelas | Keterangan |
|---|---|---|
| Domain (`:core`) | | |
| Data (`:data`) | | |
| Platform (`:platform`) | | |
| Presentation (`:feature-*`) | | |

## 3. Dependency baru
<Tidak ada / nama + versi + persetujuan pada tanggal ...>

## 4. Resource & siklus hidup
| Resource | Diakuisisi | Dilepas | Diverifikasi dengan |
|---|---|---|---|
| | | | |

## 5. Checklist kode
<Salin bagian ② dari `10-FEATURE-CHECKLIST.md`, centang, beri catatan untuk item yang N/A.>

## 6. Hasil testing
- Unit test baru: <jumlah> (daftar nama test penting)
- Test lain (UI/instrumented/migrasi): <...>
- `./gradlew ktlintCheck detekt lint test`: ✅ / ❌ (tempel ringkasan output)
- Coverage `:core`: <xx%> (sebelumnya <yy%>)
- LeakCanary: bersih / temuan + perbaikan
- StrictMode: bersih / temuan + perbaikan
- `dumpsys` (bila relevan): <ringkasan>
- Uji perangkat: <model HP, Android x, build release, hasil>

## 7. Keamanan
- Checklist review manual (`11-SECURITY-RULES.md` §D): <jawaban 13 poin, ringkas>
- Android Lint Security: <0 temuan / daftar>
- Secret scan (gitleaks): bersih / temuan
- Dependency baru: <tidak ada / hasil evaluasi SUP-07, scan CVE, perubahan merged manifest>
- Komponen ter-export / izin baru: <tidak ada / daftar + alasan>
- Risiko keamanan sisa: <...>

## 8. Penyimpangan dari spesifikasi / RULE-EXCEPTION
<Tidak ada / daftar + alasan + persetujuan>

## 9. Risiko & bug yang diketahui
<...>

## 10. Cara menguji (untuk pemilik proyek)
1. ...
2. ...
