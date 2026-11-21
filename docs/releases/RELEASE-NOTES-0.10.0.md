# RideComp 0.10.0 (beta)

**Tanggal:** 2026-11-21 · Android 8.0+ · pasang langsung dari file APK

## Cara memperbarui / How to update
Ketuk **Unduh** di kartu pembaruan di Beranda, atau buka Setelan → Pembaruan aplikasi. Pasang APK baru **di atas versi lama**, jangan uninstall dulu. Ride, sepeda, dan pengaturan tetap ada.
Tap **Download** on the update card on Home, then install the APK **over the old version**. Don't uninstall first; your data stays.

Tidak yakin file mana? Pilih **arm64-v8a**. Untuk HP lama 32-bit, pilih **armeabi-v7a**.

## Perubahan sejak 0.9.3 / Changes since 0.9.3

### Analisis ride / Ride analysis
- **Halaman detail ride baru**: bagian kecepatan, elevasi, detak jantung, dan power dengan grafik yang lebih rapi (angka sumbu bulat, penanda jarak, rata-rata saat bergerak).
- **Zona detak jantung dan zona power Z1–Z7** (dari power meter atau power estimasi), plus estimasi FTP.
- **Kartu Hasil**: 3 usaha terbaik (kecepatan, power) dan segmen yang dilewati. Potongan usaha terbaik bisa disimpan jadi segmen.
- **Pembuat segmen baru** dalam 3 langkah, dengan peta, pilihan cepat, dan rentang ketinggian.

### Peta offline / Offline maps
- **Peta per provinsi** ditawarkan dari area ride terakhir. Unduhan bisa dilanjutkan dan diperiksa keutuhannya; paket pertama: DI Yogyakarta.
- Peta yang pernah dilihat online tersimpan untuk dipakai offline, dengan batas ukuran, tombol hapus, dan peringatan ruang penyimpanan.

### Tampilan / Display
- **Tema gelap, terang, atau ikuti HP** di Setelan → Tampilan. Kontras tema terang diperbaiki.
- **HP berlayar kecil (320 dp)** kini lebih nyaman: tombol SELESAI di layar jeda dan tombol Mulai di layar sambutan tidak lagi terpotong, layar kosong bisa di-scroll, angka ringkasan bulan tidak menempel, dan kolom kontak darurat bertumpuk. **Tampilan HP 360 dp ke atas tidak berubah.**

### Sensor & jam tangan / Sensors & watches
- **"Tampilkan semua perangkat Bluetooth"** untuk menemukan jam tangan yang tidak mengiklankan layanan detak jantung, dengan tombol Periksa sebelum memasangkan.
- **Jam tangan ditandai** dengan ikon dan label, plus panduan baru **Sumber detak jantung** di Bantuan (sensor HR, jam dengan broadcast HR per merek, dan jam tanpa broadcast).
- **Aolon GTR 3 (aplikasi GloryFit), eksperimental**: detak jantung bisa dipakai saat ride. Tutup GloryFit sebelum ride.

### Perbaikan / Fixes
- Sensor tersimpan yang rusak tidak lagi membuat semua sensor lain berhenti ("Sensor tidak tersedia, mencoba lagi").
- Peta tampil benar saat app baru dibuka; pemuatan peta tidak lagi membuat layar tersendat.
- Batas cache peta dalam MB desimal; petunjuk data seluler terbaca benar di kedua posisi saklar.
- Perbaikan stabilitas dari temuan StrictMode.

## Catatan / Notes
- Tidak ada perubahan struktur data, jadi pembaruan dari 0.9.x aman.
- Fitur jam Aolon GTR 3 masih eksperimental: belum diuji di ride sungguhan.
