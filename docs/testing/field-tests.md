# Catatan Uji Lapangan (07-TEST-PLAN §4, §6)

Isi satu baris per uji. Uji di HP low-end (RAM 2–3 GB) dari merek yang agresif mematikan app (Xiaomi/Redmi, Oppo/Realme, Vivo, Infinix), dengan **build release**.

Cara memasang build untuk uji:
- Debug (LeakCanary, StrictMode, menu Developer → GPS simulasi): `./gradlew installDebug`.
- Release (uji baterai & performa): `./gradlew assembleRelease`, lalu pasang APK sesuai ABI HP dari `app/build/outputs/apk/release/`. Butuh `keystore.properties` untuk APK bertanda tangan; lihat `app/build.gradle.kts`.

Perintah verifikasi resource setelah ride disimpan (BAT-01, L11):
```
adb shell dumpsys location | grep -i ridecomp           # tidak ada request lokasi
adb shell dumpsys power | grep -i "RideComp:recording"  # tidak ada wakelock
adb shell dumpsys activity services com.ridecomp.app    # tidak ada RecordingService
adb shell dumpsys sensorservice | grep -i ridecomp      # tidak ada listener barometer
adb shell dumpsys meminfo com.ridecomp.app              # heap (MEM-08: awal/tengah/akhir)
```

| Tanggal | Skenario (L#/R#) | HP & Android | Build | Durasi | Mode layar | Sensor | Baterai awal→akhir | Hasil | Temuan / bug |
|---|---|---|---|---|---|---|---|---|---|
| | L1 Ride 1 jam, redup otomatis | | release | | Redup otomatis | – | | | |
| | L3 Layar mati 20 mnt | | release | | Mati | – | | | |
| | L4 Force stop di tengah ride | | release | | | – | | | |
| | L5 Lampu merah / berhenti 2 mnt | | release | | | – | | | |
| | L9 Upload FIT manual ke Strava | | release | | | – | | | |
| | L11 Idle drain 8 jam setelah ride | | release | 8 jam | | – | | | |
| | L12 Cabut izin / matikan GPS di tengah ride | | debug | | | – | | | |
| | L14 5x lampu merah + istirahat 20 mnt | | release | | | – | | | |
| | L15 Ride ≥ 1 jam, pengingat minum 20 mnt | | release | | | – | | | |
| | L16 Baterai ~22 % sampai < 10 % | | release | | | – | | | |
| | R1 Simulasi ride 3 jam (GPS simulasi) | | debug | 3 jam | | – | | | |
| | R2 Buka-tutup layar ride/riwayat 30x | | debug | | | – | | | |
