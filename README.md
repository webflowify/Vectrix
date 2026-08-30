# Vectrix (PlayStation 1 Emulator for Android)

A native PlayStation 1 emulator for Android. The app is written in **Java** (Android SDK with Material 3, ViewBinding), while the emulation core — CPU (MIPS R3000A), GTE, GPU, SPU, and CD-ROM — runs in **C/C++** compiled with the Android NDK and called through JNI.

The emulation core is [PCSX ReARMed](https://github.com/libretro/pcsx_rearmed) (GPLv2), bundled as source under `app/src/main/cpp/core` (with modifications) and exposed to the Java layer through a thin JNI bridge. Because the Android build links the GPLv2-only ARM dynarec, the whole application is distributed under **GPLv2** (version 2 only).

> **Note:** you must supply your own PS1 BIOS and game/ROM files. This project does **not** bundle a BIOS, any games, or ROMs. See [Legal notice](#legal-notice).

---

## Features

- **Game library** — import ROMs via the Storage Access Framework (SAF), browse a grid of games, track recently played, per-game context menu.
- **Emulation core** — PCSX ReARMed (CPU interpreter + ARM dynarec, GPU NEON renderer, HLE/Linux cores), CHD/`.bin/.cue`/`.img`/`.iso` support, `7z` archive import.
- **Rendering** — OpenGL ES (`GLSurfaceView`) with a software GPU fallback (unai) for non-ARM builds.
- **Audio** — native output via [Oboe](https://github.com/google/oboe) low-latency audio.
- **Input** — on-screen virtual gamepad (drag/resize/opacity configurable) plus physical Bluetooth/USB controller support with re-mappable buttons.
- **Save states** — multiple slots with screenshot thumbnails; import/export/overwrite protection.
- **Memory cards** — virtual `.mcr` per game, import/export.
- **Pause/resume** — with a visual overlay, auto-rotate option.
- **Cheats** — parse and apply GameShark/Action Replay cheats.
- **Settings** — renderer, frame skip, aspect ratio, audio, auto-rotate, controller mapping, and more.
- **Onboarding** — first-run legal disclaimer and BIOS import wizard.

---

## Tech stack

| Layer | Technology |
|---|---|
| App / UI | Java (Android SDK, AndroidX, Material Components, ConstraintLayout, ViewBinding) |
| Emulation core | PCSX ReARMed (C/C++) via JNI, `libps1emulator` |
| Native build | C/C++17, NDK, CMake 3.22.1 |
| Build system | Gradle 9.1.0 + Groovy DSL + AGP 9.0.1 |
| Java | Java 11 source/target |
| SDK | compileSdk = 36, targetSdk = 36, minSdk = 24 |
| Storage | Room (game library), Storage Access Framework |
| Audio | Oboe 1.10.0 |
| Images | Glide |
| Testing | JUnit 4, Mockito, Robolectric, Espresso |

---

## Building

Requirements:

- JDK 17+ for Gradle (project targets Java 11 source/target)
- Android SDK with NDK and CMake 3.22.1 (configured via `local.properties`, gitignored)

From the repository root:

```powershell
# on Windows  (on macOS/Linux use ./gradlew)
gradlew.bat assembleDebug     # build debug APK
gradlew.bat installDebug      # install on a connected device
gradlew.bat test              # unit tests (JVM)
gradlew.bat connectedAndroidTest # instrumented tests (device/emulator)
gradlew.bat lint              # Android Lint
```

The release build has ProGuard disabled (`minifyEnabled false`).

**Note:** the PCSX ReARMed core is bundled under `app/src/main/cpp/core`; no extra step is required after cloning.

---

## Getting started (on a device)

1. Build and install the app (`gradlew.bat installDebug`).
2. Accept the **legal disclaimer** on first launch.
3. Import your **BIOS** (a 512 KB PlayStation BIOS dump from hardware you own) via `Onboarding / BIOS import`.
4. Import a game ROM (`.bin/.cue`, `.img`, `.iso`, `.chd`, or `.7z`) from your library.
5. Tap a game to launch it.

---

## Legal notice

- This emulator does **not** bundle, host, or link to any BIOS, ROM, or game files.
- You must own the original hardware/software you emulate. Use only legally-owned backups or homebrew.
- Emulated content, trademarks ("PlayStation", "PSX", "Sony"), and their logos are the property of their respective owners.
- The emulation core (PCSX ReARMed) is licensed under the **GNU GPLv2** — and because the shipped Android build compiles the GPLv2-*only* ARM dynarec ("Ari64"), the combined application is distributed under **GPLv2 (version 2 only, not "or later")**. If you distribute this app, you must make the complete corresponding source code available under GPLv2. The full license is in `LICENSE` and all third-party attributions are in `THIRD-PARTY-NOTICES.md`. Both are also shown in-app via **Game Library → (menu) → Licenses**.

  The complete corresponding source is published at the public repository:
  <https://github.com/webflowify/Vectrix>
