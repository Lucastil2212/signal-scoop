# Security policy

**Publisher:** Manticore Technologies, LLC  
**Product:** Signal Scoop

Signal Scoop is designed as a **local, read-only radio survey** on your own phone. This document describes how we handle permissions, data, and threat boundaries.

## Scope (what we do)

- Request the minimum Android permissions needed to list **BLE advertisements**, **Wi-Fi scan results**, **paired Bluetooth devices**, **NFC availability**, and **on-device sensors**.
- Run scans only when you tap **Scan** and only while the app is in the foreground.
- Keep results in **memory** for the current session (ViewModel). We do not write scan results to disk or send them off-device.

## Out of scope (what we never do)

- No user accounts, analytics SDKs, ads, or crash reporters that phone home.
- No scan data transmitted over the network. The **Ask** tab runs a **local** LiteRT/MediaPipe model on your phone.
- `INTERNET` is used **only** when you explicitly download a `.task` model checkpoint (HTTPS). You can instead import a model via **Pick model** with no download.
- Release builds block cleartext via `networkSecurityConfig`.
- No connecting to, pairing with, or deauthenticating remote devices.
- No exploitation, fingerprinting of private devices beyond what the OS exposes to apps, or bypassing Android security controls.
- No background scanning; scans stop when you leave the app.

## Permissions

| Permission | Why |
|------------|-----|
| `BLUETOOTH_SCAN` / legacy `BLUETOOTH` | Discover nearby BLE devices (API-level split). |
| `BLUETOOTH_CONNECT` | Read paired device names (API 31+). |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | Trigger OS Wi-Fi scan and read results. |
| `ACCESS_FINE_LOCATION` | Required by Android for Wi-Fi/BLE on many OEMs (even when not used for GPS). |
| `NEARBY_WIFI_DEVICES` | Wi-Fi scan on Android 13+ without tying scan to location when possible. |

`BLUETOOTH_SCAN` and `NEARBY_WIFI_DEVICES` use `neverForLocation` where supported so we do not use Bluetooth/Wi-Fi APIs for positioning.

## Data handling

- **Backup / cloud transfer:** Disabled (`allowBackup=false`, empty backup rules).
- **Screenshots:** The main screen uses `FLAG_SECURE` so recent-apps and screenshots do not capture MAC addresses and SSIDs by default.
- **App switcher:** Sensitive UI is cleared when the app is stopped (not merely paused).
- **Logging:** Release builds strip verbose logs; scan payloads are not written to logcat in release.

## Reporting vulnerabilities

If you believe you found a security issue in this project, please open a GitHub issue with reproduction steps and affected version. Do not post live exploit details publicly before a fix is available.

## Limitations

Risk scores are **heuristics**, not forensic conclusions. Passive cameras or microphones that do not emit radio traffic cannot be detected. Android may throttle Wi-Fi scans.

See also [README.md](README.md) and in-app **Privacy & security**.
