package com.signalsoop.app.security

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory

/**
 * On-device defensive sentinel: interprets scan results as hacking/surveillance
 * indicators and recommends protective actions. Read-only — never attacks networks.
 */
object DefenseSentinel {
    data class SentinelAlert(
        val severity: Severity,
        val title: String,
        val detail: String,
        val action: String,
    )

    enum class Severity { INFO, WATCH, ALERT, CRITICAL }

    data class SentinelReport(
        val defenseScore: Int,
        val postureLabel: String,
        val alerts: List<SentinelAlert>,
        val playbook: List<String>,
    )

    fun analyze(findings: List<Finding>, risk: RiskSummary?): SentinelReport {
        val alerts = mutableListOf<SentinelAlert>()
        val radio =
            findings.filter {
                it.category in
                    setOf(
                        SignalCategory.BLE,
                        SignalCategory.WIFI,
                        SignalCategory.BLUETOOTH,
                    )
            }

        val unknownBle =
            radio.count {
                it.category == SignalCategory.BLE &&
                    (it.title.contains("Unknown", ignoreCase = true) ||
                        it.detail.contains("Unknown", ignoreCase = true))
            }
        if (unknownBle >= 2) {
            alerts +=
                SentinelAlert(
                    severity = if (unknownBle >= 5) Severity.CRITICAL else Severity.ALERT,
                    title = "Unidentified BLE transmitters",
                    detail = "$unknownBle unknown BLE devices are advertising nearby.",
                    action = "Move sensitive conversations offline; disable unused Bluetooth; scan again after leaving the area.",
                )
        }

        val hiddenWifi =
            radio.count {
                it.category == SignalCategory.WIFI &&
                    (it.title.contains("Hidden", ignoreCase = true) ||
                        it.detail.contains("Hidden", ignoreCase = true))
            }
        if (hiddenWifi > 0) {
            alerts +=
                SentinelAlert(
                    severity = Severity.WATCH,
                    title = "Hidden Wi-Fi presence",
                    detail = "$hiddenWifi hidden SSID access point(s) detected.",
                    action = "Avoid joining unknown networks; use mobile data for sensitive work.",
                )
        }

        val strong =
            radio.count { (it.signalStrength ?: -100) > -50 }
        if (strong >= 3) {
            alerts +=
                SentinelAlert(
                    severity = Severity.ALERT,
                    title = "Strong proximate radio footprint",
                    detail = "$strong signals exceed -50 dBm — devices are very close.",
                    action = "Assume active monitoring risk; enable airplane mode for high-sensitivity tasks.",
                )
        }

        val pairedUnknown =
            findings.count {
                it.category == SignalCategory.BLUETOOTH &&
                    it.title.contains("Unknown", ignoreCase = true)
            }
        if (pairedUnknown > 0) {
            alerts +=
                SentinelAlert(
                    severity = Severity.WATCH,
                    title = "Unnamed paired Bluetooth",
                    detail = "$pairedUnknown paired device(s) lack a friendly name.",
                    action = "Review Settings → Bluetooth; remove devices you do not recognize.",
                )
        }

        risk?.let { r ->
            if (r.score >= 70) {
                alerts +=
                    SentinelAlert(
                        severity = Severity.CRITICAL,
                        title = "High heuristic risk score",
                        detail = "Composite risk ${r.score}/100 (${r.level.label}).",
                        action = "Treat the environment as hostile for credential entry and sensitive calls.",
                    )
            }
        }

        if (alerts.isEmpty()) {
            alerts +=
                SentinelAlert(
                    severity = Severity.INFO,
                    title = "Baseline clear",
                    detail = "No strong hostile-radio patterns in this scan.",
                    action = "Re-scan after changing location to build history in the Graph tab.",
                )
        }

        var threatWeight = 0
        alerts.forEach { alert ->
            threatWeight +=
                when (alert.severity) {
                    Severity.CRITICAL -> 30
                    Severity.ALERT -> 18
                    Severity.WATCH -> 8
                    Severity.INFO -> 0
                }
        }
        val defenseScore = (100 - threatWeight).coerceIn(0, 100)
        val postureLabel =
            when {
                defenseScore >= 80 -> "Hardened"
                defenseScore >= 55 -> "Alert"
                defenseScore >= 35 -> "Exposed"
                else -> "High exposure"
            }

        val playbook =
            buildList {
                add("Signal Scoop is a defensive sentinel — passive survey only, no exploitation.")
                add("Export and delete History on shared devices; live results clear when you leave the app.")
                if (unknownBle > 0) add("Rotate or disable Bluetooth when not needed.")
                if (hiddenWifi > 0) add("Do not trust hidden Wi-Fi for sensitive traffic.")
            }

        return SentinelReport(
            defenseScore = defenseScore,
            postureLabel = postureLabel,
            alerts = alerts.sortedByDescending { it.severity.ordinal },
            playbook = playbook,
        )
    }
}
