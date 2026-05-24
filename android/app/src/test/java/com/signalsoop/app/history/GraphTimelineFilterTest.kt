package com.signalsoop.app.history

import androidx.compose.ui.graphics.Color
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphTimelineFilterTest {
    @Test
    fun slice_forSecondScan_includesStableSensorsNfcAndBluetooth() {
        val scan1Id = "scan-1"
        val scan2Id = "scan-2"
        val scan1Node = KnowledgeGraphBuilder.scanNodeId(scan1Id)
        val scan2Node = KnowledgeGraphBuilder.scanNodeId(scan2Id)
        val bleNode = "signal:aa:bb:cc:dd:ee:01"
        val wifiNode = "signal:11:22:33:44:55:66"
        val sensorNode = "signal:1-Accelerometer"
        val nfcNode = "signal:nfc-status"
        val pairedNode = "signal:00:11:22:33:44:55"

        val scan2Findings =
            listOf(
                Finding("ble-aa:bb:cc:dd:ee:01", SignalCategory.BLE, "Tag", "detail"),
                Finding("wifi-11:22:33:44:55:66", SignalCategory.WIFI, "AP", "detail"),
                Finding("sensor-1-Accelerometer", SignalCategory.SENSORS, "Accelerometer", "detail"),
                Finding("nfc-status", SignalCategory.NFC, "NFC ready", "enabled"),
                Finding("paired-00:11:22:33:44:55", SignalCategory.BLUETOOTH, "Headphones", "detail"),
            )

        val visualization =
            GraphVisualization(
                nodes =
                    listOf(
                        graphNode(scan2Node, KnowledgeGraphBuilder.NODE_SCAN, "Scan 2"),
                        graphNode(bleNode, KnowledgeGraphBuilder.NODE_SIGNAL, "BLE", "BLE"),
                        graphNode(wifiNode, KnowledgeGraphBuilder.NODE_SIGNAL, "Wi-Fi", "WIFI"),
                        graphNode(sensorNode, KnowledgeGraphBuilder.NODE_SIGNAL, "Accelerometer", "SENSORS"),
                        graphNode(nfcNode, KnowledgeGraphBuilder.NODE_SIGNAL, "NFC", "NFC"),
                        graphNode(pairedNode, KnowledgeGraphBuilder.NODE_SIGNAL, "Headphones", "BLUETOOTH"),
                    ),
                links =
                    listOf(
                        graphLink(scan2Node, bleNode, KnowledgeGraphBuilder.REL_OBSERVED, scan2Id),
                        graphLink(scan2Node, wifiNode, KnowledgeGraphBuilder.REL_OBSERVED, scan2Id),
                        // Stable signals only linked in scan 1 — findings still define scan 2 spectrum.
                        graphLink(scan1Node, sensorNode, KnowledgeGraphBuilder.REL_OBSERVED, scan1Id),
                        graphLink(scan1Node, nfcNode, KnowledgeGraphBuilder.REL_OBSERVED, scan1Id),
                        graphLink(scan1Node, pairedNode, KnowledgeGraphBuilder.REL_OBSERVED, scan1Id),
                    ),
                timelineScans =
                    listOf(
                        GraphTimelineScan(
                            scanId = scan2Id,
                            scanNodeId = scan2Node,
                            label = "Scan 2",
                            epochMs = 2L,
                            lat = 37.78,
                            lon = -122.41,
                            color = Color.Green,
                            signalCount = 5,
                            signalNodeIds =
                                KnowledgeGraphBuilder.signalKeysFrom(scan2Findings).keys
                                    .map { "signal:$it" }
                                    .toSet(),
                        ),
                    ),
                timeMinMs = 1L,
                timeMaxMs = 2L,
                usesGps = true,
                nodeCount = 6,
                linkCount = 5,
            )

        val slice = GraphTimelineFilter.slice(visualization, scan2Id)
        val visibleIds = slice.nodes.map { it.id }.toSet()

        assertTrue(visibleIds.contains(scan2Node))
        assertTrue(visibleIds.contains(bleNode))
        assertTrue(visibleIds.contains(wifiNode))
        assertTrue(visibleIds.contains(sensorNode))
        assertTrue(visibleIds.contains(nfcNode))
        assertTrue(visibleIds.contains(pairedNode))
        assertEquals(6, visibleIds.size)
    }

    @Test
    fun buildForScan_secondScan_linksRepeatFromScanNode() {
        val sharedSensor = Finding("sensor-1-Gyro", SignalCategory.SENSORS, "Gyro", "detail")
        val scan1 =
            ScanSnapshot(
                id = "s1",
                name = "One",
                scannedAtEpochMs = 1L,
                geoFix = null,
                findings = listOf(sharedSensor),
                riskSummary = null,
            )
        val scan2 =
            ScanSnapshot(
                id = "s2",
                name = "Two",
                scannedAtEpochMs = 2L,
                geoFix = null,
                findings = listOf(sharedSensor, Finding("ble-de:ad", SignalCategory.BLE, "BLE", "detail")),
                riskSummary = null,
            )

        val first = KnowledgeGraphBuilder.buildForScan(scan1, emptyMap())
        val prior = KnowledgeGraphBuilder.signalKeysFrom(scan1.findings).keys.associateWith { 1 }
        val second = KnowledgeGraphBuilder.buildForScan(scan2, prior)

        val sensorKey = "1-Gyro"
        assertTrue(
            second.edges.any {
                it.relation == KnowledgeGraphBuilder.REL_OBSERVED &&
                    it.fromNodeId == KnowledgeGraphBuilder.scanNodeId("s2") &&
                    it.toNodeId == "signal:$signalKey"
            },
        )
        assertTrue(
            second.edges.any {
                it.relation == KnowledgeGraphBuilder.REL_REPEAT &&
                    it.fromNodeId == KnowledgeGraphBuilder.scanNodeId("s2") &&
                    it.toNodeId == "signal:$signalKey"
            },
        )
        assertEquals(2, second.edges.count { it.relation == KnowledgeGraphBuilder.REL_OBSERVED })
    }

    private fun graphNode(
        id: String,
        type: String,
        label: String,
        category: String? = null,
    ): GraphVisNode =
        GraphVisNode(
            id = id,
            label = label,
            rawLabel = label,
            type = type,
            color = Color.White,
            layoutX = 0f,
            layoutY = 0f,
            signalCategory = category,
        )

    private fun graphLink(
        sourceId: String,
        targetId: String,
        relation: String,
        scanId: String,
    ): GraphVisLink =
        GraphVisLink(
            sourceId = sourceId,
            targetId = targetId,
            relation = relation,
            scanId = scanId,
        )
}
