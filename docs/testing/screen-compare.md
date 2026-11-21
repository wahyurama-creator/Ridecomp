# Screenshot Pembanding (T13.2)

Alat untuk membuktikan bahwa perubahan Fase 13 **tidak mengubah tampilan di 412, 390, dan 360 dp** (R2, `planning/16-PHASE13-PLAN.md` §7), dan untuk memeriksa layar di 320 dp. Tanpa dependency atau alat baru: `adb`, `bash`, dan Python 3 (pustaka standar).

## Syarat

- **Build debug** (`com.ridecomp.app.debug`, "RideComp debug"). Sejak D-144 build debug terpasang **di samping** build release, jadi HP dengan data ride asli bisa dipakai tanpa uninstall; data kedua app terpisah. Build debug tidak menerima tautan balik login Strava (hanya release). HP biasa cukup: lebar 320 dp disimulasikan dengan `wm density`.
- Onboarding sudah selesai dan data uji tetap (sepeda, ride contoh dari menu Developer) agar layar sama antar pengambilan.
- Bahasa dan tema diatur di app sebelum mulai; tuliskan di label (mis. `base-id-dark`).

## Langkah

```bash
# 1. Baseline: build dari commit SEBELUM perubahan (untuk Fase 13: 49f9141 = merge T13.2, sebelum T13.3)
git checkout <commit-baseline> && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # sesuai ABI HP (armeabi-v7a untuk HP 32-bit)
scripts/screens/capture.sh base-id-dark            # 412, 390, 360 dp

# 2. Hasil: build task yang diuji
git checkout <branch-task> && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
scripts/screens/capture.sh t13x-id-dark

# 3. Bandingkan: harus "All screens identical"
python3 -I scripts/screens/compare.py build/screens/base-id-dark build/screens/t13x-id-dark

# 4. Layar kecil: ambil 320 dp dan cek target sentuh < 56 dp (UI-07)
scripts/screens/capture.sh t13x-id-dark-320 320
python3 -I scripts/screens/targets.py build/screens/t13x-id-dark-320/320dp
```

Hasil disimpan di `build/screens/<label>/<lebar>dp/` (PNG + dump `uiautomator`, tidak di-commit). `capture.sh` mengembalikan density dan mematikan demo mode saat selesai, juga bila dihentikan dengan Ctrl-C.

## Cara kerja

| Bagian | Isi |
|---|---|
| `screens.txt` | Daftar `<nama> <rute>`. Layar yang butuh id (detail ride, detail rute, segmen) ditambahkan di file lokal lewat `SCREENS_FILE=...` |
| Membuka layar | `am start ... --es com.ridecomp.debug.SCREEN <rute>`; hanya build debug yang membacanya (`DebugScreenRoute`, SEC-BLD-04). Layar dibuka di atas layar awal, jadi tumpukan navigasi selalu sama |
| Lebar | `wm density = lebar_px × 160 / lebar_dp` (dibulatkan); lebar asli diambil dari `wm size` |
| Status bar | Demo mode: jam 12:00, baterai 100%, Wi-Fi penuh, tanpa notifikasi |
| Stabil | Screenshot diulang tiap detik sampai dua berturut-turut sama (maks. 5 dtk). Layar yang terus berubah (animasi, data langsung seperti cincin GPS) ditandai **UNSTABLE** dan dibandingkan dengan mata |
| `compare.py` | Isi file sama = sama; selain itu PNG didekode dan piksel RGB dibandingkan. Mencetak jumlah piksel beda + kotak batasnya; exit 1 bila ada beda atau file hilang |
| `targets.py` | Elemen `clickable`/`checkable` < 56 dp. `uiautomator` memberi batas yang **terlihat**: elemen yang terpotong tepi layar diberi tanda `edge` (lihat audit T13.1 #15) |

## Batasan

- Layar ride, jeda, dan ringkasan butuh ride berjalan (ride simulasi) dan berisi data yang berubah tiap detik: diperiksa visual, tidak dengan `compare.py`.
- `wm density` ≠ HP kecil asli (font/inset bisa beda); uji akhir tetap di HP kecil asli bila ada.
- Kursor teks yang berkedip di kolom fokus membuat layar UNSTABLE.
