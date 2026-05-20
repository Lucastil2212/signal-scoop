package com.signalsoop.app.history

import com.signalsoop.app.history.db.DeviceLinkEntity
import com.signalsoop.app.history.db.EvrusIdentityLinkEntity
import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import com.signalsoop.app.history.db.SignalAliasEntity
import com.signalsoop.app.history.db.UserGraphNodeEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/** Serializes the local knowledge graph for the map WebView viewer. */
object GraphJsonExporter {
    fun toJson(
        nodes: List<GraphNodeEntity>,
        edges: List<GraphEdgeEntity>,
        aliases: List<SignalAliasEntity>,
        userNodes: List<UserGraphNodeEntity>,
        deviceLinks: List<DeviceLinkEntity>,
        evrusLinks: List<EvrusIdentityLinkEntity>,
        scanGpsById: Map<String, Pair<Double, Double>> = emptyMap(),
    ): String {
        val aliasByKey = aliases.associateBy { it.signalKey }
        val coordsByNodeId = buildCoordinateIndex(nodes, edges, scanGpsById)
        val jsonNodes = JSONArray()

        nodes.forEach { node ->
            val signalKey = signalKeyFromNodeId(node.id)
            val pet = signalKey?.let { aliasByKey[it]?.petName }
            val coord = coordsByNodeId[node.id]
            val nodeJson =
                JSONObject()
                    .put("id", node.id)
                    .put("label", pet ?: node.label)
                    .put("rawLabel", node.label)
                    .put("type", node.nodeType)
                    .put("petName", pet ?: JSONObject.NULL)
                    .put("color", colorForType(node.nodeType))
            if (coord != null) {
                nodeJson.put("lat", coord.first).put("lon", coord.second)
            }
            jsonNodes.put(nodeJson)
        }

        userNodes.forEachIndexed { index, user ->
            val id = "user:${user.id}"
            val coord =
                user.linkedScanId?.let { scanId ->
                    coordsByNodeId[KnowledgeGraphBuilder.scanNodeId(scanId)]
                } ?: user.linkedSignalKey?.let { key -> coordsByNodeId["signal:$key"] }
            val offset = offsetDegrees(index + nodes.size, 0.00018)
            val lat = coord?.first?.plus(offset.first)
            val lon = coord?.second?.plus(offset.second)
            val userJson =
                JSONObject()
                    .put("id", id)
                    .put("label", user.label)
                    .put("rawLabel", user.body.take(48))
                    .put("type", "USER")
                    .put("petName", JSONObject.NULL)
                    .put("color", "#FFB020")
            if (lat != null && lon != null) {
                userJson.put("lat", lat).put("lon", lon)
            }
            jsonNodes.put(userJson)
        }

        evrusLinks.forEachIndexed { index, link ->
            val id = "evrus:${link.id}"
            val coord =
                link.scanId?.let { scanId ->
                    coordsByNodeId[KnowledgeGraphBuilder.scanNodeId(scanId)]
                } ?: link.signalKey?.let { key -> coordsByNodeId["signal:$key"] }
            val offset = offsetDegrees(index + 40, 0.00022)
            val lat = coord?.first?.plus(offset.first)
            val lon = coord?.second?.plus(offset.second)
            val evrusJson =
                JSONObject()
                    .put("id", id)
                    .put("label", link.displayName ?: link.evrusDid.take(24))
                    .put("rawLabel", link.evrusDid)
                    .put("type", "EVRUS")
                    .put("petName", JSONObject.NULL)
                    .put("color", "#7B61FF")
            if (lat != null && lon != null) {
                evrusJson.put("lat", lat).put("lon", lon)
            }
            jsonNodes.put(evrusJson)
        }

        val jsonLinks = JSONArray()
        edges.forEach { edge ->
            jsonLinks.put(
                JSONObject()
                    .put("source", edge.fromNodeId)
                    .put("target", edge.toNodeId)
                    .put("relation", edge.relation)
                    .put("weight", edge.weight),
            )
        }

        userNodes.forEach { user ->
            user.linkedScanId?.let { scanId ->
                jsonLinks.put(
                    JSONObject()
                        .put("source", KnowledgeGraphBuilder.scanNodeId(scanId))
                        .put("target", "user:${user.id}")
                        .put("relation", "USER_NOTE")
                        .put("weight", 1),
                )
            }
            user.linkedSignalKey?.let { key ->
                jsonLinks.put(
                    JSONObject()
                        .put("source", "signal:$key")
                        .put("target", "user:${user.id}")
                        .put("relation", "USER_NOTE")
                        .put("weight", 1),
                )
            }
        }

        evrusLinks.forEach { link ->
            link.scanId?.let { scanId ->
                jsonLinks.put(
                    JSONObject()
                        .put("source", KnowledgeGraphBuilder.scanNodeId(scanId))
                        .put("target", "evrus:${link.id}")
                        .put("relation", "EVRUS_ID")
                        .put("weight", 1),
                )
            }
            link.signalKey?.let { key ->
                jsonLinks.put(
                    JSONObject()
                        .put("source", "signal:$key")
                        .put("target", "evrus:${link.id}")
                        .put("relation", "EVRUS_ID")
                        .put("weight", 1),
                )
            }
        }

        deviceLinks.forEachIndexed { index, link ->
            val deviceId = "device:${link.id}"
            val coord = coordsByNodeId["signal:${link.signalKey}"]
            val offset = offsetDegrees(index + 80, 0.00016)
            val lat = coord?.first?.plus(offset.first)
            val lon = coord?.second?.plus(offset.second)
            val deviceJson =
                JSONObject()
                    .put("id", deviceId)
                    .put("label", link.connectionLabel)
                    .put("rawLabel", link.deviceAddress)
                    .put("type", "DEVICE")
                    .put("petName", JSONObject.NULL)
                    .put("color", "#FF4D6D")
            if (lat != null && lon != null) {
                deviceJson.put("lat", lat).put("lon", lon)
            }
            jsonNodes.put(deviceJson)
            jsonLinks.put(
                JSONObject()
                    .put("source", "signal:${link.signalKey}")
                    .put("target", deviceId)
                    .put("relation", "DEVICE_LINK")
                    .put("weight", 1),
            )
        }

        return JSONObject()
            .put("nodes", jsonNodes)
            .put("links", jsonLinks)
            .put("meta", JSONObject().put("engine", "leaflet").put("localOnly", true))
            .toString()
    }

