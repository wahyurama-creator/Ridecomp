# 11 — Security Rules: Library (Supply Chain) & Kode (WAJIB)

Acuan: **OWASP MASVS** (Mobile Application Security Verification Standard) dan Android security best practices. Agent wajib memverifikasi rekomendasi tool/API terbaru saat implementasi, karena praktik keamanan berubah.

**Profil risiko RideComp:** aplikasi offline-first tanpa backend sendiri. Data sensitif: **lokasi & rute (bisa menunjukkan alamat rumah)**, data kesehatan (HR), dan token Strava (Fase 5). Permukaan serangan utama: library pihak ketiga, file dari luar (GPX), intent dari app lain, data BLE dari perangkat sekitar, penyimpanan lokal, dan OAuth.

> Sama seperti aturan reliabilitas: tidak ada proses yang menjamin aplikasi 100% aman. Tujuannya adalah meminimalkan permukaan serangan dan menangkap masalah sedini mungkin, secara otomatis maupun manual.

---

## A. Keamanan library & supply chain

### A.1 Aturan

| ID | Aturan | Verifikasi |
|---|---|---|
| SUP-01 | Repository hanya `google()` dan `mavenCentral()`. Repository lain (JitPack dll.) hanya dengan persetujuan dan `content { includeGroup(...) }` agar tidak bisa menyamar sebagai library lain. | Review `../settings.gradle.kts` |
| SUP-02 | **Tidak ada versi dinamis** (`+`, `latest.release`, range). Semua versi dikunci di `libs.versions.toml`. | `./gradlew dependencies` + review |
| SUP-03 | **Dependency locking** Gradle aktif (lockfile ikut di-commit), agar dependency transitif tidak berubah diam-diam. | Lockfile ada & up to date |
| SUP-04 | **Dependency verification** Gradle (`gradle/verification-metadata.xml` dengan checksum SHA-256) aktif; setiap update dependency memperbarui metadata secara sadar. | Build gagal bila checksum tidak cocok |
| SUP-05 | **Gradle Wrapper** divalidasi (checksum `gradle-wrapper.jar` dicocokkan dengan checksum resmi Gradle) saat dibuat & setiap upgrade. | Catatan di Feature Report |
| SUP-06 | **Scan kerentanan dependency** (CVE) dengan OWASP Dependency-Check (Gradle plugin) dan/atau OSV-Scanner atas lockfile. Kerentanan **High/Critical = blocker**; Medium dievaluasi & dicatat. | Laporan scan |
| SUP-07 | **Evaluasi sebelum menambah dependency** (melengkapi DEP-02): fungsi & alasan; alternatif tanpa dependency; pemelihara & tanggal rilis terakhir; CVE terbuka (cek OSV/NVD/GitHub Advisory); lisensi kompatibel (Apache-2.0/MIT/BSD diutamakan; GPL perlu persetujuan); ukuran; **izin & komponen yang ditambahkan ke manifest**. | Tabel evaluasi di rencana task |
| SUP-08 | **Review merged manifest** setiap ada dependency baru: tidak boleh ada izin, `service`, `receiver`, `provider`, atau `activity` ter-export yang tidak diharapkan dari library. | `app/build/intermediates/merged_manifest/...` atau tab Merged Manifest |
| SUP-09 | Tidak ada SDK analytics, iklan, tracking, atau crash reporting pihak ketiga (DEP-03). | Review dependency tree |
| SUP-10 | Update dependency dilakukan berkala (minimal tiap akhir fase): cek versi baru & advisory, update dalam commit terpisah `chore(deps): ...`, jalankan seluruh test. | Phase Gate |
| SUP-11 | **Secret scanning** (mis. gitleaks) sebelum commit/push; tidak ada keystore, `../local.properties`, token, atau client secret di repo. | Hasil scan bersih |

### A.2 Tool (agent mengajukan pilihan final di T0.13)

| Kebutuhan | Pilihan utama | Catatan |
|---|---|---|
| CVE dependency | OWASP Dependency-Check Gradle plugin | Sebaiknya memakai NVD API key (gratis) agar update database cepat; key tidak di-commit |
| CVE (alternatif/pelengkap) | OSV-Scanner atas Gradle lockfile | Ringan, database OSV |
| Lisensi | Gradle license report plugin | Daftar lisensi semua dependency |
| Secret | gitleaks (pre-commit hook) | |
| Static analysis keamanan | Android Lint (kategori Security, severity dinaikkan jadi error) + detekt | Sudah ada di verifikasi standar |
| Scan APK (per fase) | MobSF (open source, dijalankan lokal) | Analisis statis APK release: izin, komponen ter-export, konfigurasi, secret ter-hardcode |

