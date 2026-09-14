# PS1 Emulator (PlayStation 1 Emulator for Android)

A native PlayStation 1 emulator for Android. The app is written in **Java** (Android SDK with Material 3, ViewBinding), while the emulation core — CPU (MIPS R3000A), GTE, GPU, SPU, and CD-ROM — runs in **C/C++** compiled with the Android NDK and called through JNI.

The emulation core is [PCSX ReARMed](https://github.com/libretro/pcsx_rearmed) (GPLv2), integrated as a git submodule and exposed to the Java layer through a thin JNI bridge. Because the Android build links the GPLv2-only ARM dynarec, the whole application is distributed under **GPLv2** (version 2 only).

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

## Architecture

```
┌──────────────────────────────────────────────┐
│             Android App (Java)               │
│  Activities / Fragments / Views (Material)   │
│  - Library, Emulation, Settings, Save states │
│  - Onboarding (disclaimer, BIOS import)      │
└──────────────┬───────────────────────────────┘
               │ JNI calls / callbacks
┌──────────────▼───────────────────────────────┐
│              JNI bridge (C++)                │
│   jni_bridge.cpp  ·  audio_oboe.cpp          │
└──────────────┬───────────────────────────────┘
┌──────────────▼───────────────────────────────┐
│       PCSX ReARMed core (C/C++)              │
│   MIPS R3000A + dynarec · GTE · GPU          │
│   SPU · CD-ROM · memory cards · save states  │
└──────────────────────────────────────────────┘
```

**Threading** — the UI thread never calls into the emulator directly. Emulation runs on a dedicated
thread hosted by `EmulatorService`; audio is pulled from a low-latency native callback; rendering
happens on the `GLSurfaceView`'s GL thread.

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

## Project structure

```
app/
├── src/main/java/com/tansoft/ps1emulator/
│   ├── MainActivity.java                 # entry point / onboarding gate
│   ├── core/                             # EmulatorBridge (JNI), EmulatorService, SettingsHelper
│   ├── ui/
│   │   ├── library/                      # game grid, adapter, ViewModel, context menu
│   │   ├── emulation/                    # EmulationActivity (GLSurfaceView + overlay)
│   │   ├── settings/                     # settings + controller mapping
│   │   ├── savestate/                    # save-state slots
│   │   └── onboarding/                   # disclaimer + BIOS import
│   ├── input/                            # virtual gamepad, controller mapping
│   ├── storage/                          # ROM/BIOS/memory-card/save import-export
│   ├── data/                             # Room (GameEntity/Dao/Database)
│   ├── cheat/                            # CheatCodeParser
│   └── util/                             # EdgeToEdgeHelper, ThumbnailExtractor, ObjectPool
└── src/main/cpp/
    ├── CMakeLists.txt                    # native build (core + bridge + audio)
    ├── jni_bridge.cpp                    # JNI entry points
    ├── audio_oboe.cpp                    # native audio output
    ├── gpu_freeze_wrapper.c
    └── core/                             # PCSX ReARMed submodule
```

---

## Building

Requirements:

- JDK 17+ for Gradle (project targets Java 11 source/target)
- Android SDK with NDK and CMake 3.22.1 (see `local.properties`, gitignored)

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

**Note:** the emulation core is a git submodule. After cloning, initialize it first:

```sh
git submodule update --init --recursive
```

---

## Getting started (on a device)

1. Build and install the app (`gradlew.bat installDebug`).
2. Accept the **legal disclaimer** on first launch.
3. Import your **BIOS** (a 512 KB PlayStation BIOS dump from hardware you own) via
   `Onboarding / BIOS import`.
4. Import a game ROM (`.bin/.cue`, `.img`, `.iso`, `.chd`, or `.7z`) from your library.
5. Tap a game to launch it.

---

## Legal notice

- This emulator does **not** bundle, host, or link to any BIOS, ROM, or game files.
- You must own the original hardware/software you emulate. Use only legally-owned backups or homebrew.
- Emulated content, trademarks ("PlayStation", "PSX", "Sony"), and their logos are the property of their
  respective owners.
- The emulation core (PCSX ReARMed) is licensed under the **GNU GPLv2** — and because the shipped
  Android build compiles the GPLv2-*only* ARM dynarec ("Ari64"), the combined application is
  distributed under **GPLv2 (version 2 only, not "or later")**. If you distribute this app, you must
  make the complete corresponding source code available under GPLv2. The full license is in `LICENSE`
  and all third-party attributions are in `THIRD-PARTY-NOTICES.md`. Both are also shown in-app via
  **Game Library → (menu) → Licenses**.

  The complete corresponding source is published at the public repository:
  <https://github.com/webflowify/Vectrix>

---

## Roadmap

See `ps1-emulator-plan.md` for the full phased development plan and `DECISION-CORE-STRATEGY.md`
for the rationale behind using the PCSX ReARMed core.