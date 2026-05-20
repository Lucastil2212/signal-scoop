# Signal Scoop

**Signal Scoop** is a privacy-first Android app that surveys **local wireless signals** around your phone: BLE beacons, Wi-Fi access points, paired Bluetooth devices, NFC status, and onboard sensors. Everything runs **on-device** — no accounts, no cloud, no tracking.

<p align="center">
  <img src="SIGNAL%20SCOOP%20LOGO.png" alt="Signal Scoop logo" width="220" />
</p>

## What it detects

| Source | What you see |
|--------|----------------|
| **BLE scan** | Nearby BLE devices (earbuds, trackers, IoT, beacons) with RSSI |
| **Wi-Fi scan** | Nearby access points / hotspots (SSID, BSSID, signal, security) |
| **Paired Bluetooth** | Devices already bonded with your phone |
| **Sensors** | Magnetometer, accelerometer, and other hardware exposed to apps |
| **NFC** | Whether NFC hardware exists and is enabled |
| **Ask** | Natural-language Q&A over the current scan (on-device LLM) |
| **History** | Saved scans with GPS time/place and a local knowledge graph for trends |

Signal Scoop is a **read-only survey tool**. It does not connect to networks, deauthenticate clients, fingerprint private devices, or bypass Android permissions.

### Ask (on-device assistant)

After a scan, open the **Ask** tab for summaries and analysis. Built-in queries (summarize, analyze risk, counts, list BLE/Wi-Fi, etc.) answer **directly from scan data** with no model required. Open-ended questions use an optional **local** LiteRT/MediaPipe model ([cil-graph](https://github.com/contextgraph/cil-graph) android stack) — scan payloads are never uploaded.

### Scan history & knowledge graph

Each scan is **saved on-device** with a timestamp and a **native GPS fix** (when location is enabled). Open the **Graph** tab for:

- **Timeline** — rename, delete, expand scans; pet names per signal; photos/videos; device links
- **3D Graph** — interactive Three.js viewer (pinch/drag) of scans, places, signals, EVRUS nodes
- **Vault** — everything collected locally (scans, aliases, media, notes, EVRUS links)

**EVRUS / EVRMORE** — local connector stores identity and P2P refs; optional handoff to an installed EVRUS companion app; graph anchoring stays on-device.

Published by **Manticore Technologies, LLC**.

### Risk summary

After each scan, the app shows a simple **risk score** (0–100) based on heuristics such as unknown BLE names, hidden Wi-Fi SSIDs, and very strong nearby RSSI. This is guidance only — not a guarantee of surveillance devices.

## Requirements

- Android **8.0+** (API 26+)
- Bluetooth and Wi-Fi enabled for full results
- **Location permission** may be required for Wi-Fi scans (Android platform rule)
- Android 12+: `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`
- Android 13+: `NEARBY_WIFI_DEVICES` (when applicable)

## Publish on Google Play ($2)

Publishing requires **your** [Google Play Developer](https://play.google.com/console) account (**$25 one-time** registration, separate from the app price).

1. Follow **[docs/play-store/PUBLISHING.md](docs/play-store/PUBLISHING.md)** (pricing, listing, data safety).
2. Host **[docs/privacy-policy.html](docs/privacy-policy.html)** at a public HTTPS URL (required).
3. Build the signed bundle:
   ```bash
   ./scripts/generate-release-keystore.sh   # once — back up the .jks file
   ./scripts/build-play-bundle.sh           # → release/signal-scoop-play.aab
   ```
4. Upload `signal-scoop-play.aab` in Play Console and set **Paid → $2.00**.

## Install the APK

Prebuilt APKs (in `release/`):

| File | Notes |
|------|--------|
| [`signal-scoop-debug.apk`](release/signal-scoop-debug.apk) | **Recommended for sideloading** — debug-signed, installs without extra signing steps |
| [`signal-scoop-release.apk`](release/signal-scoop-release.apk) | Release build (unsigned); sign with your own keystore for production |

On your phone:

1. Copy the APK to the device (USB, cloud drive, etc.).
2. Open it and allow **Install unknown apps** if prompted.
3. Grant permissions when you first tap **Scan nearby signals**.

## Build from source

### Prerequisites

- JDK 17+
- Android SDK with API 35 and Build-Tools 35

Set `sdk.dir` in `android/local.properties` (see `android/local.properties.example`).

If `./gradlew` reports **JAVA_HOME is not set**, either:

```bash
# One-time per terminal session
source android/env.sh
```

or install a system JDK (`sudo apt install openjdk-17-jdk`) and set `JAVA_HOME` in your shell profile.

This repo also sets `org.gradle.java.home` in `android/gradle.properties` when using the portable JDK at `~/.local/jdk/jdk-17`.

### Commands

```bash
cd android
source env.sh          # optional if JAVA_HOME is already configured
./gradlew assembleDebug
./gradlew assembleRelease
```

The unsigned release APK is written to:

`android/app/build/outputs/apk/release/app-release-unsigned.apk`

Copy or rename it as needed. For Play Store distribution you would sign with your own keystore.

## Project layout

```
signal-scoop/
├── android/                 # Kotlin + Jetpack Compose app
├── release/                 # Shipped APK for sideloading
├── LICENSE                  # MIT (Manticore Technologies, LLC)
├── NOTICE.md                # Copyright and attribution
├── seed.md                  # Original MVP notes
├── SIGNAL SCOOP LOGO.png    # Brand asset
└── .beads/                  # Task tracking (Beads)
```

## Task tracking (Beads)

This repo uses [Beads](https://github.com/gastownhall/beads) (`bd`) for agent-friendly issue tracking.

```bash
bd ready          # tasks ready to work
bd show <id>      # task details
bd close <id>     # mark done
```

Run `bd prime` in the project root for workflow context.

## License

Copyright © 2026 **Manticore Technologies, LLC**. All rights reserved.

Signal Scoop is released under the [MIT License](LICENSE). Redistributions must retain the copyright notice and license text.

## Security & privacy

Signal Scoop is built for **local-only, read-only** surveying. See **[SECURITY.md](SECURITY.md)** for the full policy.

| Control | Implementation |
|---------|----------------|
| No scan upload | Scan/Ask context stays on-device; HTTPS only for optional model download |
| Local history only | Saved scans + graph in app-private DB; live session clears on exit; backup disabled |
| Minimal permissions | Bluetooth/Wi-Fi/location with `neverForLocation` where supported; legacy BT on API 26–30 |
| User consent | Scan runs only after you grant permissions and tap Scan |
| Screen capture | `FLAG_SECURE` on the main screen |
| Session privacy | Results cleared when you leave the app (`onStop`) |
| Release hardening | R8 minify + shrink resources; verbose logs stripped |

Expand **Privacy & security** in the app for a short in-ui summary.

## Safety & limitations

- Passive microphones or cameras **not broadcasting** radio signals cannot be detected reliably.
- Wi-Fi scan frequency is **throttled by Android**; repeated scans may return stale or empty results.
- RSSI and risk scores are **heuristic**, not forensic proof.

Use Signal Scoop as one input among many when reviewing your environment.