### A.3 Memperbarui `verification-metadata.xml` (SUP-04)

Metadata **wajib** dibuat ulang dengan cache Gradle **kosong**. Dengan cache yang sudah terisi, Gradle tidak mencatat sebagian file metadata (`.module` BOM, POM parent), sehingga build gagal di clone baru (D-48).

```
GRADLE_USER_HOME=<folder sementara kosong> ./gradlew --no-configuration-cache --continue \
  --write-verification-metadata sha256 help assembleDebug assembleRelease test lint ktlintCheck detekt koverVerify generateLicenseReport
```

1. Diff harus **hanya menambah** entri. Checksum yang berubah = berhenti dan laporkan ke pemilik.
2. Cocokkan file baru dengan checksum yang dipublikasikan repository (`.sha1` di Maven Central / Google Maven).
3. Verifikasi ulang dengan `GRADLE_USER_HOME` kosong yang lain **tanpa** `--write-verification-metadata`.
4. Setiap upgrade Gradle: tambahkan juga `gradle:gradle:<versi>` → `gradle-<versi>-src.zip` (diunduh sync IDE), checksum dari `https://services.gradle.org/distributions/gradle-<versi>-src.zip.sha256`.

---

## B. Keamanan kode (aturan)

### B.1 Penyimpanan data (MASVS-STORAGE, MASVS-PRIVACY)

| ID | Aturan |
|---|---|
| SEC-STO-01 | Semua data (DB, FIT, GPX, crash log) di **internal storage** app (`filesDir`/`noBackupFilesDir`). Tidak menulis ke external/shared storage kecuali export yang dipilih pengguna lewat Storage Access Framework / share sheet. |
| SEC-STO-02 | Token & rahasia hanya disimpan terenkripsi dengan kunci di **Android Keystore** (DATA-06). Tidak di Room biasa, DataStore plain, SharedPreferences plain, atau file teks. |
| SEC-STO-03 | `android:allowBackup` & aturan backup (`dataExtractionRules` / `fullBackupContent`): **token dan rahasia dikecualikan** dari backup & device transfer. Keputusan apakah data ride ikut backup diajukan ke pemilik proyek. |
| SEC-STO-04 | Log, crash log, dan notifikasi tidak berisi koordinat mentah, token, atau ID akun di build release (ERR-05). Crash log yang diekspor pengguna disanitasi. |
| SEC-STO-05 | **Privasi lokasi:** fitur "zona privasi" (radius di sekitar titik yang ditentukan pengguna, mis. rumah) yang memangkas titik GPS di dalam radius **saat export FIT/GPX** dipertimbangkan sebagai fitur (ajukan ke pemilik; default mati). Strava juga punya fitur privasi sendiri. |
| SEC-STO-06 | Hapus aktivitas = hapus baris DB **dan** file FIT/GPX terkait. File sementara (export/share) dibersihkan setelah dipakai. |

### B.2 Komponen platform & IPC (MASVS-PLATFORM)

| ID | Aturan |
|---|---|
| SEC-PLT-01 | Semua `activity`, `service`, `receiver`, `provider` **`android:exported="false"`** kecuali yang memang harus (launcher activity; activity penerima "Buka dengan" GPX; redirect OAuth Fase 5). Setiap komponen ter-export didokumentasikan alasannya. |
| SEC-PLT-02 | `RecordingService` tidak ter-export. Kontrol dari notifikasi memakai `PendingIntent` eksplisit dengan **`FLAG_IMMUTABLE`**. |
| SEC-PLT-03 | Intent masuk (GPX, OAuth redirect) diperlakukan sebagai **input tidak tepercaya**: validasi skema URI (`content://`/`file://` sesuai kebijakan), MIME/ekstensi, dan ukuran sebelum diproses. |
| SEC-PLT-04 | Share file (FIT/GPX) hanya lewat **`FileProvider`** dengan path terbatas (folder export saja) dan izin URI sementara (`FLAG_GRANT_READ_URI_PERMISSION`). Tidak pernah `file://` URI ke app lain. |
| SEC-PLT-05 | Tidak memakai WebView. Bila kelak diperlukan: JavaScript mati secara default, tanpa `addJavascriptInterface`, hanya konten lokal/HTTPS. |
| SEC-PLT-06 | Izin minimal sesuai daftar di `03-ARCHITECTURE.md` §2; `BLUETOOTH_SCAN` dengan `neverForLocation`. Izin baru wajib disetujui. |
| SEC-PLT-07 | Layar setting Strava (berisi Client Secret saat input): pertimbangkan `FLAG_SECURE` agar tidak muncul di screenshot/recent apps. |

