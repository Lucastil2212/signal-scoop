package com.signalsoop.app.assistant

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory

/** Structured facts derived from a scan — used for reliable local answers and compact LLM context. */
data class ScanAnalytics(
    val totalFindings: Int,
    val bleCount: Int,
    val wifiCount: Int,
    val bluetoothCount: Int,
    val sensorCount: Int,
    val nfcCount: Int,
    val systemCount: Int,
    val unknownBleCount: Int,
    val hiddenWifiCount: Int,
    val strongSignalCount: Int,
    val riskSummary: RiskSummary?,
    val unknownBle: List<NotableFinding>,
    val hiddenWifi: List<NotableFinding>,
    val strongestRadio: List<NotableFinding>,
    val topRisk: List<NotableFinding>,
    val pairedBluetooth: List<NotableFinding>,
    val bleDevices: List<NotableFinding>,
    val wifiNetworks: List<NotableFinding>,
    val nfcStatus: String?,
) {
    data class NotableFinding(
        val category: SignalCategory,
        val title: String,
        val detail: String,
        val rssi: Int?,
        val riskPoints: Int,
    )

    fun formatDigest(): String = buildString {
        appendLine("Counts: ${bleCount} BLE, ${wifiCount} Wi-Fi, ${bluetoothCount} paired BT, ${sensorCount} sensors")
        riskSummary?.let { r ->
            appendLine("Risk: ${r.score}/100 (${r.level.label}) — ${r.highlights.take(3).joinToString("; ")}")
        }
        if (unknownBleCount > 0) appendLine("Unknown BLE: $unknownBleCount")
        if (hiddenWifiCount > 0) appendLine("Hidden Wi-Fi: $hiddenWifiCount")
        if (strongSignalCount > 0) appendLine("Very strong RSSI (>-50 dBm): $strongSignalCount")
        nfcStatus?.let { appendLine("NFC: $it") }
        if (strongestRadio.isNotEmpty()) {
            append("Strongest: ")
            appendLine(strongestRadio.take(5).joinToString { it.shortLabel() })
        }
        if (topRisk.isNotEmpty()) {
            append("Top risk: ")
            appendLine(topRisk.take(5).joinToString { it.shortLabel() })
        }
    }

    private fun NotableFinding.shortLabel(): String {
        val rssi = rssi?.let { " ${it}dBm" }.orEmpty()
        return "${category.label} ${title}$rssi"
    }

    companion object {
        fun from(findings: List<Finding>, riskSummary: RiskSummary?): ScanAnalytics {
            val ble = findings.filter { it.category == SignalCategory.BLE }
            val wifi = findings.filter { it.category == SignalCategory.WIFI }
            val bt = findings.filter { it.category == SignalCategory.BLUETOOTH }
            val sensors = findings.filter { it.category == SignalCategory.SENSORS }
            val nfc = findings.filter { it.category == SignalCategory.NFC }
            val system = findings.filter { it.category == SignalCategory.SYSTEM }

            val unknownBle =
                ble.filter { isUnknown(it) }.map { it.toNotable() }

            val hiddenWifi =
                wifi.filter { isHidden(it) }.map { it.toNotable() }

            val radio =
                (ble + wifi + bt + nfc)
                    .map { it.toNotable() }
                    .sortedWith(
                        compareByDescending<NotableFinding> { it.riskPoints }
                            .thenByDescending { it.rssi ?: Int.MIN_VALUE },
                    )

            val strongest =
                radio
                    .filter { (it.rssi ?: -100) > -55 }
                    .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
                    .take(8)

            val topRisk =
                findings
                    .filter { it.category != SignalCategory.SENSORS && it.category != SignalCategory.SYSTEM }
                    .sortedByDescending { it.riskPoints }
                    .take(8)
                    .map { it.toNotable() }

            val nfcLine =
                nfc.firstOrNull()?.let { "${it.title} — ${it.detail}" }

            val bleSorted =
                ble
                    .sortedByDescending { it.signalStrength ?: Int.MIN_VALUE }
                    .map { it.toNotable() }
            val wifiSorted =
                wifi
                    .sortedByDescending { it.signalStrength ?: Int.MIN_VALUE }
                    .map { it.toNotable() }

            return ScanAnalytics(
                totalFindings = findings.size,
                bleCount = ble.size,
                wifiCount = wifi.size,
                bluetoothCount = bt.size,
                sensorCount = sensors.size,
                nfcCount = nfc.size,
                systemCount = system.size,
                unknownBleCount = unknownBle.size,
                hiddenWifiCount = hiddenWifi.size,
                strongSignalCount = findings.count { (it.signalStrength ?: -100) > -50 },
                riskSummary = riskSummary,
                unknownBle = unknownBle,
                hiddenWifi = hiddenWifi,
                strongestRadio = strongest,
                topRisk = topRisk,
                pairedBluetooth = bt.map { it.toNotable() },
                bleDevices = bleSorted,
                wifiNetworks = wifiSorted,
                nfcStatus = nfcLine,
            )
        }

        private fun isUnknown(f: Finding): Boolean =
            f.title.contains("Unknown", ignoreCase = true) ||
                f.detail.contains("Unknown", ignoreCase = true)

        private fun isHidden(f: Finding): Boolean =
            f.title.contains("Hidden", ignoreCase = true) ||
                f.detail.contains("Hidden", ignoreCase = true) ||
                f.title.isBlank()

        private fun Finding.toNotable() =
            NotableFinding(
                category = category,
                title = title,
                detail = detail,
                rssi = signalStrength,
                riskPoints = riskPoints,
            )
    }
}
