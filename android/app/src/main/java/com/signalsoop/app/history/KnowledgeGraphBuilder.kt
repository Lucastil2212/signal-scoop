package com.signalsoop.app.history

import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import org.json.JSONObject

/**
 * Builds local knowledge-graph nodes and edges when a scan is saved.
 * Signals (BLE MAC, Wi-Fi BSSID) link across scans; places cluster by rounded coordinates.
 */
object KnowledgeGraphBuilder {
    const val NODE_SCAN = "SCAN"
    const val NODE_PLACE = "PLACE"
    const val NODE_SIGNAL = "SIGNAL"

    const val REL_AT_PLACE = "AT_PLACE"
    const val REL_OBSERVED = "OBSERVED"
    const val REL_REPEAT = "REPEAT"

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

        nodes +=
            GraphNodeEntity(
                id = scanNodeId,
                nodeType = NODE_SCAN,
                label = snapshot.name,
                metadataJson =
                    JSONObject()
                        .put("scannedAt", snapshot.scannedAtEpochMs)
                        .put("riskScore", snapshot.riskSummary?.score ?: JSONObject.NULL)
                        .toString(),
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
                            .put("lat", snapshot.geoFix.latitude)
                            .put("lon", snapshot.geoFix.longitude)
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
                    label = finding.title,
                    metadataJson =
                        JSONObject()
                            .put("category", finding.category.name)
                            .put("detail", finding.detail)
                            .toString(),
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

    private fun isGraphSignal(finding: Finding): Boolean =
        finding.category == SignalCategory.BLE ||
            finding.category == SignalCategory.WIFI ||
            finding.category == SignalCategory.BLUETOOTH

    private fun graphSignalKey(finding: Finding): String? {
        if (finding.id.startsWith("ble-") && finding.id.length > 4) return finding.id.removePrefix("ble-")
        if (finding.id.startsWith("wifi-") && finding.id.length > 5) return finding.id.removePrefix("wifi-")
        if (finding.category == SignalCategory.BLUETOOTH && finding.id.isNotBlank()) return finding.id
        return null
    }
}
