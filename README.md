# SchoolMate

Aplikasi Android manajemen waktu untuk pelajar SMA: jadwal pelajaran, tugas dan PR, pengingat ujian, dan mode fokus. Semua data tersimpan di perangkat (offline-first), tanpa akun, Firebase, atau API berbayar.

## Status

- Tahap 1 – fondasi proyek: build stabil, arsitektur MVVM, Room, DataStore, Navigation Compose, CI yang menghasilkan APK.
- Tahap 2 – data inti: jadwal mingguan, tugas/PR, ujian, sesi belajar (Room v2 dengan migrasi teruji), ringkasan di Beranda.
- Tahap 3 – UI dan fitur inti: semua layar terhubung ke Room, tambah/edit/hapus untuk jadwal, tugas, dan ujian.
- Tahap 4 – notifikasi dan pengingat lokal untuk tugas, ujian, dan pelajaran (versi 0.4.0).

| Fitur | Status |
|---|---|
| Navigasi bawah: Beranda, Jadwal, Tugas, Ujian, Pengaturan | Selesai (tahap 3) |
| Beranda: tanggal, pelajaran hari ini, tugas terlambat & terdekat, ujian mendatang, waktu belajar minggu ini | Selesai (tahap 3) |
| Jadwal mingguan Senin–Minggu + form dengan deteksi bentrok langsung | Selesai (tahap 3) |
| Tugas: urut deadline, filter Aktif/Terlambat/Selesai/Semua, centang cepat + urungkan, detail & status | Selesai (tahap 3) |
| Ujian: mendatang/sudah lewat, form nama, mapel, tanggal, jam, durasi, catatan | Selesai (tahap 3) |
| Catatan belajar (input manual per minggu) dan kelola mapel, lewat Pengaturan | Selesai (tahap 3) |
| Nama panggilan, tema terang/gelap/sistem, warna dinamis (DataStore) | Selesai |
| Pengingat lokal tugas, ujian, dan pelajaran + pengaturannya | Selesai (tahap 4) |
| Pomodoro (tab Fokus kembali di tahap itu) | Tahap berikutnya |

## Teknologi

| Komponen | Versi |
|---|---|
| Gradle (wrapper) | 9.6.1 |
| Android Gradle Plugin | 9.4.1 (Kotlin bawaan AGP) |
| Kotlin / Compose compiler | 2.4.21 |
| KSP | 2.3.12 |
| Compose BOM / Material 3 | 2026.09.00 |
| Room | 2.8.5 |
| DataStore Preferences | 1.2.1 |
| Navigation Compose (type-safe) | 2.10.2 |
| JDK | 17 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |

