# Signal Scoop

**Signal Scoop** is a privacy-first Android **defensive sentinel** that surveys **local wireless signals** around your phone: BLE beacons, Wi-Fi access points, paired Bluetooth devices, NFC status, and onboard sensors. After each scan it flags hacking/surveillance-style patterns (unknown BLE, hidden Wi-Fi, strong proximate RSSI) with actionable guidance. Everything runs **on-device** — no accounts, no cloud, no tracking.

<p align="center">
  <img src="SIGNAL%20SCOOP%20LOGO.png" alt="Signal Scoop logo" width="220" />
</p>

**Current release:** `1.7.5-beta` (versionCode 16)

## What it detects

| Source | What you see |
|--------|----------------|
| **BLE scan** | MAC, name, RSSI, manufacturer ID, service UUIDs, TX power, connectable flag, advertisement bytes |
| **Wi-Fi scan** | SSID, BSSID, RSSI, channel/band, connected-AP flag, security capabilities |
| **Paired Bluetooth** | Bond state, device class, link type |
| **Sensors** | Vendor, type, resolution, range, min delay, power draw |
| **NFC** | Whether NFC hardware exists and is enabled |
| **Session** | Device model, Android version, scan duration, permissions, radio/VPN flags |
| **Ask** | Natural-language Q&A over the current scan (on-device LLM) |
| **Knowledge** | Saved scans, local graph map, vault (aliases, media, notes) |

Signal Scoop is a **read-only survey tool**. It does not connect to networks, deauthenticate clients, fingerprint private devices, or bypass Android permissions.

## How to use the app

Bottom navigation has four tabs. Everything is labeled in the UI — you should not need external docs for day-to-day use.

### Scan tab (home)

1. Tap **Scan nearby signals** at the top (grant permissions when prompted).
2. Review **risk**, **sentinel alerts**, and the **findings** list; filter by BLE / Wi-Fi / Bluetooth / sensors / NFC.
3. Scroll to **Knowledge graph** — tap the card or mini map for **full-screen map**, or **Open Map tab** for the Graph hub.
4. **Ask β** tab is for questions about the **current** scan only.

Each scan you run is **saved automatically** with time and GPS (when location is on).

### Graph tab (Knowledge hub)

Three sub-tabs:

| Sub-tab | What you do |
|---------|-------------|
| **Scans** | **Graph insights** → tap to open **full-screen map**. Tap any **scan card** → bottom sheet with **all signals** (filter BLE / Wi-Fi / BT). Checkboxes + **Save PDF** / **Share** for reports (every signal + collection summary per scan). |
| **Map** | Geo map (Carto dark tiles) or layout fallback when no GPS. **Past scans** strip: tap a chip to filter the map and open that scan’s signals. Tap **nodes** or **lines** for details. **↗** in the top bar = full screen. |
| **Vault** | Counts and lists for scans, pet names, media, notes, EVRUS links — all on-device. |

Top bar **↗** (when a graph exists) opens full-screen map from any sub-tab.

### Connect tab

Local mesh only (no cloud): **Messages**, **Voice mesh**, **Mesh radio** — see [SECURITY.md](SECURITY.md).

### Ask tab

Built-in queries use scan data directly. Open-ended chat uses an optional **local** model (MediaPipe LiteRT `.task` bundles) with a **Context Integration Layer** that layers the current scan plus on-device knowledge-graph history into the prompt. Nothing is uploaded.

## Features in detail

### Defense sentinel & risk summary

After each scan: **defense score** (0–100), **sentinel alerts**, and a **risk score** from heuristics (unknown BLE, hidden Wi-Fi, strong RSSI). Guidance only — not forensic proof.

### Ask (on-device assistant)

Summarize, analyze risk, list BLE/Wi-Fi, etc. work **without** a downloaded model. Optional `.task` model via HTTPS download or **Pick model** from storage.

### Scan history & knowledge graph

- **On-device database** — scans, findings, GPS, risk, graph nodes/edges (Room).
- **Native map** — OSMDroid + Carto dark basemap when nodes have coordinates; otherwise a **2D canvas** layout.
- **Color coding** — scan sessions (timeline chips), BLE (cyan), Wi-Fi (amber), Bluetooth (pink), NFC (gold), sensors (purple), places (blue); link types (observed, at place, repeat, notes, EVRUS, devices). Floating **map key** on the graph view.
- **Scan detail sheet** — full signal list per saved scan, with filters and optional photo/video on the scan.
- **Graph detail sheet** — tap signals, places, or **relationship lines** for context; scan nodes open the scan sheet.
- **Copy** icons on findings, risk, insights, and scan rows.

