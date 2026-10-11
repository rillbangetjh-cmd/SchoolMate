# SchoolMate

Aplikasi Android manajemen waktu untuk pelajar SMA: jadwal pelajaran, tugas dan PR, pengingat ujian, dan mode fokus. Semua data tersimpan di perangkat (offline-first), tanpa akun, Firebase, atau API berbayar.

## Status

- Tahap 1 – fondasi proyek: build stabil, arsitektur MVVM, Room, DataStore, Navigation Compose, CI yang menghasilkan APK.
- Tahap 2 – data inti: jadwal mingguan, tugas/PR, ujian, sesi belajar (Room v2 dengan migrasi teruji), ringkasan di Beranda.
- Tahap 3 – UI dan fitur inti: semua layar terhubung ke Room, tambah/edit/hapus untuk jadwal, tugas, dan ujian.

| Fitur | Status |
|---|---|
| Navigasi bawah: Beranda, Jadwal, Tugas, Ujian, Pengaturan | Selesai (tahap 3) |
| Beranda: tanggal, pelajaran hari ini, tugas terlambat & terdekat, ujian mendatang, waktu belajar minggu ini | Selesai (tahap 3) |
| Jadwal mingguan Senin–Minggu + form dengan deteksi bentrok langsung | Selesai (tahap 3) |
| Tugas: urut deadline, filter Aktif/Terlambat/Selesai/Semua, centang cepat + urungkan, detail & status | Selesai (tahap 3) |
| Ujian: mendatang/sudah lewat, form nama, mapel, tanggal, jam, durasi, catatan | Selesai (tahap 3) |
| Catatan belajar (input manual per minggu) dan kelola mapel, lewat Pengaturan | Selesai (tahap 3) |
| Nama panggilan, tema terang/gelap/sistem, warna dinamis (DataStore) | Selesai |
| Pengingat/notifikasi; Pomodoro (tab Fokus kembali di tahap itu) | Tahap berikutnya |

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
- `ui/AppFlowTest` – alur UI end-to-end di `MainActivity` asli (Compose + Robolectric): tambah jadwal, tugas, ujian lewat tombol dan form, centang selesai, filter, recreate activity, mode gelap. Set env `SCHOOLMATE_SCREENSHOT_DIR` untuk menyimpan screenshot tiap layar.

## CI

Workflow [`Android CI`](.github/workflows/android.yml) berjalan di setiap push ke `main`, pull request, dan bisa dijalankan manual. Workflow membangun debug APK, menjalankan unit test dan lint, memverifikasi tanda tangan APK, lalu mengunggah artifact `SchoolMate-debug-apk` serta laporan test/lint.
