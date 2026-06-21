# Notif Grabber

Android app sederhana yang menangkap semua notifikasi yang masuk ke HP dan
mengirimkannya sebagai JSON ke webhook yang kamu tentukan.

## Cara pakai

1. Buka folder ini di **Android Studio** (File > Open), biarkan Gradle sync
   (butuh koneksi internet untuk download dependency dari Google
   Maven/Maven Central).
2. Jalankan/install ke HP target.
3. Buka app, isi **URL webhook**, tekan **Simpan**.
4. Tekan **Buka Pengaturan Notifikasi**, lalu aktifkan toggle untuk "Notif
   Grabber" di daftar (ini wajib lewat Settings manual — Android tidak
   mengizinkan izin ini diberikan diam-diam tanpa interaksi pengguna).
5. Setelah aktif, setiap notifikasi baru di HP itu akan di-POST ke webhook.

## Format payload

```json
{
  "event": "posted",
  "package": "com.whatsapp",
  "app_name": "WhatsApp",
  "title": "Budi",
  "text": "Halo, lagi dimana?",
  "sub_text": "",
  "post_time": 1750000000000,
  "device_time": 1750000000123
}
```

## Catatan reliabilitas

- Beberapa HP (Xiaomi/MIUI, Oppo/ColorOS, Vivo, dll.) suka membatasi proses
  background. Kalau notifikasi berhenti terkirim setelah beberapa lama,
  cek pengaturan **battery optimization** / **autostart** untuk app ini dan
  whitelist dari situ.
- `BootReceiver` mencoba minta rebind otomatis setelah restart HP.
- Pengiriman webhook saat ini "fire and forget" (gagal kirim = dilewati
  begitu saja). Kalau butuh jaminan terkirim (retry/antrian lokal), itu
  bisa ditambahkan — kasih tahu saja.

## Build tanpa Android Studio (lewat GitHub Actions)

1. Buat repo baru di GitHub (boleh private), lalu push seluruh isi folder
   ini ke repo tersebut.
   ```
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/USERNAME/NotifGrabber.git
   git push -u origin main
   ```
2. Buka tab **Actions** di repo tersebut — workflow "Build APK" akan
   otomatis jalan (atau klik **Run workflow** untuk trigger manual).
3. Setelah selesai (centang hijau), klik build run-nya, scroll ke bagian
   **Artifacts**, download `notif-grabber-debug-apk`.
4. Extract zip-nya, dapat file `app-debug.apk`. Transfer ke HP (lewat
   kabel, Drive, dsb), lalu install (aktifkan "Install dari sumber tidak
   dikenal" kalau diminta).

Ini APK debug (ditandatangani otomatis dengan debug key) — cukup untuk
pemakaian pribadi, tidak perlu upload ke Play Store.

## Build via command line (kalau punya JDK + Android SDK terinstall)

```
./gradlew assembleDebug
```
APK hasilnya ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Build APK lewat Android Studio (kalau ada)

**Build > Generate Signed Bundle / APK**, pilih APK, ikuti wizard untuk
membuat/pakai keystore.
