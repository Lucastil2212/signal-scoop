package com.signalsoop.app.security

/**
 * Declarative guarantees enforced by architecture and manifest (see SECURITY.md).
 */
object ScanPolicy {
    const val COPYRIGHT_HOLDER = "Manticore Technologies, LLC"
    const val LICENSE_NOTICE =
        "© 2026 Manticore Technologies, LLC. Signal Scoop is licensed under the MIT License."

    const val MAX_BLE_SCAN_MS = 8_000L
    const val MAX_WIFI_WAIT_MS = 12_000L

    val privacyBullets = listOf(
        "Defense sentinel: passive RF survey for surveillance/hacking indicators — never offensive tooling.",
        "No accounts or cloud analytics. Scan results never leave your phone.",
        "Optional HTTPS download only fetches a local LLM checkpoint (.task) when you tap Download.",
        "Ask uses an on-device model; answers use your current scan and local history only.",
        "Scans run only when you tap Scan, in the foreground.",
        "Live results clear when you leave the app; saved History stays in on-device storage only.",
        "GPS fixes use the phone’s native location stack — never sent off-device.",
        "We do not connect to, pair with, or attack nearby devices.",
        "Screenshots and recent-apps preview are blocked on the results screen.",
        "Connect mesh: LAN-only peers, E2EE text, rate/size limits; radio stops when the app backgrounds.",
        "Mesh device ID stored in encrypted prefs; graph WebView cannot load external URLs.",
    )

    val permissionBullets = listOf(
        "Bluetooth — discover BLE and list paired devices.",
        "Wi-Fi — list nearby access points (Android may require location).",
        "Location — OS requirement for Wi-Fi/BLE; also used for GPS coordinates when you scan.",
    )
}