### B.3 Input tidak tepercaya (MASVS-CODE)

| ID | Aturan |
|---|---|
| SEC-IN-01 | **Parser GPX/XML:** nonaktifkan pemrosesan DOCTYPE/entity eksternal (`FEATURE_PROCESS_DOCDECL = false`) untuk mencegah XXE & "billion laughs". Batasi ukuran file (mis. ≤ 20 MB) dan jumlah titik (≤ 50.000), tolak dengan pesan yang jelas bila melebihi. |
| SEC-IN-02 | Nilai numerik dari file (lat/lon/ele/time) divalidasi rentangnya (lat ±90, lon ±180, elevasi wajar); nilai NaN/Infinity ditolak. |
| SEC-IN-03 | **Parser BLE:** setiap pembacaan byte memeriksa panjang buffer sebelum membaca (tidak ada `IndexOutOfBounds` dari paket pendek/berbahaya); nilai di luar rentang dibuang (STB-09). Perangkat BLE asing bisa mengirim data sembarang. |
| SEC-IN-04 | Nama file/nama rute dari luar tidak dipakai langsung sebagai path (cegah **path traversal**); nama file internal dibuat sendiri (UUID/timestamp), nama asli hanya disimpan sebagai label teks. |
| SEC-IN-05 | Room: hanya query berparameter (`@Query` dengan argumen); dilarang menyusun SQL dari string input pengguna. |
| SEC-IN-06 | Parser memiliki **fuzz test** sederhana (input acak/terpotong) yang memastikan tidak ada crash (lihat TEST-xx di `07-TEST-PLAN.md`). |

### B.4 Jaringan (MASVS-NETWORK)

| ID | Aturan |
|---|---|
| SEC-NET-01 | **Network Security Config:** `cleartextTrafficPermitted="false"` untuk semua domain. Hanya HTTPS. |
| SEC-NET-02 | Tidak ada custom `TrustManager`/`HostnameVerifier` yang menerima semua sertifikat, tidak ada bypass SSL di build mana pun. |
| SEC-NET-03 | Domain yang dihubungi didokumentasikan: `tiles.openfreemap.org` (peta online, D-90), `www.strava.com` & `api-v3.strava.com` (Strava), server token Strava (D-82). Tidak ada request lain. |
| SEC-NET-04 | Timeout koneksi/baca diset; respons di-parse dengan validasi (tidak mempercayai bentuk JSON begitu saja). |

### B.5 Autentikasi Strava (MASVS-AUTH, Fase 5)

| ID | Aturan |
|---|---|
| SEC-AUTH-01 | OAuth dibuka di **browser eksternal / Custom Tabs**, bukan WebView. |
| SEC-AUTH-02 | Gunakan parameter **`state` acak** dan validasi saat redirect (cegah CSRF/injeksi redirect). Gunakan PKCE bila didukung Strava (agent memverifikasi dokumentasi terbaru). |
| SEC-AUTH-03 | Redirect URI memakai skema/host khusus app; activity penerima hanya memproses redirect yang cocok persis dan menolak selainnya. |
| SEC-AUTH-04 | Client Secret Strava **hanya ada di server token** (Cloudflare Worker, sebagai *secret*), tidak di repo, source, APK, atau HP pengguna (D-82). App hanya memuat Client ID (publik) dan alamat server lewat konfigurasi build. Server tidak menyimpan dan tidak me-log token. |
| SEC-AUTH-05 | Scope minimal: hanya `activity:write` (tambahan scope butuh persetujuan). |
| SEC-AUTH-06 | Disconnect memanggil endpoint revoke lalu menghapus token lokal; token tidak pernah ditampilkan di UI/log. |

### B.5b SMS darurat & data kontak (fitur deteksi insiden)

