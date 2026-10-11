# SchoolMate

Aplikasi Android manajemen waktu untuk pelajar SMA: jadwal pelajaran, tugas dan PR, pengingat ujian, dan mode fokus. Semua data tersimpan di perangkat (offline-first), tanpa akun, Firebase, atau API berbayar.

## Status

Tahap 1 – fondasi proyek: build stabil, arsitektur MVVM, Room, DataStore, Navigation Compose, CI yang menghasilkan APK.

| Fitur | Status |
|---|---|
| Navigasi bawah (Beranda, Mapel, Tugas, Fokus, Pengaturan) | Selesai |
| Kelola mata pelajaran (Room) | Selesai |
| Nama panggilan, tema terang/gelap/sistem, warna dinamis (DataStore) | Selesai |
| Jadwal mingguan, tugas/PR, ujian, pengingat, Pomodoro | Tahap berikutnya |

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

Alur data: `Room/DataStore → Repository (Flow) → ViewModel (StateFlow) → Compose UI`.

## Build

Butuh JDK 17 dan Android SDK (platform 37 diunduh otomatis oleh AGP).

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit test JVM
./gradlew lintDebug            # Android Lint
```

Build debug memakai debug keystore bawaan Android yang dibuat otomatis oleh AGP; tidak perlu keystore buatan sendiri.

## CI

Workflow [`Android CI`](.github/workflows/android.yml) berjalan di setiap push ke `main`, pull request, dan bisa dijalankan manual. Workflow membangun debug APK, menjalankan unit test dan lint, memverifikasi tanda tangan APK, lalu mengunggah artifact `SchoolMate-debug-apk` serta laporan test/lint.
