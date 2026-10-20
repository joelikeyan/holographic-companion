# Android Holographic System — Cortana Hologram Companion

> Android shell, Native light,
> Draggable node, Rendering bright.
> Overlay high, Inference fast,
> Drawn in Vulkan, built to last!

A persistent floating holographic AI companion for Android, built around a
Cortana-inspired avatar rendered above the OS in a `TYPE_APPLICATION_OVERLAY`
window. Palette: Electric Blue `#00E5FF`, Soft Indigo `#4A148C`, Magenta `#FF007F`.

**Version 0.1.0 — buildable skeleton.** The overlay shell is real and working.
The volumetric Filament/Vulkan renderer and the on-device AI engines are
plugged in as documented next steps (see Status).

## Status — what's real vs. what's next

| Piece | Status |
|---|---|
| Overlay window (`TYPE_APPLICATION_OVERLAY`), draggable | ✅ Implemented (`HolographicOverlayService`) |
| Tap to collapse into glowing halo node / expand back | ✅ Implemented |
| Foreground service + persistent notification | ✅ Implemented |
| Launcher flow: requests overlay permission, starts service | ✅ Implemented (`MainActivity`) |
| Auto-restart after reboot / app update | ✅ Implemented (`BootCompletedReceiver`, best-effort on Android 12+) |
| Hologram visuals | ⚠️ **Placeholder** — 2D Canvas animation (pulsing core, telemetry rings, scanlines, embers) in `HologramSurfaceView`. The Filament/Vulkan volumetric renderer will render into this same SurfaceView later |
| Gyro/accelerometer parallax | 🔜 Roadmap |
| Audio-reactive FFT pulses | 🔜 Roadmap |
| Speech recognition (whisper.cpp, AAudio/Oboe) | 🔜 Roadmap — pipeline specced, not wired |
| Voice (Piper TTS) | 🔜 Roadmap |
| On-device LLM (MediaPipe + Gemma-2B / Llama-3-8B, Vulkan) | 🔜 Roadmap |
| Live telemetry (CPU / battery / thermals) | 🔜 Roadmap — binds to `ActivityManager` / `BatteryManager` |
| Particle disperse/reassemble transitions | 🔜 Roadmap (Vulkan compute) |

## Project layout

```
app/src/main/
  AndroidManifest.xml
  java/com/cortana/hologram/
    HolographicOverlayService.kt   ← Joe's overlay service (drag, tap-toggle, FGS)
    HologramSurfaceView.kt         ← placeholder renderer (Filament plugs in here)
    MainActivity.kt                ← launcher + overlay-permission flow
    BootCompletedReceiver.kt       ← restart after boot/update
  res/layout/overlay_hologram_layout.xml
  res/drawable/halo_ring.xml, ic_launcher_foreground.xml
scripts/deploy_holographic_companion.sh
.github/workflows/build.yml        ← builds the APK in the cloud, no local SDK needed
assets/models/                     ← (you add) whisper-tiny.bin, gemma-2b-it-cpu-int4.bin
```

One build fix vs. the original spec: the manifest's `package=` attribute moved
to `namespace`/`applicationId` in `app/build.gradle` (required by AGP 8).
Everything else from the spec is verbatim.

## Build it

### Option A — GitHub Actions (no local Android SDK needed)
1. Push this folder to a GitHub repo.
2. Open the repo's **Actions** tab → the APK builds automatically on push
   (or run "Build APK" manually).
3. Download `cortana-hologram-debug-apk` from the run's artifacts.

### Option B — Android Studio
Open the folder in Android Studio, let it sync (it generates the Gradle
wrapper), then **Run ▶** on a device with Vulkan support.

### Option C — Command line
Requires JDK 17 + Android SDK (compileSdk 34). Generate the wrapper once
(`gradle wrapper --gradle-version 8.9`), then:

```
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Deploy to a phone (ADB)

```
chmod +x scripts/deploy_holographic_companion.sh
./scripts/deploy_holographic_companion.sh
```

The script builds, installs, grants audio + overlay permissions, and starts
the service. Model files are optional in v0.1 (the AI pipeline isn't wired
yet); if present in `assets/models/`, they are pushed to the phone.

## Permissions & platform notes

- **Overlay**: the app opens system settings for "display over other apps"
  on first launch; nothing floats until you grant it.
- **Android 14**: the service uses the `specialUse` foreground-service type,
  declared in the manifest with its subtype property, per the spec.
- **Boot start**: Android 12+ restricts foreground-service starts from boot
  receivers on some devices. If the hologram doesn't return after a reboot,
  tap the app icon once — the receiver failure is handled silently by design.
- **Vulkan**: the manifest requires Vulkan 1.1+ hardware, per the spec, so
  the app won't install on devices without it.

## Roadmap (in order)

1. Filament scene in `HologramSurfaceView`'s surface: avatar mesh, scanline /
   noise / chromatic-aberration shaders, volumetric bloom.
2. Sensor parallax (gyroscope + accelerometer) and idle micro-animation.
3. whisper.cpp via AAudio/Oboe for offline listening; Piper TTS voice.
4. MediaPipe LLM Inference (Gemma-2B int4 first) on Vulkan compute.
5. Telemetry rings bound to real CPU/battery/thermal data; FFT-reactive core.