| ID | Aturan |
|---|---|
| SEC-SMS-01 | Izin `SEND_SMS` hanya diminta saat pengguna mengaktifkan deteksi insiden; fitur nonaktif = izin tidak diminta. |
| SEC-SMS-02 | SMS **hanya** dikirim ke nomor yang didaftarkan pengguna di layar Keselamatan; tidak ada jalur kode lain yang dapat mengirim SMS (satu kelas `SmsSender`, dipanggil hanya oleh alur insiden & SMS uji). |
| SEC-SMS-03 | Batas keras: maks 3 SMS per kontak per insiden + retry gagal maks 5x; SMS uji dibatasi 1 per menit. Mencegah biaya pulsa tak terkendali akibat bug. |
| SEC-SMS-04 | Isi SMS minimal: nama pengendara, waktu, tautan lokasi, akurasi. Tanpa data kesehatan atau info lain. |
| SEC-SMS-05 | Kontak dipilih via `ACTION_PICK` (tanpa izin `READ_CONTACTS`). Nomor kontak = data pribadi pihak ketiga: disimpan lokal, tidak di-log, tidak masuk crash log, export, backup data share, maupun kartu share. |
| SEC-SMS-06 | Intent/komponen tidak ter-export yang bisa memicu pengiriman SMS dari luar app (SEC-PLT-01). |
| SEC-SMS-07 | Bila kelak didistribusikan lewat Google Play, kebijakan izin SMS ditinjau ulang sebelum rilis (ajukan pilihan ke pemilik). |

### B.5c Backup & restore

| ID | Aturan |
|---|---|
| SEC-BK-01 | Arsip backup tidak pernah berisi token/secret Strava atau kontak darurat. |
| SEC-BK-02 | Opsi enkripsi berbasis kata sandi memakai algoritma terautentikasi standar dan KDF kuat dengan salt acak dari library tepercaya; **tidak ada kripto buatan sendiri**. Kata sandi tidak disimpan. |
| SEC-BK-03 | Restore: validasi nama entri (anti zip-slip), batas ukuran/jumlah entri, checksum, versi skema; ekstrak ke folder sementara internal; tukar atomik. |
| SEC-BK-04 | File backup ditulis hanya ke lokasi yang dipilih pengguna (SAF/share sheet); salinan sementara di cache dihapus setelah selesai. |

### B.6 Build & rilis (MASVS-RESILIENCE ringan)

| ID | Aturan |
|---|---|
| SEC-BLD-01 | Release: `debuggable=false`, R8 aktif, log debug dihapus/dimatikan, StrictMode & LeakCanary tidak ikut. |
| SEC-BLD-02 | Keystore rilis & password **di luar repo** (`keystore.properties`/environment), masuk `../.gitignore`. Backup keystore dicatat oleh pemilik proyek (kehilangan keystore = tidak bisa update APK). |
| SEC-BLD-03 | Signing scheme v2+ (v3 bila tersedia). |
| SEC-BLD-04 | Tidak ada endpoint, flag, atau menu developer (fake GPS/BLE) yang aktif di build release (`BuildConfig.DEBUG`/source set `debug`). |

---

## C. Kapan pemeriksaan dilakukan

| Kapan | Pemeriksaan |
|---|---|
| **Setiap commit** | Secret scan (gitleaks); format & lint |
| **Setiap task** | Verifikasi standar (Lint Security sebagai error, detekt); **checklist review keamanan manual** (§D) untuk kode yang diubah; SUP-07/SUP-08 bila ada dependency baru |
| **Setiap akhir fase (Phase Gate)** | Scan CVE dependency (SUP-06); laporan lisensi; cek update dependency (SUP-10); review merged manifest (SUP-08); scan APK release dengan MobSF; review threat model (§E) |
| **Sebelum rilis/APK dibagikan** | Semua di atas + checklist SEC-BLD |

---

## D. Checklist review keamanan manual (dipakai di tahap ② & ③)

Agent meninjau **diff** task dan menjawab setiap poin (Ya / Tidak / N/A + catatan):

