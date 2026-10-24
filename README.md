# DC Android (Discord Ultra-Light Client)

Ultra-compact, memory-optimized native Android client for Discord Web with 24/7 background session retention, built with **Kotlin** and **Android WebView**.

Designed as a personal lightweight replacement for bloated mobile browsers (Chrome / Kiwi) that disconnect after 1–3 minutes when the phone screen is locked or turned off. Uses the proven background architecture from [BC-Android](../BC-Android).

---

## Key Features

- **24/7 Background Session Retention & Anti-Shutdown:**
  - Integrated foreground service (`ConnectionKeeperService`) maintaining CPU wake lock (`PARTIAL_WAKE_LOCK`) and low-latency Wi-Fi lock (`WIFI_MODE_FULL_LOW_LATENCY`).
  - Custom `KeepAliveWebView` intercepting `onWindowVisibilityChanged` to prevent Chromium's Blink rendering engine from freezing JavaScript timers, WebSockets, Gateway heartbeats, or WebRTC voice/streaming sessions when the screen turns off.
- **Ultra-Low Memory Footprint:**
  - Pure native WebView wrapper with zero browser chrome, tabs, or bloated browser extensions (~0.8 MB APK footprint).
  - Idle RAM usage of **~45 MB – 75 MB** (compared to 500 MB – 800 MB on mobile Chrome).
  - Active memory pruning via Android `onTrimMemory` hooks clears non-essential cache buffers during OS memory pressure without dropping active voice or chat sessions.
  - Automatically throttles CSS animations and redraws when minimized or screen-locked to eliminate CPU heat and conserve battery.
- **Desktop Web Client Spoofing:**
  - Automatically injects modern Desktop Chrome User-Agent to bypass Discord's mobile redirect and load the full Discord Web app directly.
- **WebRTC Voice & Audio Integration:**
  - Native handling of `RECORD_AUDIO` and `WebChromeClient.onPermissionRequest` for voice channels and video streams.
  - Audio playback configured without requiring manual user touch gestures (`mediaPlaybackRequiresUserGesture = false`).
- **Zero-Bar Immersive Edge-to-Edge:**
  - Fullscreen display with transparent status and navigation bars.
- **Double-Tap Back Protection:**
  - Prevents accidental swipes or back presses from disconnecting your voice room or closing Discord.
- **Native File Chooser Bridge:**
  - Full support for uploading pictures, files, and attachments into Discord chats directly from your phone's gallery or file manager.
- **Native External Link Chooser:**
  - Links sent in chat (YouTube, external websites, bug trackers) open via Android's native system app picker so your Discord voice room session is never disrupted.

---

## Architecture Comparison

| Metric / Capability | Mobile Browsers (Chrome / Edge) | Official Discord App | DC Android (This Client) |
| :--- | :--- | :--- | :--- |
| **Footprint (Installed)** | ~200 MB – 450 MB | ~180 MB – 300 MB | **~2 MB – 5 MB** |
| **RAM Usage (Idle/Voice)**| ~500 MB – 850 MB | ~250 MB – 400 MB | **~45 MB – 80 MB** |
| **Screen-Off Background** | Dies in 1–3 minutes | Throttled by OS killers | **Continuous 24/7 (WakeLock + Foreground Service)** |
| **Audio / Stream Farming**| Muted/killed when locked | Can drop in background | **Persistent WebRTC audio & connection** |
| **Telemetry / Trackers**  | Full Google telemetry | Full Discord telemetry | **Minimal OS WebView container** |

---

## Project Structure

```text
DC-android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/izumii/dcandroid/
│       │   ├── DCApplication.kt           # Application setup & debug hooks
│       │   ├── KeepAliveWebView.kt        # Intercepts visibility to prevent Chromium freeze
│       │   ├── MainActivity.kt            # WebSettings, Desktop UA, WebRTC permissions
│       │   ├── memory/
│       │   │   └── MemoryOptimizer.kt     # CSS throttle & onTrimMemory buffer pruning
│       │   └── service/
│       │       └── ConnectionKeeperService.kt # Foreground service, WakeLock, WiFiLock
│       └── res/
│           ├── drawable/ic_discord_logo.xml
│           ├── layout/activity_main.xml
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               └── themes.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat
```

---

## Building from Source

### Prerequisites
- JDK 17 or newer
- Android SDK (API 34, Build Tools 34.0.0)

### Command
```bash
./gradlew assembleDebug
# or for release:
./gradlew assembleRelease
```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Background Setup on Aggressive OEM Android (MIUI / HyperOS / Samsung / Vivo)

For completely uninterrupted 24/7 background voice or stream-watching sessions:
1. Go to **Settings ➔ Apps ➔ DC Android ➔ Battery saver**: Set to **"No restrictions"** (Unrestricted).
2. Enable **"Autostart"** (Mulai otomatis).
3. Open **Recent Apps**, long-press DC Android, and tap the **Padlock icon** to prevent task killers from clearing the app during heavy multitasking.

---

## License

Personal project under the MIT License.
