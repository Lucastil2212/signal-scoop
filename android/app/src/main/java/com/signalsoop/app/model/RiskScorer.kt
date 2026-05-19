package com.signalsoop.app.model

object RiskScorer {
    fun summarize(findings: List<Finding>): RiskSummary {
        val score = findings.sumOf { it.riskPoints }.coerceAtMost(100)
        val highlights = buildList {
            val unknownBle = findings.count {
                it.category == SignalCategory.BLE &&
                    (it.title.contains("Unknown", ignoreCase = true) || it.detail.contains("Unknown", ignoreCase = true))
            }
            if (unknownBle >= 3) add("$unknownBle unknown BLE devices")

            val hiddenWifi = findings.count {
                it.category == SignalCategory.WIFI &&
                    (it.title.contains("Hidden", ignoreCase = true) || it.detail.contains("Hidden", ignoreCase = true))
            }
            if (hiddenWifi > 0) add("$hiddenWifi hidden Wi-Fi network(s)")

            val strongSignals = findings.count { (it.signalStrength ?: -100) > -50 }
            if (strongSignals >= 2) add("$strongSignals very strong nearby signals")

            val pairedUnknown = findings.count {
                it.category == SignalCategory.BLUETOOTH && it.title.contains("Unknown", ignoreCase = true)
            }
            if (pairedUnknown > 0) add("$pairedUnknown paired device(s) without a name")
        }

        val level = when {
            score >= 70 -> RiskLevel.HIGH
            score >= 45 -> RiskLevel.ELEVATED
            score >= 20 -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }

        return RiskSummary(
            level = level,
            score = score,
            highlights = highlights.ifEmpty {
                listOf("No unusual patterns detected in this scan.")
            },
        )
    }
}
