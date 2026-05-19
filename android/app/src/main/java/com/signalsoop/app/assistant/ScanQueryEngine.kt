package com.signalsoop.app.assistant

/**
 * Answers common questions directly from [ScanAnalytics] — no LLM required.
 */
object ScanQueryEngine {
    fun tryAnswer(intent: QueryIntent, analytics: ScanAnalytics): String? =
        when (intent) {
            QueryIntent.HELP -> helpText()
            QueryIntent.SUMMARY -> buildSummary(analytics)
            QueryIntent.ANALYZE -> buildAnalysis(analytics)
            QueryIntent.COUNT_BLE -> countLine("BLE devices", analytics.bleCount)
            QueryIntent.COUNT_WIFI -> countLine("Wi-Fi networks", analytics.wifiCount)
            QueryIntent.COUNT_BLUETOOTH -> countLine("paired Bluetooth devices", analytics.bluetoothCount)
            QueryIntent.LIST_UNKNOWN_BLE -> listNotable("Unknown BLE devices", analytics.unknownBle, analytics.bleCount)
            QueryIntent.LIST_HIDDEN_WIFI -> listNotable("Hidden Wi-Fi networks", analytics.hiddenWifi, analytics.wifiCount)
            QueryIntent.LIST_STRONGEST -> listNotable("Strongest nearby signals", analytics.strongestRadio, null)
            QueryIntent.LIST_PAIRED -> listNotable("Paired Bluetooth devices", analytics.pairedBluetooth, analytics.bluetoothCount)
            QueryIntent.LIST_BLE -> listNotable("BLE devices", analytics.bleDevices, analytics.bleCount)
            QueryIntent.LIST_WIFI -> listNotable("Wi-Fi networks", analytics.wifiNetworks, analytics.wifiCount)
            QueryIntent.NFC -> analytics.nfcStatus?.let { "NFC: $it" } ?: "NFC: no status in this scan."
            QueryIntent.GENERAL -> null
        }

    fun fallbackSummary(analytics: ScanAnalytics): String = buildSummary(analytics)

    private fun helpText(): String =
        """
        |You can ask (no model required for most):
        |• Summarize / analyze the scan
        |• How many BLE or Wi-Fi devices?
        |• List unknown BLE or hidden Wi-Fi
        |• Strongest signals / paired Bluetooth / NFC
        |
        |Load a .task model for free-form follow-up questions.
        """.trimMargin()

    private fun buildSummary(analytics: ScanAnalytics): String = buildString {
        appendLine("Scan summary (${analytics.totalFindings} findings)")
        appendLine()
        appendLine(analytics.formatDigest().trimEnd())
        appendLine()
        appendLine(
            "Radio environment: ${analytics.bleCount} BLE · ${analytics.wifiCount} Wi-Fi · " +
                "${analytics.bluetoothCount} paired BT",
        )
        if (analytics.sensorCount > 0) {
            appendLine("Also reported ${analytics.sensorCount} on-phone sensors (not nearby transmitters).")
        }
    }

    private fun buildAnalysis(analytics: ScanAnalytics): String = buildString {
        val risk = analytics.riskSummary
        appendLine("Scan analysis")
        appendLine()
        if (risk != null) {
            appendLine("Risk score ${risk.score}/100 (${risk.level.label}).")
            appendLine(risk.level.description)
            appendLine()
            appendLine("Notable patterns:")
            risk.highlights.forEach { appendLine("• $it") }
        } else {
            appendLine("No risk score was computed for this scan.")
        }
        appendLine()
        if (analytics.unknownBleCount >= 3) {
            appendLine("• Several unnamed BLE devices — could be earbuds, trackers, or IoT; not proof of surveillance.")
        }
        if (analytics.hiddenWifiCount > 0) {
            appendLine("• Hidden Wi-Fi SSIDs present — sometimes used by hotspots or corporate APs.")
        }
        if (analytics.strongSignalCount >= 2) {
            appendLine("• Multiple very strong signals (RSSI > -50 dBm) — something is physically close.")
        }
        if (analytics.unknownBleCount == 0 && analytics.hiddenWifiCount == 0 && analytics.strongSignalCount < 2) {
            appendLine("• Nothing strongly unusual in this scan; still only a passive radio survey.")
        }
        appendLine()
        appendLine("Note: heuristic only — not forensic proof. Passive devices without radio are not detected.")
        if (analytics.topRisk.isNotEmpty()) {
            appendLine()
            appendLine("Highest-weight findings:")
            analytics.topRisk.take(5).forEach { appendLine("• ${it.formatBullet()}") }
        }
    }

    private fun countLine(label: String, count: Int): String =
        "This scan found $count $label."

    private fun listNotable(
        heading: String,
        items: List<ScanAnalytics.NotableFinding>,
        totalInCategory: Int?,
    ): String {
        if (items.isEmpty()) {
            val extra = totalInCategory?.let { " ($it total in category)" }.orEmpty()
            return "No $heading recorded$extra."
        }
        return buildString {
            appendLine("$heading (${items.size})")
            items.take(12).forEach { appendLine("• ${it.formatBullet()}") }
            if (items.size > 12) appendLine("• … and ${items.size - 12} more")
        }
    }

    private fun ScanAnalytics.NotableFinding.formatBullet(): String {
        val rssi = rssi?.let { " · $it dBm" }.orEmpty()
        return "[$category] $title — $detail$rssi"
    }
}