1. Apakah ada **input dari luar** baru (file, intent, BLE, jaringan)? → validasi panjang, rentang, ukuran, tipe (SEC-IN-*).
2. Apakah ada **komponen manifest** baru atau perubahan `exported`/intent-filter? → SEC-PLT-01.
3. Apakah ada **data sensitif** baru (lokasi, HR, token) yang disimpan/di-log/dikirim? → SEC-STO-*, ERR-05.
4. Apakah ada **file** yang ditulis/dibaca/dibagikan? → internal storage, nama aman, FileProvider (SEC-STO-01, SEC-IN-04, SEC-PLT-04).
5. Apakah ada **PendingIntent**? → `FLAG_IMMUTABLE` & eksplisit.
6. Apakah ada **request jaringan**? → HTTPS, timeout, domain terdokumentasi (SEC-NET-*).
7. Apakah ada **SQL** di luar `@Query` berparameter? → SEC-IN-05.
8. Apakah ada **rahasia/hardcoded credential/URL debug**? → harus tidak ada.
9. Apakah ada **izin** baru? → SEC-PLT-06, persetujuan.
10. Apakah ada **dependency** baru? → SUP-07, SUP-08, scan CVE.
11. Apakah error/exception bisa **membocorkan informasi** (stack trace ke UI, data pribadi di pesan)? → pesan generik di UI, detail di log debug saja.
12. Apakah fitur debug/simulasi bisa **aktif di release**? → SEC-BLD-04.
13. Apakah kode menyentuh **SMS, kontak darurat, atau kartu share**? → SEC-SMS-*, SC privasi (`12-FEATURE-SPECS.md` §11).

---

## E. Threat model ringkas (diperbarui tiap fase)

