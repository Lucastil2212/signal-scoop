package com.signalsoop.app.history

import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import org.json.JSONObject

/**
 * Builds local knowledge-graph nodes and edges when a scan is saved.
 * Radio signals (BLE, Wi-Fi, Bluetooth), NFC, and on-phone sensors link across scans;
 * places cluster by rounded coordinates.
 */
object KnowledgeGraphBuilder {
    const val NODE_SCAN = "SCAN"
    const val NODE_PLACE = "PLACE"
    const val NODE_SIGNAL = "SIGNAL"

    const val REL_AT_PLACE = "AT_PLACE"
    const val REL_OBSERVED = "OBSERVED"
    const val REL_REPEAT = "REPEAT"

    private val graphExcludedIds =
        setOf(
            "permissions-missing",
            "ble-permission",
            "ble-unsupported",
            "ble-disabled",
            "ble-no-scanner",
            "ble-empty",
            "ble-error",
            "wifi-permission",
            "wifi-location-off",
            "wifi-disabled",
            "wifi-empty",
            "bt-permission",
            "bt-unsupported",
            "bt-disabled",
            "bt-none-paired",
            "scan-complete",
        )

    data class GraphDelta(
        val nodes: List<GraphNodeEntity>,
        val edges: List<GraphEdgeEntity>,
    )

    fun buildForScan(
        snapshot: ScanSnapshot,
        priorSignalObservationCounts: Map<String, Int>,
    ): GraphDelta {
        val nodes = mutableListOf<GraphNodeEntity>()
        val edges = mutableListOf<GraphEdgeEntity>()
        val scanNodeId = scanNodeId(snapshot.id)

        val scanMeta =
            JSONObject().apply {
                put("scannedAt", snapshot.scannedAtEpochMs)
                put("riskScore", snapshot.riskSummary?.score ?: JSONObject.NULL)
                snapshot.geoFix?.let { fix ->
                    put("lat", fix.latitude)
                    put("lon", fix.longitude)
                }
            }
        nodes +=
            GraphNodeEntity(
                id = scanNodeId,
                nodeType = NODE_SCAN,
                label = snapshot.name,
                metadataJson = scanMeta.toString(),
            )

        val placeKey = snapshot.geoFix?.let { placeKeyFor(it.latitude, it.longitude) }
        if (placeKey != null && snapshot.geoFix != null) {
            val placeNodeId = "place:$placeKey"
            nodes +=
                GraphNodeEntity(
                    id = placeNodeId,
                    nodeType = NODE_PLACE,
                    label = "Near ${snapshot.geoFix.formatCoordinates()}",
                    metadataJson =
                        JSONObject()
                            .apply {
                                put("lat", snapshot.geoFix.latitude)
                                put("lon", snapshot.geoFix.longitude)
                            }
                            .toString(),
                )
            edges +=
                GraphEdgeEntity(
                    fromNodeId = scanNodeId,
                    toNodeId = placeNodeId,
                    relation = REL_AT_PLACE,
                    scanId = snapshot.id,
                    weight = 1,
                )
        }

        val signalKeys = signalKeysFrom(snapshot.findings)
        for ((signalKey, finding) in signalKeys) {
            val signalNodeId = "signal:$signalKey"
            nodes +=
                GraphNodeEntity(
                    id = signalNodeId,
                    nodeType = NODE_SIGNAL,
                    label = graphSignalLabel(finding),
                    metadataJson = signalMetadataJson(finding).toString(),
                )
            edges +=
                GraphEdgeEntity(
                    fromNodeId = scanNodeId,
                    toNodeId = signalNodeId,
                    relation = REL_OBSERVED,
                    scanId = snapshot.id,
                    weight = 1,
                )

            val priorCount = priorSignalObservationCounts[signalKey] ?: 0
            if (priorCount > 0) {
                edges +=
                    GraphEdgeEntity(
                        fromNodeId = signalNodeId,
                        toNodeId = signalNodeId,
                        relation = REL_REPEAT,
                        scanId = snapshot.id,
                        weight = priorCount + 1,
                    )
            }
        }

        return GraphDelta(
            nodes = nodes.distinctBy { it.id },
            edges = edges,
        )
    }

    fun scanNodeId(scanId: String): String = "scan:$scanId"

    fun placeKeyFor(latitude: Double, longitude: Double): String =
        String.format("%.4f,%.4f", latitude, longitude)

    fun signalKeysFrom(findings: List<Finding>): Map<String, Finding> {
        val map = linkedMapOf<String, Finding>()
        findings.forEach { finding ->
            if (!isGraphSignal(finding)) return@forEach
            val key = graphSignalKey(finding) ?: return@forEach
            map.putIfAbsent(key, finding)
        }
        return map
    }

    fun graphSignalKeyFrom(finding: Finding): String? = graphSignalKey(finding)

    fun isGraphSignal(finding: Finding): Boolean {
        if (finding.id in graphExcludedIds) return false
        return when (finding.category) {
            SignalCategory.BLE,
            SignalCategory.WIFI,
            SignalCategory.BLUETOOTH,
            SignalCategory.NFC,
            SignalCategory.SENSORS,
            -> graphSignalKey(finding) != null
            else -> false
        }
    }

    private fun graphSignalLabel(finding: Finding): String {
        val prefix = finding.category.label
        return if (finding.title.startsWith(prefix, ignoreCase = true)) {
            finding.title
        } else {
            "$prefix · ${finding.title}"
        }
    }

    private fun signalMetadataJson(finding: Finding): JSONObject =
        JSONObject().apply {
            put("category", finding.category.name)
            put("title", finding.title)
            put("detail", finding.detail)
            put("findingId", finding.id)
            finding.signalStrength?.let { put("rssi", it) }
            if (finding.riskPoints > 0) put("riskPoints", finding.riskPoints)
        }

    private fun graphSignalKey(finding: Finding): String? {
        if (finding.id.startsWith("ble-") && finding.id.length > 4) {
            return finding.id.removePrefix("ble-")
        }
        if (finding.id.startsWith("wifi-") && finding.id.length > 5) {
            return finding.id.removePrefix("wifi-")
        }
        if (finding.id.startsWith("paired-") && finding.id.length > 7) {
            return finding.id.removePrefix("paired-")
        }
        if (finding.id.startsWith("sensor-") && finding.id.length > 7) {
            return finding.id.removePrefix("sensor-")
        }
        if (finding.id == "nfc-status" || finding.id.startsWith("nfc-")) {
            return finding.id
        }
        if (finding.category == SignalCategory.BLUETOOTH && finding.id.isNotBlank()) {
            return finding.id
        }
        return null
    }
}
