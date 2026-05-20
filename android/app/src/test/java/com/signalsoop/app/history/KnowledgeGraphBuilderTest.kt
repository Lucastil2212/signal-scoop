package com.signalsoop.app.history

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeGraphBuilderTest {
    @Test
    fun includesNfcSensorsAndPairedBluetooth() {
        val snapshot =
            ScanSnapshot(
                id = "s1",
                name = "Test",
                scannedAtEpochMs = 1L,
                geoFix = null,
                findings =
                    listOf(
                        Finding("ble-aa:bb", SignalCategory.BLE, "BLE", "detail"),
                        Finding("wifi-11:22", SignalCategory.WIFI, "Wi-Fi", "detail"),
                        Finding("paired-00:11", SignalCategory.BLUETOOTH, "Phone", "detail"),
                        Finding("nfc-status", SignalCategory.NFC, "NFC ready", "enabled"),
                        Finding("sensor-1-Accel", SignalCategory.SENSORS, "Accelerometer", "vendor"),
                        Finding("ble-empty", SignalCategory.BLE, "Empty", "none"),
                    ),
                riskSummary = null,
            )

        val keys = KnowledgeGraphBuilder.signalKeysFrom(snapshot.findings)
        assertEquals(5, keys.size)
        assertTrue(keys.containsKey("aa:bb"))
        assertTrue(keys.containsKey("11:22"))
        assertTrue(keys.containsKey("00:11"))
        assertTrue(keys.containsKey("nfc-status"))
        assertTrue(keys.containsKey("1-Accel"))
        assertFalse(keys.containsKey("ble-empty"))
    }

    @Test
    fun buildForScan_emitsRichMetadata() {
        val finding = Finding("ble-de:ad", SignalCategory.BLE, "Tracker", "RSSI strong", signalStrength = -48, riskPoints = 3)
        val snapshot =
            ScanSnapshot(
                id = "s2",
                name = "Scan 2",
                scannedAtEpochMs = 2L,
                geoFix = null,
                findings = listOf(finding),
                riskSummary = null,
            )
        val delta = KnowledgeGraphBuilder.buildForScan(snapshot, emptyMap())
        val signalNode = delta.nodes.find { it.nodeType == KnowledgeGraphBuilder.NODE_SIGNAL }
        assertTrue(signalNode != null)
        assertTrue(signalNode!!.label.contains("BLE"))
        assertTrue(delta.edges.any { it.relation == KnowledgeGraphBuilder.REL_OBSERVED })
    }
}