Semua versi dikelola di [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Struktur

```
app/src/main/java/id/schoolmate/app/
├── SchoolMateApplication.kt   # memegang AppContainer
├── MainActivity.kt            # splash screen, tema, entry Compose
├── di/                        # dependency injection manual (AppContainer)
├── data/
│   ├── local/                 # Room: database, entity, dao
│   ├── preferences/           # DataStore: preferensi pengguna
│   └── repository/            # sumber data tunggal untuk ViewModel
├── domain/model/              # model domain murni
├── notifications/             # perencana pengingat, sinkronisasi alarm, notifikasi, receiver
├── ui/
│   ├── navigation/            # rute type-safe + destinasi bawah
│   ├── screens/<fitur>/       # Screen (Compose) + ViewModel (StateFlow)
│   ├── components/            # komponen UI bersama
│   └── theme/                 # Material 3 color, type, theme
└── util/                      # logika murni yang mudah dites
app/schemas/                   # skema Room yang diekspor (untuk migrasi)
```

## Model data (Room v2)

| Tabel | Isi | Relasi ke `subjects` |
|---|---|---|
| `subjects` | Mata pelajaran (v1, tidak berubah) | – |
| `schedule_entries` | Slot pelajaran mingguan: hari, jam mulai–selesai, ruang | `CASCADE` (ikut terhapus) |
| `tasks` | Tugas/PR: deadline (tanggal + jam opsional), prioritas, status | `SET NULL` |
| `exams` | Ujian: tanggal, jam mulai, durasi, lokasi | `SET NULL` |
| `study_sessions` | Sesi belajar: waktu mulai, tanggal lokal, durasi menit | `SET NULL` (juga ke `tasks`) |

Format penyimpanan yang konsisten (lihat `data/local/converter/Converters.kt`):

| Tipe Kotlin | Disimpan sebagai |
|---|---|
| `LocalDate` | epoch day (INTEGER) |
| `LocalTime` | menit dalam sehari 0–1439 (INTEGER) |
| `Instant` | epoch milidetik (INTEGER) |
| `DayOfWeek` | ISO 1 (Senin) – 7 (Minggu) |
| `Priority` | level 1 (LOW) – 3 (HIGH) |
| `TaskStatus` | nama enum: `TODO`, `IN_PROGRESS`, `DONE` |

Aturan validasi ada di `domain/validation/Validation.kt` dan dipakai oleh semua repository (judul 1–100 karakter, jadwal tidak boleh bentrok, sesi belajar 1–720 menit, dst.). Kegagalan dikembalikan sebagai `Result.failure(ValidationException)`, bukan exception SQL mentah.

Setiap kenaikan versi database wajib punya migrasi eksplisit di `data/local/migration/` dan skema yang diekspor di `app/schemas/`; `MigrationTest` memverifikasi datanya tetap utuh.

Alur data: `Room/DataStore → Repository (Flow) → ViewModel (StateFlow) → Compose UI`.

## Notifikasi dan pengingat

### Cara mengaktifkan

1. Buka **Pengaturan → Notifikasi**, nyalakan **Aktifkan pengingat** (bawaan: mati, supaya tidak ada notifikasi tanpa persetujuan).
2. Di Android 13 ke atas, dialog izin notifikasi muncul saat itu juga (bukan saat aplikasi dibuka). Kalau ditolak, tombol **Izinkan** bisa memunculkan dialog sekali lagi; kalau Android sudah memblokirnya, tombol **Buka pengaturan** membuka halaman notifikasi aplikasi di pengaturan Android.
3. Atur per jenis: pengingat tugas (1 jam/3 jam/1 hari/2 hari sebelum deadline), ujian (1 jam/1/2/3 hari sebelum mulai), pelajaran (5/10/15/30 menit sebelum mulai). Mengubah pengaturan ini tidak menyentuh data tugas, jadwal, ujian, atau mapel.

### Izin yang dipakai

| Izin | Alasan |
|---|---|
| `POST_NOTIFICATIONS` | Wajib di Android 13+ untuk menampilkan notifikasi. Diminta hanya saat pengguna menyalakan pengingat. |
| `RECEIVE_BOOT_COMPLETED` | Memasang ulang pengingat setelah HP dinyalakan ulang (Android menghapus semua alarm saat reboot). |

Aplikasi **tidak** meminta `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`: pengingat sekolah tidak butuh ketepatan per detik, dan izin itu dibatasi Google Play untuk aplikasi jam/kalender.

### Cara kerjanya

- Semuanya lokal: tanpa server, Firebase, akun, atau internet.
- `ReminderPlanner` (logika murni, dites penuh) menghitung waktu tiap pengingat dari data Room dan preferensi DataStore:
  - Tugas: deadline − waktu pengingat. Tugas tanpa jam dianggap berakhir pukul 23.59. Tugas selesai atau yang deadline/waktu pengingatnya sudah lewat tidak diingatkan.
  - Ujian: waktu mulai − waktu pengingat (ujian tanpa jam dianggap mulai 07.00). Ujian yang sudah lewat tidak diingatkan.
  - Pelajaran: kemunculan berikutnya dari jadwal mingguan (minggu ini atau minggu depan) dalam zona waktu perangkat; setelah berbunyi, minggu berikutnya dijadwalkan otomatis.
  - Pengingat tugas/ujian yang jatuh pukul 21.00–06.00 dimajukan ke 19.00 malam sebelumnya (kalau jam itu belum lewat).
- `ReminderSync` membandingkan rencana dengan alarm yang sudah terpasang: yang baru/berubah dipasang ulang, yang hilang (tugas selesai/dihapus, ujian dihapus, jadwal dihapus, jenis dimatikan) dibatalkan, dan notifikasi yang sudah tampil untuk tugas selesai/terhapus ikut ditutup. Satu entitas = satu alarm dan satu ID notifikasi tetap (`jenis` + `id`), jadi tidak ada duplikat. Riwayat notifikasi yang sudah tampil disimpan agar alarm ganda tidak menampilkan notifikasi dua kali.
- Sinkronisasi dipicu oleh perubahan data saat aplikasi terbuka, saat alarm berbunyi, dan oleh event sistem: reboot, ubah jam, ubah zona waktu, dan update aplikasi. Tidak ada polling atau service di latar belakang.
- Alarm memakai `AlarmManager.setAndAllowWhileIdle` (tidak eksak, tetap jalan saat Doze). WorkManager tidak dipakai karena tidak dirancang untuk waktu tertentu.
- Saat alarm berbunyi, data dicek ulang ke database dulu: kalau tugas sudah selesai/diubah/dihapus atau acaranya sudah lewat, tidak ada notifikasi.
- Menyentuh notifikasi membuka detail tugas/ujian yang bersangkutan (atau tab Jadwal untuk pelajaran); tombol kembali menuju Beranda.
- Maksimal 100 alarm terpasang sekaligus; sisanya dipasang otomatis setelah alarm terakhir lewat.
- Tiga channel notifikasi: **Pengingat tugas**, **Pengingat ujian**, **Pengingat pelajaran** – masing-masing bisa diatur di pengaturan Android.

### Keterbatasan Android

- Waktu pengingat tidak dijamin tepat ke menit: Android dapat menunda alarm non-eksak beberapa menit (lebih lama saat Doze/hemat baterai).
- Kalau aplikasi di-**Force stop**, Android membatalkan semua alarmnya sampai aplikasi dibuka lagi (alarm dipasang ulang otomatis saat dibuka).
- Beberapa merek HP (mode hemat baterai agresif) dapat menahan atau membuang alarm aplikasi latar belakang; izinkan SchoolMate berjalan di latar belakang bila pengingat tidak muncul.
- Jika izin notifikasi dicabut atau channel dimatikan, pengingat tidak tampil (tanpa crash); status di Pengaturan menampilkannya.
- Notifikasi yang terlewat karena HP mati tidak ditampilkan belakangan bila acaranya sudah lewat.

### Uji manual di perangkat

Dialog izin sistem dan pengiriman notifikasi yang sebenarnya (Doze, reboot, OEM) tidak bisa disimulasikan penuh di unit test. Langkah uji di HP:

1. Install APK, buka Pengaturan → Notifikasi, nyalakan pengingat → dialog izin muncul → **Izinkan** → status "Diizinkan".
2. Ulangi di instalasi bersih, pilih **Jangan izinkan** → status merah + tombol "Izinkan"; tolak lagi → tombol "Buka pengaturan" membuka pengaturan notifikasi.
3. Set pengingat tugas "1 jam", buat tugas dengan deadline ±1 jam 5 menit dari sekarang → tunggu ±5 menit (bisa molor beberapa menit) → notifikasi "PR …" muncul → sentuh → detail tugas terbuka.
4. Buat tugas lain dengan deadline dekat lalu tandai selesai sebelum waktunya → tidak ada notifikasi.
5. Tambah jadwal pelajaran hari ini 15 menit dari sekarang dengan pengingat 10 menit → notifikasi "… mulai 10 menit lagi" muncul ±5 menit kemudian.
6. Buat ujian besok dengan pengingat 1 hari pada jam yang dekat → notifikasi "Ujian: …" → sentuh → detail ujian.
7. Reboot HP sebelum pengingat berikutnya (tanpa membuka aplikasi) → pengingat tetap muncul.
8. Ganti zona waktu (mis. WIB → WITA) di pengaturan tanggal & waktu → pengingat pelajaran mengikuti jam lokal yang baru.
9. Matikan "Pengingat ujian" di aplikasi → pengingat ujian tidak muncul; tugas/pelajaran tetap.

## Build

Butuh JDK 17 dan Android SDK (platform 37 diunduh otomatis oleh AGP).

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit test JVM + Robolectric (DAO, repository, migrasi, ViewModel, alur UI)
./gradlew lintDebug            # Android Lint
```

Build debug memakai debug keystore bawaan Android yang dibuat otomatis oleh AGP; tidak perlu keystore buatan sendiri.

## Test

- `data/` – DAO, repository, dan `MigrationTest` (v1 → v2).
- `domain/`, `util/`, `ui/FormLogicTest` – validasi form, urutan & filter tugas, format tanggal/durasi.
- `ui/<fitur>/*ViewModelTest` – ViewModel dengan Room in-memory dan jam tetap.
- `ui/PersistenceAcrossRestartTest` – data tetap ada setelah database ditutup dan dibuka ulang.
- `notifications/` – perhitungan waktu pengingat, jam tenang, pengulangan mingguan, zona waktu & DST, identitas notifikasi, logika izin, sinkronisasi alarm (edit/selesai/hapus/reboot/zona waktu/duplikat) dengan Room in-memory, penyimpanan DataStore, serta notifier/AlarmManager/receiver di Robolectric.
- `ui/settings/NotificationSettingsTest` – tampilan izin ditolak/diblokir, switch, dan pilihan waktu pengingat.
- `ui/AppFlowTest` – alur UI end-to-end di `MainActivity` asli (Compose + Robolectric): tambah jadwal, tugas, ujian lewat tombol dan form, centang selesai, filter, recreate activity, mengaktifkan notifikasi, alarm ujian terpasang, alarm dipicu lewat receiver, notifikasi menyentuh detail ujian, mode gelap. Set env `SCHOOLMATE_SCREENSHOT_DIR` untuk menyimpan screenshot tiap layar.

## CI

Workflow [`Android CI`](.github/workflows/android.yml) berjalan di setiap push ke `main`, pull request, dan bisa dijalankan manual. Workflow membangun debug APK, menjalankan unit test dan lint, memverifikasi tanda tangan APK, lalu mengunggah artifact `SchoolMate-debug-apk` serta laporan test/lint.