    private fun buildCoordinateIndex(
        nodes: List<GraphNodeEntity>,
        edges: List<GraphEdgeEntity>,
        scanGpsById: Map<String, Pair<Double, Double>>,
    ): Map<String, Pair<Double, Double>> {
        val coords = mutableMapOf<String, Pair<Double, Double>>()

        scanGpsById.forEach { (scanId, coord) ->
            coords[KnowledgeGraphBuilder.scanNodeId(scanId)] = coord
        }

        nodes.forEach { node ->
            coordsFromMetadata(node.metadataJson)?.let { coords[node.id] = it }
        }

        edges
            .filter { it.relation == KnowledgeGraphBuilder.REL_AT_PLACE }
            .forEach { edge ->
                coords[edge.toNodeId]?.let { placeCoord ->
                    if (coords[edge.fromNodeId] == null) {
                        coords[edge.fromNodeId] = placeCoord
                    }
                }
            }

        var signalRound = 0
        edges
            .filter { it.relation == KnowledgeGraphBuilder.REL_OBSERVED }
            .forEach { edge ->
                val scanCoord = coords[edge.fromNodeId] ?: return@forEach
                if (coords[edge.toNodeId] != null) return@forEach
                val offset = offsetDegrees(signalRound++, 0.00012)
                coords[edge.toNodeId] =
                    Pair(scanCoord.first + offset.first, scanCoord.second + offset.second)
            }

        return coords
    }

    private fun coordsFromMetadata(metadataJson: String?): Pair<Double, Double>? {
        val meta = metadataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (!meta.has("lat") || !meta.has("lon")) return null
        val lat = meta.optDouble("lat")
        val lon = meta.optDouble("lon")
        if (lat == 0.0 && lon == 0.0) return null
        return Pair(lat, lon)
    }

    /** Small deterministic offset (~tens of meters) so stacked nodes remain visible. */
    private fun offsetDegrees(seed: Int, metersApprox: Double): Pair<Double, Double> {
        val angle = seed * 2.399963f
        val dLat = metersApprox * cos(angle)
        val dLon = metersApprox * sin(angle)
        return Pair(dLat, dLon)
    }

    private fun signalKeyFromNodeId(nodeId: String): String? =
        if (nodeId.startsWith("signal:")) nodeId.removePrefix("signal:") else null

    private fun colorForType(type: String): String =
        when (type) {
            KnowledgeGraphBuilder.NODE_SCAN -> "#39FF14"
            KnowledgeGraphBuilder.NODE_PLACE -> "#00AEEF"
            KnowledgeGraphBuilder.NODE_SIGNAL -> "#7AE7FF"
            else -> "#9AA3B2"
        }
}