**EVRUS / EVRMORE** — local identity and P2P refs; optional EVRUS companion app; **Anchor graph** stays on-device.

### Connect (local mesh)

- **Messages** — X3DH + Double Ratchet (ChaCha20-Poly1305); export inbox as `.txt`
- **Voice mesh** — PCM over local mesh
- **Mesh radio** — LAN peers, encrypted session (port 28777, private IPs only)

Published by **Manticore Technologies, LLC**.

## Requirements

- Android **8.0+** (API 26+)
- Bluetooth and Wi-Fi enabled for full results
- **Location permission** for Wi-Fi scans and **GPS at scan time** (Android platform rules)
- Android 12+: `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`
- Android 13+: `NEARBY_WIFI_DEVICES` (when applicable)

**Network:** `INTERNET` is used for optional Ask model download and **map tile loading** on the Graph map (Carto CDN). Scan results are not sent to Signal Scoop servers.

## Publish on Google Play ($2)

Publishing requires **your** [Google Play Developer](https://play.google.com/console) account (**$25 one-time** registration).

1. **[docs/play-store/PUBLISHING.md](docs/play-store/PUBLISHING.md)** — pricing, listing, data safety.
2. Host **[docs/privacy-policy.html](docs/privacy-policy.html)** at a public HTTPS URL.
3. `./scripts/generate-release-keystore.sh` then `./scripts/build-play-bundle.sh` → `release/signal-scoop-play.aab`
4. Upload in Play Console — **Paid → $2.00**.

## Install the APK

| File | Notes |
|------|--------|
| [`release/signal-scoop-debug.apk`](release/signal-scoop-debug.apk) | **Recommended for sideloading** — debug-signed |
| [`release/signal-scoop-release.apk`](release/signal-scoop-release.apk) | Release APK (sign with your keystore for production) |

1. Copy the APK to the device.
2. Open it; allow **Install unknown apps** if prompted.
3. Grant permissions; tap **Scan nearby signals** on the **Scan** tab.

## Build from source

### Prerequisites

- JDK 17+
- Android SDK API 35 + Build-Tools 35
- `sdk.dir` in `android/local.properties` (see `android/local.properties.example`)

```bash
source android/env.sh    # if JAVA_HOME is unset (~/.local/jdk/jdk-17 or system JDK)
cd android
./gradlew assembleDebug
./gradlew assembleRelease
```

Outputs:

- Debug: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release: `android/app/build/outputs/apk/release/app-release.apk` (signed if `keystore.properties` exists)

Copy to `release/` for sideloading:

```bash
cp android/app/build/outputs/apk/debug/app-debug.apk release/signal-scoop-debug.apk
cp android/app/build/outputs/apk/release/app-release.apk release/signal-scoop-release.apk
```

## Project layout

```
signal-scoop/
├── android/                 # Kotlin + Jetpack Compose app
├── docs/                    # Privacy policy, Play Store copy, data safety
├── release/                 # Shipped APK / AAB artifacts
├── LICENSE                  # MIT (Manticore Technologies, LLC)
├── NOTICE.md                # Copyright and attribution
├── SECURITY.md              # Permissions, mesh, storage
├── seed.md                  # Original MVP notes
└── .beads/                  # Task tracking (Beads)
```

## Task tracking (Beads)

```bash
bd ready
bd show <id>
bd close <id>
bd prime    # full workflow context
```

## License

Copyright © 2026 **Manticore Technologies, LLC**. [MIT License](LICENSE).

## Security & privacy

See **[SECURITY.md](SECURITY.md)** and in-app **Privacy & security**.

| Control | Implementation |
|---------|----------------|
| No scan upload | History and graph stay in app-private storage |
| Local history | Room DB; rename/delete scans; backup disabled |
| Map tiles | HTTPS to Carto basemap only; no scan data in tile requests |
| User consent | Scan only after permissions + tap |
| Screen capture | `FLAG_SECURE` on sensitive screens |
| Release | R8 minify + shrink; verbose logs stripped |

## Safety & limitations

- Passive cameras/mics **without radio** are not reliably detected.
- Wi-Fi scans are **throttled by Android**.
- RSSI and risk scores are **heuristic**, not proof.

Use Signal Scoop as one input among many when reviewing your environment.