| Aset | Ancaman | Kontrol |
|---|---|---|
| Rekaman lokasi & rute rumah | App lain membaca file; export membocorkan alamat rumah | Internal storage, FileProvider terbatas, zona privasi (opsional), tidak ada external storage |
| Data HR (kesehatan) | Bocor lewat log/backup/crash log | Log disanitasi, aturan backup, tanpa SDK pihak ketiga |
| Token & secret Strava (Fase 5) | Token dicuri dari storage/log/backup; secret bocor | Token di berkas AES-256-GCM dengan kunci Android Keystore di `noBackupFilesDir` (tidak ikut backup/transfer), `toString` menyamarkan, tidak di-log, revoke + hapus lokal saat putus (D-79). Secret hanya di server token (D-82); bila bocor: ganti di strava.com dan perbarui secret Worker |
| Server token Strava (D-82) | Disalahgunakan pihak lain; token lewat server | Hanya 3 endpoint POST form, nilai dibatasi pola & ukuran sebelum diteruskan, tanpa penyimpanan/log, balasan hanya token + nama; menukar code/token tetap butuh code/refresh token milik pengguna sehingga server tidak memberi akses baru. Sisa risiko: siapa pun bisa memanggil endpoint (tanpa atestasi app) — dampak terbatas pada kuota Strava app |
| Integritas app | Library berbahaya/terkompromi | SUP-01..11 |
| Stabilitas | GPX/BLE berbahaya membuat crash atau hang | Validasi input, batas ukuran, XXE dimatikan, fuzz test |
| Komponen app | App lain memicu service/activity | `exported=false`, PendingIntent immutable, validasi intent |
| Alur OAuth (Fase 5) | Redirect palsu/CSRF; app lain mendaftarkan skema `ridecomp://` dan mencegat code | `state` acak 256-bit sekali pakai (dibandingkan constant-time), redirect `ridecomp://localhost/strava-auth` dicocokkan persis (skema, host, path, tanpa port/userinfo/fragment), nilai query dibatasi panjang; dibuka di app Strava/browser (bukan WebView). PKCE tidak didukung Strava: code curian tidak berguna tanpa Client Secret yang hanya ada di HP pengguna |
| Upload Strava & jaringan (Fase 5) | Data ride terkirim tanpa sengaja; balasan server berbahaya; MITM | Upload hanya atas ketukan pengguna atau saat upload otomatis diaktifkan (default mati); HTTPS saja, trust anchor sistem, tanpa redirect, timeout, balasan dibatasi 64 KB dan divalidasi; domain hanya `www.strava.com` & `api-v3.strava.com`; tidak ada data lain yang dikirim |
| Peta online (D-90) | Server peta melihat area yang ditampilkan (perkiraan lokasi); MITM; kuota data pengguna | Hanya `tiles.openfreemap.org` (tanpa akun/kunci/cookie), HTTPS dengan trust anchor sistem; hanya permintaan tile/TileJSON (tanpa lokasi eksplisit, tanpa telemetri MapLibre); hanya saat layar berpeta tampil dan ada internet; pengguna bisa memilih Hanya Wi‑Fi atau Mati di Setelan → Peta, dijelaskan di layar itu; cache tile di penyimpanan internal, tidak ikut backup cloud maupun transfer perangkat. Sisa risiko: penyedia gratis bisa berhenti tanpa pemberitahuan → app kembali ke garis/breadcrumb |
| Widget layar utama (D-96) | App lain memicu receiver/aksi widget; data ride terlihat di layar kunci/beranda | Receiver hanya menangani `APPWIDGET_UPDATE`; aksi MainActivity dari widget hanya membuka layar persiapan/ride (tidak memulai rekaman); PendingIntent eksplisit `FLAG_IMMUTABLE`; widget hanya menampilkan ringkasan (sesi, jarak minggu ini, jarak/waktu ride) — tanpa lokasi; pengguna memasang/menghapusnya sendiri |
| SMS darurat | Bug/penyalahgunaan mengirim SMS massal (biaya), nomor kontak bocor | SEC-SMS-01..07 |
| Sensor BLE (Fase 2) | Perangkat asing di sekitar diiklankan sebagai HR/CSC/CP; paket rusak/berlebihan | Hanya sensor yang dipasangkan pengguna yang disambung (alamat tersimpan); parser memvalidasi panjang & rentang (STB-09); operasi GATT bertimeout; data hanya dipakai untuk metrik (tidak ada perintah tulis ke sensor) |
| Deteksi insiden (Fase 2) | Alarm palsu mengirim SMS; insiden nyata terlewat | Hitung mundur 30 dtk + tombol batal (juga di notifikasi), ambang di `IncidentConfig` dikalibrasi lewat L17, penjelasan keterbatasan di layar Keselamatan |
| File backup | Arsip di Drive/WA dibaca orang lain; arsip berbahaya saat restore | Enkripsi kata sandi (SEC-BK-02), validasi arsip (SEC-BK-03) |
| File GPX & "Buka dengan" (Fase 3) | GPX berbahaya (XXE, billion laughs, file raksasa, nilai ekstrem) membuat crash/hang; app lain memicu impor | Parser milik sendiri tanpa DOCTYPE/entity eksternal, batas byte/titik/kedalaman, validasi nilai (D-43); intent-filter hanya `content://`, tanpa BROWSABLE (D-44). Sisa risiko: app lain yang mengirim intent eksplisit dapat menambah rute tanpa dialog (dampak terbatas: hanya menambah rute; bisa dihapus) |
| File peta PMTiles (Fase 3) | File rusak/berbahaya mengeksploitasi renderer native MapLibre (C++); file raksasa memenuhi storage; renderer menghubungi jaringan | Header divalidasi (magic, v3, MVT, bounds) sebelum disalin; batas 2 GB + sisa ruang 200 MB; hanya file pilihan pengguna (SAF) ke internal storage; izin INTERNET dihapus + `setConnected(false)`; MapLibre diperbarui tiap fase (SUP-10) (D-64) |
| Kartu share (Fase 4) | Gambar membocorkan lokasi rumah; file gambar dibaca app lain atau menumpuk | Potong 500 m awal/akhir default aktif, garis diproyeksikan ke 0..1 di `:core` (tidak ada koordinat/teks lokasi di gambar), opsi tanpa jejak; PNG di `cacheDir/share`, FileProvider hanya `cache-path share/` dengan grant baca sementara, dihapus saat app dibuka berikutnya (D-73). Zona privasi (SEC-STO-05) belum ada |
| Kartu peta & tombol app (T10.6) | Kartu peta membocorkan area tempat tinggal; daftar app terpasang | Kartu peta opsional (bukan default), potong 500 m awal/akhir tetap berlaku, catatan "memperlihatkan lokasi" saat dipilih; tile hanya bila Setelan → Peta mengizinkan online. `<queries>` hanya `com.instagram.android` & `com.whatsapp` (cek terpasang, tanpa izin `QUERY_ALL_PACKAGES`); kirim lewat `ACTION_SEND` + `setPackage`, FileProvider grant sementara seperti sebelumnya (D-100) |
| Cek pembaruan (T10.9) | Balasan palsu/berbahaya dari jaringan mengarahkan pengguna ke APK jahat; pelacakan | HTTPS saja (network security config), tanpa redirect, balasan dibatasi 256 KB, tag harus versi polos (`AppVersion.parse`), halaman harus diawali `https://github.com/wahyurama-creator/Ridecomp/releases/`; app hanya membuka halaman rilis di browser (tidak mengunduh/memasang APK sendiri, Android tetap memverifikasi tanda tangan saat update); tanpa token/akun, tanpa data pengguna; bisa dimatikan; paling sering 1×/hari saat app dibuka, tanpa kerja background (D-103) |
| Rekor, segmen & beban latihan (Fase 4) | Data kesehatan (HR, beban) & titik segmen (bisa dekat rumah) bocor; perhitungan berat membuat app lambat/boros | Semua di DB internal (ikut backup, bisa dienkripsi kata sandi); tidak dikirim ke mana pun, tidak di-log; analisis hanya saat app terbuka (BAT-05), track dibaca bertahap dengan batas 24 jam sampel (MEM-01); label "estimasi, bukan nilai medis" |
| Cache tile peta (T11.11, D-128) | Cache membocorkan area yang pernah dilihat; cache memenuhi penyimpanan; peta offline memicu jaringan | Cache MapLibre di `filesDir` (internal, tidak ikut backup cloud), batas 50/150/300 MB dengan LRU, "Hapus cache peta" dengan konfirmasi; offline/"Mati" memakai `setConnected(false)` sehingga tile hanya dibaca dari cache (diverifikasi logcat tanpa HTTP); socket MapLibre ber-tag lewat client OkHttp sendiri (D-132) |
| Paket peta provinsi (T11.12–T11.14, D-129…D-131) | Indeks/berkas palsu atau berbahaya (URL asing, file raksasa, file rusak untuk renderer native); unduhan memenuhi penyimpanan atau menguras kuota/baterai; lokasi bocor ke server | Indeks hanya dari APK, divalidasi di `:core` (awalan URL rilis proyek, nama `[a-z0-9-].pmtiles`, ukuran ≤ 2 GB, SHA-256 64 heksa, skema dikenal); HTTPS saja, URL akhir setelah redirect dicek, `Content-Length` harus sama dengan indeks, penulisan dibatasi ukuran; **SHA-256 lalu header PMTiles** dicek sebelum file dipakai, gagal checksum = dihapus; batas total 500 MB/1 GB/2 GB + sisa ruang ≥ 200 MB + peringatan < 500 MB; file sementara usang dibersihkan; Wi‑Fi saja secara default, hanya saat aplikasi terbuka, tidak saat ride; lokasi hanya dicocokkan ke bbox di HP (server hanya melihat file yang diunduh). Rilis peta dipublikasikan pemilik lewat skrip dengan konfirmasi |
| Pindai luas & Periksa (T12.2, D-134) | Perangkat asing di sekitar terdaftar/tersambung; pindai tanpa filter boros baterai; nama perangkat berbahaya | Default mati, bertimeout 15 dtk, saringan nama/RSSI; tersambung hanya atas ketukan Periksa, tanpa bonding, `close()` di `finally`; nama hanya ditampilkan |
| Jam GloryFit eksperimental (T12.5b, D-135/D-136) | Paket vendor rusak/palsu membuat crash atau HR palsu; perintah ke jam menguras baterai jam; protokol berubah setelah update | Parser memeriksa prefix, panjang, rentang 30–230 bpm; perintah hanya selama ride, STOP saat selesai (NonCancellable, ≤ 2 dtk); label eksperimental; protokol clean-room dari perangkat sendiri, rekaman mentah tidak disimpan di repo |
| Rute layar debug untuk screenshot pembanding (T13.2, D-142) | App lain di HP yang memasang build **debug** membuka layar mana pun lewat extra `com.ridecomp.debug.SCREEN` ke `MainActivity` (exported) | Hanya di source set `debug` (release: `DevTools.screenRoute` = null, SEC-BLD-04); nilai divalidasi pola `[a-z_]{1,32}(/[A-Za-z0-9_]{1,32})?`; rute tak dikenal hanya di-log; hanya navigasi, tidak ada aksi/data yang diubah; build debug tidak dibagikan ke pengguna |
| Pilihan tema (T11.9, D-126) | Salinan cepat tema berbeda dari setelan | Tabel `settings` tetap sumber; root menyalin setiap nilai ke SharedPreferences privat (isi hanya nama tema) |
