# Kebijakan Privasi RideComp / RideComp Privacy Policy

**Berlaku sejak / Effective:** 18 November 2026 · versi aplikasi 0.9.1

## Bahasa Indonesia

RideComp adalah cycling computer yang bekerja di HP Anda. Kami tidak punya akun pengguna, server data, iklan, atau analitik.

**Data yang disimpan di HP Anda saja:** ride (lokasi GPS per detik, kecepatan, jarak, ketinggian), data sensor (detak jantung, cadence, power), profil (berat badan, tahun lahir, HR max, FTP, jenis kelamin bila diisi), sepeda & komponennya, rute GPX yang Anda impor, rencana latihan, kontak darurat. Semua ada di penyimpanan internal app; tidak ikut backup cloud Google. Anda bisa membuat backup terenkripsi sendiri dari Setelan → Backup.

**Data yang keluar dari HP — hanya bila Anda memakai fiturnya:**
- **Peta online (OpenFreeMap):** saat peta tampil dan ada internet, HP meminta potongan peta dari `tiles.openfreemap.org`; server itu melihat area peta yang diminta (perkiraan lokasi) dan alamat IP Anda. Bisa diatur ke *Hanya Wi‑Fi* atau *Mati* di Setelan → Peta. OpenFreeMap tidak memakai akun maupun cookie.
- **Strava (opsional):** bila Anda menghubungkan Strava, ride yang Anda pilih (atau ride baru bila upload otomatis aktif) dikirim langsung dari HP ke Strava sebagai file FIT. Penukaran token login melewati server token RideComp (Cloudflare Worker) yang tidak menyimpan atau mencatat apa pun. Token disimpan terenkripsi di HP dan dihapus saat Anda memutuskan Strava.
- **SMS darurat (opsional):** bila fitur Keselamatan aktif dan insiden terdeteksi (dengan hitung mundur yang bisa dibatalkan) atau Anda menekan SOS, HP mengirim SMS berisi lokasi terakhir ke kontak darurat yang Anda pilih, melalui operator seluler Anda.
- **Cek pembaruan (sejak 0.9.1):** saat aplikasi dibuka, paling sering sekali sehari, HP bertanya ke `api.github.com` apakah ada versi RideComp baru. Yang terlihat oleh GitHub hanya alamat IP Anda; tidak ada data ride atau profil yang dikirim. Bisa dimatikan di Setelan → Pembaruan aplikasi.
- **Berbagi:** file FIT/GPX dan kartu gambar hanya dibagikan ke app yang Anda pilih sendiri. Kartu peta memperlihatkan lokasi ride; 500 m awal & akhir disembunyikan secara default.

**Izin:** lokasi (merekam ride), Bluetooth (sensor & smart trainer), notifikasi (ride berjalan), SMS (hanya fitur darurat), internet & status jaringan (peta online, Strava, cek pembaruan), pengecualian optimasi baterai (agar HP tidak menghentikan rekaman).

**Menghapus data:** hapus ride di Riwayat, atau hapus data/aplikasi dari setelan Android — semuanya hilang dari HP.

Pertanyaan: hubungi pengembang melalui halaman rilis aplikasi.

## English

RideComp is a cycling computer that works on your phone. There are no user accounts, data servers, ads or analytics.

**Stored only on your phone:** rides (GPS position every second, speed, distance, altitude), sensor data (heart rate, cadence, power), your profile (weight, birth year, HR max, FTP, sex if entered), bikes and their parts, imported GPX routes, training plans, emergency contacts. Everything is in the app's internal storage and excluded from Google cloud backup. You can make your own encrypted backup in Settings → Backup.

**What leaves the phone — only when you use the feature:**
- **Online map (OpenFreeMap):** while a map is shown and there is internet, the phone requests map tiles from `tiles.openfreemap.org`; that server sees the map area requested (an approximate location) and your IP address. Set it to *Wi‑Fi only* or *Off* in Settings → Maps. OpenFreeMap uses no accounts or cookies.
- **Strava (optional):** when you connect Strava, the rides you choose (or new rides when auto-upload is on) are sent directly from the phone to Strava as FIT files. Login tokens are exchanged through RideComp's token server (a Cloudflare Worker) that stores and logs nothing. Tokens are kept encrypted on the phone and deleted when you disconnect.
- **Emergency SMS (optional):** when Safety is on and an incident is detected (with a cancellable countdown) or you press SOS, the phone sends an SMS with your last location to the contacts you chose, through your mobile carrier.
- **Update check (since 0.9.1):** when the app is opened, at most once a day, the phone asks `api.github.com` whether a newer RideComp exists. GitHub only sees your IP address; no ride or profile data is sent. Turn it off in Settings → App updates.
- **Sharing:** FIT/GPX files and image cards go only to the app you pick. Map cards show where you rode; the first and last 500 m are hidden by default.

**Permissions:** location (recording rides), Bluetooth (sensors and smart trainers), notifications (ride in progress), SMS (emergency feature only), internet and network state (online map, Strava, update check), battery optimisation exemption (so the phone does not stop a recording).

**Deleting data:** delete rides in History, or clear the app's data / uninstall it in Android settings — everything is removed from the phone.

Questions: contact the developer through the app's release page.
