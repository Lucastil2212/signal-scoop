package com.signalsoop.app.history

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory

data class ScanFindingCounts(
    val total: Int,
    val ble: Int,
    val wifi: Int,
    val bluetooth: Int,
    val nfc: Int,
    val sensors: Int,
    val system: Int,
) {
    fun summaryLine(): String =
        buildList {
            if (ble > 0) add("$ble BLE")
            if (wifi > 0) add("$wifi Wi-Fi")
            if (bluetooth > 0) add("$bluetooth Bluetooth")
            if (nfc > 0) add("$nfc NFC")
            if (sensors > 0) add("$sensors sensors")
            if (system > 0) add("$system status")
        }.joinToString(" · ").ifBlank { "0 signals" }

    companion object {
        fun from(findings: List<Finding>): ScanFindingCounts {
            val displayable = KnowledgeGraphBuilder.displayableFindings(findings)
            return ScanFindingCounts(
                total = displayable.size,
                ble = displayable.count { it.category == SignalCategory.BLE },
                wifi = displayable.count { it.category == SignalCategory.WIFI },
                bluetooth = displayable.count { it.category == SignalCategory.BLUETOOTH },
                nfc = displayable.count { it.category == SignalCategory.NFC },
                sensors = displayable.count { it.category == SignalCategory.SENSORS },
                system = findings.count { it.category == SignalCategory.SYSTEM },
            )
        }
    }
}
