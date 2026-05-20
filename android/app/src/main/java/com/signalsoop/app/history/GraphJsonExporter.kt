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

/** Serializes the local knowledge graph for the 3D WebView viewer. */
object GraphJsonExporter {
    fun toJson(
        nodes: List<GraphNodeEntity>,
        edges: List<GraphEdgeEntity>,
        aliases: List<SignalAliasEntity>,
        userNodes: List<UserGraphNodeEntity>,
        deviceLinks: List<DeviceLinkEntity>,
        evrusLinks: List<EvrusIdentityLinkEntity>,
    ): String {
        val aliasByKey = aliases.associateBy { it.signalKey }
        val jsonNodes = JSONArray()
        val placed = mutableMapOf<String, Triple<Float, Float, Float>>()

        nodes.forEachIndexed { index, node ->
            val pos = positionFor(node, index, nodes.size, placed)
            placed[node.id] = pos
            val signalKey = signalKeyFromNodeId(node.id)
            val pet = signalKey?.let { aliasByKey[it]?.petName }
            jsonNodes.put(
                JSONObject()
                    .put("id", node.id)
                    .put("label", pet ?: node.label)
                    .put("rawLabel", node.label)
                    .put("type", node.nodeType)
                    .put("x", pos.first.toDouble())
                    .put("y", pos.second.toDouble())
                    .put("z", pos.third.toDouble())
                    .put("petName", pet ?: JSONObject.NULL)
                    .put("color", colorForType(node.nodeType)),
            )
        }

        userNodes.forEachIndexed { index, user ->
            val id = "user:${user.id}"
            val angle = (index + nodes.size) * 0.71f
            val radius = 4.5f + (index % 5) * 0.35f
            jsonNodes.put(
                JSONObject()
                    .put("id", id)
                    .put("label", user.label)
                    .put("rawLabel", user.body.take(48))
                    .put("type", "USER")
                    .put("x", (cos(angle) * radius).toDouble())
                    .put("y", ((index % 7) - 3) * 0.55)
                    .put("z", (sin(angle) * radius).toDouble())
                    .put("petName", JSONObject.NULL)
                    .put("color", "#FFB020"),
            )
            user.linkedScanId?.let { scanId ->
                edges.find { it.fromNodeId == KnowledgeGraphBuilder.scanNodeId(scanId) }
                    ?.let { /* already linked via scan */ }
            }
        }

        evrusLinks.forEachIndexed { index, link ->
            val id = "evrus:${link.id}"
            jsonNodes.put(
                JSONObject()
                    .put("id", id)
                    .put("label", link.displayName ?: link.evrusDid.take(24))
                    .put("rawLabel", link.evrusDid)
                    .put("type", "EVRUS")
                    .put("x", (index * 0.9 - 2).toDouble())
                    .put("y", 3.2 + index * 0.15)
                    .put("z", (index * 0.4).toDouble())
                    .put("petName", JSONObject.NULL)
                    .put("color", "#7B61FF"),
            )
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
            jsonNodes.put(
                JSONObject()
                    .put("id", deviceId)
                    .put("label", link.connectionLabel)
                    .put("rawLabel", link.deviceAddress)
                    .put("type", "DEVICE")
                    .put("x", (index * 0.7).toDouble())
                    .put("y", -2.5)
                    .put("z", (index * 0.5 - 1).toDouble())
                    .put("petName", JSONObject.NULL)
                    .put("color", "#FF4D6D"),
            )
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
            .put("meta", JSONObject().put("engine", "three.js").put("localOnly", true))
            .toString()
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

    private fun positionFor(
        node: GraphNodeEntity,
        index: Int,
        total: Int,
        placed: Map<String, Triple<Float, Float, Float>>,
    ): Triple<Float, Float, Float> {
        when (node.nodeType) {
            KnowledgeGraphBuilder.NODE_PLACE -> {
                val meta = node.metadataJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                val lat = meta?.optDouble("lat") ?: 0.0
                val lon = meta?.optDouble("lon") ?: 0.0
                return Triple(
                    ((lon * 1000) % 8).toFloat() - 4f,
                    0f,
                    ((lat * 1000) % 8).toFloat() - 4f,
                )
            }
            KnowledgeGraphBuilder.NODE_SCAN -> {
                val angle = index * (6.28318f / total.coerceAtLeast(1))
                return Triple(cos(angle) * 2.2f, (index % 5) * 0.3f - 0.6f, sin(angle) * 2.2f)
            }
            KnowledgeGraphBuilder.NODE_SIGNAL -> {
                val angle = index * 0.55f + 1.2f
                return Triple(cos(angle) * 5f, ((index % 9) - 4) * 0.25f, sin(angle) * 5f)
            }
        }
        val angle = index * 0.4f
        return Triple(cos(angle) * 3f, 0f, sin(angle) * 3f)
    }
}
