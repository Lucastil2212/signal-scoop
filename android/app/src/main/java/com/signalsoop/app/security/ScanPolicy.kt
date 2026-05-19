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
        "No accounts or cloud analytics. Scan results never leave your phone.",
        "Optional HTTPS download only fetches a local LLM checkpoint (.task) when you tap Download.",
        "Ask uses an on-device model; answers are grounded in your current scan session only.",
        "Scans run only when you tap Scan, in the foreground.",
        "Results stay in memory and are cleared when you leave the app.",
        "We do not connect to, pair with, or attack nearby devices.",
        "Screenshots and recent-apps preview are blocked on the results screen.",
    )

    val permissionBullets = listOf(
        "Bluetooth — discover BLE and list paired devices.",
        "Wi-Fi — list nearby access points (Android may require location).",
        "Location — required by the OS for Wi-Fi/BLE on many phones, not used for GPS tracking.",
    )
}
