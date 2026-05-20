# Google Play — Data safety form (Signal Scoop)

Use when Play Console asks **Data safety**. Version **1.6.2-beta**.

## Does your app collect or share user data with the developer?

**No** — there are no developer servers receiving scan or graph data.

## Data types

| Data type | Collected? | Shared? | Purpose | Notes |
|-----------|------------|---------|---------|-------|
| **Location** (approximate / precise) | **Yes, on device** | **No** | App functionality | GPS saved with scans when enabled; not uploaded |
| **Device or other IDs** (MAC, BSSID) | **Yes, on device** | **No** | App functionality | Shown in UI and local DB only |
| **Photos / videos** (optional) | **Yes, on device** | **No** | User attaches to scans/signals | App-private files |
| **Other user-generated content** | **Yes, on device** | **No** | Notes, pet names, mesh messages | Local only |

Framing:

- **Not shared with third parties** (except map tile CDN receives standard tile URLs — no scan content).
- **Users can delete** — delete scans in app or uninstall.
- **Encryption in transit** — HTTPS for tiles/model only; no scan upload channel.
- **Collection is optional** — only when user scans, saves, or uses graph features.

## Precise location

Fine location is used for **Android Wi-Fi/BLE requirements** and **optional GPS stored with saved scans** on-device. The Knowledge map displays your scan places locally; coordinates are **not** sent to Manticore.

Declaration example: *“Location used for scan-time GPS and OS wireless scan rules; stored locally; not sold or uploaded to developer.”*

## Security practices

- Data encrypted in transit: **N/A for scan data** (not transmitted); HTTPS for optional model/tiles
- Deletion: **Yes** (in-app delete / uninstall)
- Play Families: **No**

## Account / ads

**No** accounts. **No** ads.

## Internet permission

Used for optional Ask model download and map basemap tiles — **not** for uploading surveys.
