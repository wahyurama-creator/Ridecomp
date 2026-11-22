# RideComp 0.10.1 (beta)

**Tanggal:** 2026-11-22 · Android 8.0+ · pasang langsung dari file APK

## Cara memperbarui / How to update
Ketuk **Unduh** di kartu pembaruan di Beranda, atau buka Setelan → Pembaruan aplikasi. Pasang APK baru **di atas versi lama**, jangan uninstall dulu. Ride, sepeda, dan pengaturan tetap ada.
Tap **Download** on the update card on Home, then install the APK **over the old version**. Don't uninstall first; your data stays.

Tidak yakin file mana? Pilih **arm64-v8a**. Untuk HP lama 32-bit, pilih **armeabi-v7a**.

## Perbaikan / Fixes
- **Tanjakan terlalu besar di HP tanpa barometer.** Di HP yang tidak punya sensor tekanan udara, tinggi diambil dari GPS yang bisa melonjak puluhan sampai ratusan meter. Akibatnya ride datar bisa tercatat ratusan meter tanjakan. Tinggi GPS kini disaring jauh lebih ketat, dan di detail ride angkanya ditandai **"Perkiraan dari GPS"**.
- **HP dengan barometer:** bacaan barometer yang telat tidak lagi diganti tinggi GPS (bisa menambah tanjakan palsu).

## Catatan / Notes
- Berlaku untuk ride baru. Ride yang sudah tersimpan tidak berubah.
- Di HP tanpa barometer, tanjakan masih bisa lebih besar dari aplikasi lain yang memakai barometer atau peta ketinggian. Koreksi dengan peta ketinggian offline sedang direncanakan.
- Tidak ada perubahan data, jadi pembaruan dari 0.10.0 aman.
