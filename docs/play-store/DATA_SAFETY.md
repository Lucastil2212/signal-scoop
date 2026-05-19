# Google Play — Data safety form (Signal Scoop)

Use these answers when Play Console asks **Data safety**. Adjust if you change the app.

## Does your app collect or share user data?

**No** — for data sent to developer servers (there are no servers).

## Data types — device permissions vs “collection”

Play treats some on-device access as “collected” even if it never leaves the phone. Answer honestly:

| Data type | Collected? | Shared? | Ephemeral? | Required? | Purpose |
|-----------|------------|---------|------------|-----------|---------|
| **Location** (approximate) | **No** *or* **Yes, not shared** — if Play forces “collected” because of `ACCESS_FINE_LOCATION`, mark: collected on-device only, not shared, ephemeral, required for Wi-Fi/BLE scan | No | Yes (cleared when app stops) | Yes for scan | App functionality |
| **Device or other IDs** (Bluetooth/Wi-Fi MAC, BSSID) | Shown in UI only; **not transmitted** | No | Yes | Optional (only when user scans) | App functionality |

Recommended framing:

- **Data is processed ephemerally** — results are not stored long-term.
- **Data is not shared with third parties.**
- **Users can request deletion** — N/A for server data; uninstall app.
- **Encryption in transit** — N/A (no network).
- **Users can choose whether data is collected** — Yes; only when they tap Scan and grant permissions.

## Security practices

- Data encrypted in transit: **No data transmitted** (or N/A)
- Users can ask for data deletion: **Yes** (uninstall / no server retention)
- Committed to Play Families Policy: **No** (not a kids app)

## Account creation

**No** accounts.

## Ads

**No** ads.

## Precise location

We request **Fine location** permission because Android ties Wi-Fi/BLE scans to it on many OEMs. The app does **not** display a map or upload GPS coordinates.

If the form allows a declaration: **“Location permission used solely for Wi-Fi/Bluetooth scanning per Android requirements; location is not sold or uploaded.”**
