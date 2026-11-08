# MiTool

Flash dan unlock HP Xiaomi, langsung dari Android.

Flash HP Xiaomi yang masuk mode EDL (9008) langsung dari HP lain lewat kabel OTG. Tidak perlu laptop Windows lagi.

## Bisa apa

- Deteksi HP EDL 9008 via USB OTG
- Kirim firehose loader via Sahara, flash firmware via Firehose
- Mode fastboot: flash ROM fastboot langsung dari HP, tanpa binary fastboot
- Baca firmware langsung dari kartu microSD, tanpa disalin ke internal
- Format firmware: rawprogram / patch XML, sparse image

## Fastboot

Colok HP mode fastboot via OTG, pilih folder ROM hasil ekstrak (berisi `images/` dan script `flash_all`), lalu pilih scriptnya:

- **Clean all** — flash semua, data dihapus
- **Save userdata** — flash semua, data tetap
- **Clean all and lock** — flash semua, data dihapus, bootloader dikunci

Protokol fastboot jalan langsung lewat USB (tanpa binary). Image sparse yang besar dibagi otomatis mengikuti `max-download-size` device. ROM `.tgz` harus diekstrak dulu sebelum dipilih. Flash fastboot tidak butuh signer.

## Yang belum bisa

- HP EDL yang minta autentikasi belum bisa di-flash, signer-nya belum ada. Riset DLL membuktikan signing-nya hidup di `account_auth.exe`, bukan di `SLA_Challenge.dll` — jadi tidak ada yang bisa diimplementasikan dari DLL itu. Lihat `auth/SlaSigner.kt`.

## Build

```sh
./gradlew assembleDebug
```

Butuh Android SDK (compileSdk 34) dan JDK 17. APK debug ada di `app/build/outputs/apk/debug/`.

## Catatan

- Jangan cabut kabel OTG dan kartu microSD selama flash berjalan. Flash yang terputus bisa bikin HP target brick. Selama flash, app jalan sebagai foreground service — jangan di-swipe dari recent apps.
- Kartu microSD sebaiknya exFAT kalau ada file image di atas 4 GB.
- Port dari [MiFlash_CSharp_Native_NoAuth](https://github.com/HadiKhoirudin/MiFlash_CSharp_Native_NoAuth) (MIT). Versi ini hanya jalur autentikasi resmi — tanpa blob NoAuth, tanpa patched loader.
