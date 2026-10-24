# DC Android (Discord Ultra-Light Client)

Ultra-compact, memory-optimized native Android client for Discord Web with 24/7 background session retention, built with **Kotlin** and **Android WebView**.

Designed as a personal lightweight replacement for bloated mobile browsers (Chrome / Kiwi) that disconnect after 1–3 minutes when the phone screen is locked or turned off. Uses the proven background architecture from [BC-Android](../BC-Android).

---

## Key Features

- **24/7 Background Session Retention & Anti-Shutdown:**
  - Integrated foreground service (`ConnectionKeeperService`) maintaining CPU wake lock (`PARTIAL_WAKE_LOCK`) without battery-draining Wi-Fi locks.
  - Custom `KeepAliveWebView` intercepting `onWindowVisibilityChanged` to prevent Chromium's Blink rendering engine from freezing JavaScript timers, WebSockets, Gateway heartbeats, or WebRTC voice/streaming sessions when the screen turns off.
- **Ultra-Low Memory & Battery Footprint:**
  - Pure native WebView wrapper with zero browser chrome, tabs, or bloated browser extensions (**~0.66 MB APK footprint**).
  - Idle RAM usage of **~40 MB – 70 MB** (compared to 500 MB – 800 MB on mobile Chrome).
  - Active memory pruning via Android `onTrimMemory` hooks clears non-essential cache buffers during OS memory pressure without dropping active voice or chat sessions.
  - Automatically throttles CSS animations and caps `requestAnimationFrame` down to **1 FPS** when minimized or screen-locked to eliminate CPU heat and conserve battery.
- **HTML5 Desktop Notification Bridge:**
  - Native polyfill for Web Notifications (`new Notification(title, { body })`) that routes Discord DMs and mentions straight into Android's native heads-up notification system.
- **Discreet / Stealth Flavor:**
  - Includes an optional discreet build variant with a minimal app name (`"DC"`), transparent launcher icon, and privacy notifications (`"Active session"` instead of revealing titles).
- **Crash Auto-Recovery with Crash-Loop Guard:**
  - Automatically recovers and restarts frozen or memory-exhausted WebKit render processes. If consecutive crashes exceed 3 within 30s, it clears corrupted session storage and notifies the user to prevent ANR crash-loops.
- **Dynamic Notification Mention Badge:**
  - Automatically parses Discord's unread mention counter from page titles and displays active mention counts (`(3) new mentions`) on the persistent keep-alive notification.
- **Dynamic Pinch-to-Zoom & Viewport Adaptation:**
  - Overrides viewport scaling constraints, allowing up to 5x pinch-to-zoom magnification across Discord desktop web UI for effortless readability on high-DPI mobile screens.
- **Desktop Web Client Spoofing & Mobile Tweaks:**
  - Automatically injects modern Desktop Chrome User-Agent to bypass Discord's mobile redirect and load the full Discord Web app directly.
  - Injects responsive mobile CSS tweaks to eliminate "Download the App" and "Better in App" nagging overlays.
- **WebRTC Voice & Audio Integration:**
  - Watch-only and listen-only WebRTC audio playback without activating VoIP Call Mode, keeping audio purely on standard media stream without intrusive call volume overlays.
  - Web Audio Context lifecycle hooks keep audio output primed without freezing when switching apps.
  - Audio playback configured without requiring manual user touch gestures (`mediaPlaybackRequiresUserGesture = false`).
- **Zero-Bar Immersive Edge-to-Edge:**
  - Fullscreen display with transparent status and navigation bars, reapplied seamlessly across orientation changes.
- **Double-Tap Back Protection:**
  - Prevents accidental swipes or back presses from disconnecting your voice room or closing Discord.
- **Native File Chooser & Download Bridge:**
  - Full support for uploading pictures, files, and attachments into Discord chats directly from your phone's gallery or file manager.
  - Full support for saving attachments, blobs, and media files directly into the Android Downloads directory.
- **Native External Link Chooser:**
  - Links sent in chat (YouTube, Spotify, external websites, bug trackers) open via Android's native system app picker (with safe `intent://` scheme support) so your Discord voice room session is never disrupted.

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
│       │   ├── DCApplication.kt           # Application setup, DevTools, & onTrimMemory
│       │   ├── KeepAliveWebView.kt        # Intercepts visibility to prevent Chromium freeze
│       │   ├── MainActivity.kt            # WebSettings, Desktop UA, WebRTC, Zoom, AudioContext
│       │   ├── WebAppInterface.kt         # JS bridge: downloads, clipboard, external chooser
│       │   ├── memory/
│       │   │   └── MemoryOptimizer.kt     # 1 FPS RAF throttle, GIF/media suppression, onTrimMemory
│       │   └── service/
│       │       └── ConnectionKeeperService.kt # Foreground service, WakeLock, badge counter
│       └── res/
│           ├── drawable/ic_discord_logo.xml
│           ├── layout/activity_main.xml
│           ├── xml/
│           │   └── network_security_config.xml
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

### Gradle Commands
```bash
# Debug build (Standard):
./gradlew assembleStandardDebug

# Release build (Standard & Discreet):
./gradlew assembleStandardRelease assembleDiscreetRelease
```

The output APKs are saved in `publish/`:
- `publish/DC-Android-Standard-Release.apk` (~0.66 MB)
- `publish/DC-Android-Discreet-Release.apk` (~0.66 MB)

---

## Background Setup on Aggressive OEM Android (MIUI / HyperOS / Samsung / Vivo)

For completely uninterrupted 24/7 background voice or stream-watching sessions:
1. Go to **Settings ➔ Apps ➔ DC Android ➔ Battery saver**: Set to **"No restrictions"** (Unrestricted).
2. Enable **"Autostart"** (Mulai otomatis).
3. Open **Recent Apps**, long-press DC Android, and tap the **Padlock icon** to prevent task killers from clearing the app during heavy multitasking.

---

## License

Personal project under the MIT License.
