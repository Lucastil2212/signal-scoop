# Security policy

**Publisher:** Manticore Technologies, LLC  
**Product:** Signal Scoop (`1.6.2-beta`)

Signal Scoop is a **local, read-only radio survey** and **defensive hacking sentinel**. It flags hostile-radio patterns and recommends protective actions — never offensive tooling.

## Scope (what we do)

- List **BLE**, **Wi-Fi scan results**, **paired Bluetooth**, **NFC**, and **on-device sensors** with minimal permissions.
- Run scans only when you tap **Scan nearby signals** (foreground).
- Capture **GPS** at scan time when location is enabled; store with saved scans locally.
- **Save scans** in app-private Room DB (findings, risk, coordinates, user-visible name).
- Build a **local knowledge graph** (scans, signals, places, links) for the Graph tab — not transmitted to Manticore servers.
- **Defense sentinel** after each scan (heuristic alerts; not forensic proof).
- Live session UI clears sensitive state when you leave the app; **saved History** remains until you delete it.

## Out of scope (what we never do)

- No accounts, analytics SDKs, ads, or crash reporters that phone home with scan payloads.
- No scan/history upload to developer-operated backends.
- No connecting to, pairing with, or deauthenticating remote devices.
- No exploitation or bypassing Android security controls.
- No background scanning when the app is not in use for survey features.

## Network use

| Use | Data sent |
|-----|-----------|
| Optional **Ask** model download | HTTPS to URL you trigger; `.task` checkpoint only |
| **Knowledge map** basemap tiles | HTTPS tile requests to Carto CDN (map display only; no scan content) |

Release builds block cleartext except as configured in `networkSecurityConfig`.

## Storage

- **History / graph:** App-private Room (`signal_scoop_scan_history.db`); not SQLCipher — protect device with a lock screen.
- **Ask model path:** Encrypted prefs for optional LiteRT checkpoint location.
- **Knowledge graph UI:** Native **OSMDroid** map or **Compose Canvas** layout — no WebView graph viewer.

## Permissions

| Permission | Why |
|------------|-----|
| `BLUETOOTH_SCAN` / legacy `BLUETOOTH` | BLE discovery |
| `BLUETOOTH_CONNECT` | Paired device names (API 31+) |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | Wi-Fi scan results |
| `ACCESS_FINE_LOCATION` | Android Wi-Fi/BLE rules; GPS coordinates at scan time |
| `NEARBY_WIFI_DEVICES` | Wi-Fi on Android 13+ where applicable |
| `INTERNET` | Optional model download; map tile HTTPS |

`neverForLocation` is used on Bluetooth/Wi-Fi permissions where supported; GPS uses the location stack separately.

## Data handling

- Rename or delete scans from **Graph → Scans**.
- **Backup / cloud transfer:** Disabled (`allowBackup=false`).
- **Screenshots:** `FLAG_SECURE` in release builds (disabled in debug for store assets and development).
- **Logging:** Release builds strip verbose logs; scan payloads are not written to logcat in release.

## Reporting vulnerabilities

Open a GitHub issue with reproduction steps and affected version. Do not post live exploit details publicly before a fix is available.

## Limitations

Risk scores are **heuristics**. Passive cameras or microphones without radio emissions may not be detected. Android may throttle Wi-Fi scans.

See [README.md](README.md) and in-app **Privacy & security**.
