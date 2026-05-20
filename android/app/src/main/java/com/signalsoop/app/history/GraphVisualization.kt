package com.signalsoop.app.history

import androidx.compose.ui.graphics.Color
import com.signalsoop.app.history.db.DeviceLinkEntity
import com.signalsoop.app.history.db.EvrusIdentityLinkEntity
import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import com.signalsoop.app.history.db.SignalAliasEntity
import com.signalsoop.app.history.db.UserGraphNodeEntity
import kotlin.math.cos
import kotlin.math.sin

data class GraphVisNode(
    val id: String,
    val label: String,
    val rawLabel: String,
    val type: String,
    val color: Color,
    val layoutX: Float,
    val layoutY: Float,
    val lat: Double? = null,
    val lon: Double? = null,
    val epochMs: Long? = null,
    val signalCategory: String? = null,
    val linkedScanId: String? = null,
    val timeLabel: String? = null,
)

data class GraphVisLink(
    val sourceId: String,
    val targetId: String,
    val relation: String,
    val scanId: String? = null,
    val epochMs: Long? = null,
)

data class GraphVisualization(
    val nodes: List<GraphVisNode>,
    val links: List<GraphVisLink>,
    val timelineScans: List<GraphTimelineScan>,
    val timeMinMs: Long,
    val timeMaxMs: Long,
    val usesGps: Boolean,
    val nodeCount: Int,
    val linkCount: Int,
) {
    val geoNodeCount: Int = nodes.count { it.lat != null && it.lon != null }
}

object GraphVisualizationBuilder {
    fun build(
        nodes: List<GraphNodeEntity>,
        edges: List<GraphEdgeEntity>,
        aliases: List<SignalAliasEntity>,
        userNodes: List<UserGraphNodeEntity>,
        deviceLinks: List<DeviceLinkEntity>,
        evrusLinks: List<EvrusIdentityLinkEntity>,
        scanGpsById: Map<String, Pair<Double, Double>>,
        scanEpochById: Map<String, Long> = emptyMap(),
        scanLabelsById: Map<String, String> = emptyMap(),
    ): GraphVisualization {
        val aliasByKey = aliases.associateBy { it.signalKey }
        val coordsByNodeId = buildCoordinateIndex(nodes, edges, scanGpsById)
        val visNodes = mutableListOf<GraphVisNode>()
        val visLinks = mutableListOf<GraphVisLink>()

        val sortedScanIds = scanEpochById.entries.sortedBy { it.value }.map { it.key }
        val scanIndexById = sortedScanIds.mapIndexed { index, id -> id to index }.toMap()
        val signalsPerScan =
            edges
                .filter { it.relation == KnowledgeGraphBuilder.REL_OBSERVED }
                .groupBy { it.scanId }
                .mapValues { it.value.size }

        val timeMin = scanEpochById.values.minOrNull() ?: 0L
        val timeMax = scanEpochById.values.maxOrNull() ?: timeMin

        nodes.forEach { node ->
            val signalKey = signalKeyFromNodeId(node.id)
            val pet = signalKey?.let { aliasByKey[it]?.petName }
            val coord = coordsByNodeId[node.id]
            val meta = node.metadataJson?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            when (node.nodeType) {
                KnowledgeGraphBuilder.NODE_SCAN -> {
                    val scanId = node.id.removePrefix("scan:")
                    val epoch = meta?.optLong("scannedAt")?.takeIf { it > 0 } ?: scanEpochById[scanId]
                    val idx = scanIndexById[scanId] ?: 0
                    visNodes +=
                        GraphVisNode(
                            id = node.id,
                            label = pet ?: node.label,
                            rawLabel = node.label,
                            type = node.nodeType,
                            color = GraphColorPalette.scanColor(idx, sortedScanIds.size.coerceAtLeast(1)),
                            layoutX = 0f,
                            layoutY = 0f,
                            lat = coord?.first,
                            lon = coord?.second,
                            epochMs = epoch,
                            linkedScanId = scanId,
                            timeLabel = epoch?.let { formatTime(it) },
                        )
                }
                KnowledgeGraphBuilder.NODE_PLACE -> {
                    visNodes +=
                        GraphVisNode(
                            id = node.id,
                            label = pet ?: node.label,
                            rawLabel = node.label,
                            type = node.nodeType,
                            color = GraphColorPalette.place,
                            layoutX = 0f,
                            layoutY = 0f,
                            lat = coord?.first,
                            lon = coord?.second,
                            epochMs = linkedScanEpoch(node.id, edges, scanEpochById),
                            timeLabel = null,
                        )
                }
                KnowledgeGraphBuilder.NODE_SIGNAL -> {
                    val category = meta?.optString("category")
                    val obs =
                        edges.find {
                            it.relation == KnowledgeGraphBuilder.REL_OBSERVED && it.toNodeId == node.id
                        }
                    val scanId = obs?.scanId
                    val epoch = scanId?.let { scanEpochById[it] }
                    val rssi = meta?.optInt("rssi")?.takeIf { it != 0 }
                    val detail = meta?.optString("detail")?.takeIf { it.isNotBlank() }
                    val extrasSummary = meta?.optString("extrasSummary")?.takeIf { it.isNotBlank() }
                    val subtitle =
                        buildList {
                            rssi?.let { add("$it dBm") }
                            detail?.let { add(it.take(48)) }
                            extrasSummary?.let { add(it.take(40)) }
                            epoch?.let { add(formatTime(it)) }
                        }.joinToString(" · ")
                    visNodes +=
                        GraphVisNode(
                            id = node.id,
                            label = pet ?: node.label,
                            rawLabel = node.label,
                            type = node.nodeType,
                            color = GraphColorPalette.signalColor(category),
                            layoutX = 0f,
                            layoutY = 0f,
                            lat = coord?.first,
                            lon = coord?.second,
                            epochMs = epoch,
                            signalCategory = category,
                            linkedScanId = scanId,
                            timeLabel = subtitle.ifBlank { epoch?.let { formatTime(it) } },
                        )
                }
                else -> {
                    visNodes +=
                        GraphVisNode(
                            id = node.id,
                            label = pet ?: node.label,
                            rawLabel = node.label,
                            type = node.nodeType,
                            color = GraphColorPalette.nodeTypeColor(node.nodeType),
                            layoutX = 0f,
                            layoutY = 0f,
                            lat = coord?.first,
                            lon = coord?.second,
                        )
                }
            }
        }

        userNodes.forEachIndexed { index, user ->
            val id = "user:${user.id}"
            val coord =
                user.linkedScanId?.let { scanId ->
                    coordsByNodeId[KnowledgeGraphBuilder.scanNodeId(scanId)]
                } ?: user.linkedSignalKey?.let { key -> coordsByNodeId["signal:$key"] }
            val offset = offsetDegrees(index + nodes.size, 0.00018)
            visNodes +=
                GraphVisNode(
                    id = id,
                    label = user.label,
                    rawLabel = user.body.take(48),
                    type = "USER",
                    color = GraphColorPalette.user,
                    layoutX = 0f,
                    layoutY = 0f,
                    lat = coord?.first?.plus(offset.first),
                    lon = coord?.second?.plus(offset.second),
                )
        }

        evrusLinks.forEachIndexed { index, link ->
            val id = "evrus:${link.id}"
            val coord =
                link.scanId?.let { scanId ->
                    coordsByNodeId[KnowledgeGraphBuilder.scanNodeId(scanId)]
                } ?: link.signalKey?.let { key -> coordsByNodeId["signal:$key"] }
            val offset = offsetDegrees(index + 40, 0.00022)
            visNodes +=
                GraphVisNode(
                    id = id,
                    label = link.displayName ?: link.evrusDid.take(24),
                    rawLabel = link.evrusDid,
                    type = "EVRUS",
                    color = GraphColorPalette.evrus,
                    layoutX = 0f,
                    layoutY = 0f,
                    lat = coord?.first?.plus(offset.first),
                    lon = coord?.second?.plus(offset.second),
                )
        }

        deviceLinks.forEachIndexed { index, link ->
            val deviceId = "device:${link.id}"
            val coord = coordsByNodeId["signal:${link.signalKey}"]
            val offset = offsetDegrees(index + 80, 0.00016)
            visNodes +=
                GraphVisNode(
                    id = deviceId,
                    label = link.connectionLabel,
                    rawLabel = link.deviceAddress,
                    type = "DEVICE",
                    color = GraphColorPalette.device,
                    layoutX = 0f,
                    layoutY = 0f,
                    lat = coord?.first?.plus(offset.first),
                    lon = coord?.second?.plus(offset.second),
                )
        }

        edges.forEach { edge ->
            val epoch = edge.scanId?.let { scanEpochById[it] }
            visLinks +=
                GraphVisLink(
                    sourceId = edge.fromNodeId,
                    targetId = edge.toNodeId,
                    relation = edge.relation,
                    scanId = edge.scanId,
                    epochMs = epoch,
                )
        }

        userNodes.forEach { user ->
            user.linkedScanId?.let { scanId ->
                visLinks +=
                    GraphVisLink(
                        sourceId = KnowledgeGraphBuilder.scanNodeId(scanId),
                        targetId = "user:${user.id}",
                        relation = "USER_NOTE",
                    )
            }
            user.linkedSignalKey?.let { key ->
                visLinks +=
                    GraphVisLink(
                        sourceId = "signal:$key",
                        targetId = "user:${user.id}",
                        relation = "USER_NOTE",
                    )
            }
        }

        evrusLinks.forEach { link ->
            link.scanId?.let { scanId ->
                visLinks +=
                    GraphVisLink(
                        sourceId = KnowledgeGraphBuilder.scanNodeId(scanId),
                        targetId = "evrus:${link.id}",
                        relation = "EVRUS_ID",
                    )
            }
            link.signalKey?.let { key ->
                visLinks +=
                    GraphVisLink(
                        sourceId = "signal:$key",
                        targetId = "evrus:${link.id}",
                        relation = "EVRUS_ID",
                    )
            }
        }

        deviceLinks.forEach { link ->
            visLinks +=
                GraphVisLink(
                    sourceId = "signal:${link.signalKey}",
                    targetId = "device:${link.id}",
                    relation = "DEVICE_LINK",
                )
        }

        val usesGps = applyLayout(visNodes, visLinks)
        finalizeNodeLayout(visNodes)

        val timelineScans =
            sortedScanIds.map { scanId ->
                val nodeId = KnowledgeGraphBuilder.scanNodeId(scanId)
                val node = visNodes.find { it.id == nodeId }
                GraphTimelineScan(
                    scanId = scanId,
                    scanNodeId = nodeId,
                    label = scanLabelsById[scanId] ?: node?.label ?: "Scan",
                    epochMs = scanEpochById[scanId] ?: 0L,
                    lat = node?.lat ?: scanGpsById[scanId]?.first,
                    lon = node?.lon ?: scanGpsById[scanId]?.second,
                    color = node?.color ?: GraphColorPalette.scanColor(0, 1),
                    signalCount = signalsPerScan[scanId] ?: 0,
                    signalSummary = signalSummaryForScan(scanId, nodes, edges),
                )
            }

        return GraphVisualization(
            nodes = visNodes,
            links = visLinks,
            timelineScans = timelineScans,
            timeMinMs = timeMin,
            timeMaxMs = timeMax,
            usesGps = usesGps,
            nodeCount = visNodes.size,
            linkCount = visLinks.size,
        )
    }

    private fun linkedScanEpoch(
        placeNodeId: String,
        edges: List<GraphEdgeEntity>,
        scanEpochById: Map<String, Long>,
    ): Long? =
        edges
            .find { it.relation == KnowledgeGraphBuilder.REL_AT_PLACE && it.toNodeId == placeNodeId }
            ?.scanId
            ?.let { scanEpochById[it] }

    private fun formatTime(epochMs: Long): String =
        java.text.SimpleDateFormat("MMM d · h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(epochMs))

    private fun applyLayout(nodes: MutableList<GraphVisNode>, links: List<GraphVisLink>): Boolean {
        if (nodes.isEmpty()) return false
        val geoNodes = nodes.filter { it.lat != null && it.lon != null }
        if (geoNodes.isNotEmpty()) {
            var minLat = geoNodes.first().lat!!
            var maxLat = minLat
            var minLon = geoNodes.first().lon!!
            var maxLon = minLon
            geoNodes.forEach { n ->
                minLat = minOf(minLat, n.lat!!)
                maxLat = maxOf(maxLat, n.lat!!)
                minLon = minOf(minLon, n.lon!!)
                maxLon = maxOf(maxLon, n.lon!!)
            }
            val padLat = maxOf((maxLat - minLat) * 0.12, 0.0005)
            val padLon = maxOf((maxLon - minLon) * 0.12, 0.0005)
            minLat -= padLat
            maxLat += padLat
            minLon -= padLon
            maxLon += padLon
            val latSpan = maxOf(maxLat - minLat, 0.0008)
            val lonSpan = maxOf(maxLon - minLon, 0.0008)

            nodes.indices.forEach { i ->
                val n = nodes[i]
                var lat = n.lat
                var lon = n.lon
                if (lat == null || lon == null) {
                    val parent =
                        links.firstNotNullOfOrNull { link ->
                            when {
                                link.targetId == n.id -> nodes.find { it.id == link.sourceId }
                                link.sourceId == n.id -> nodes.find { it.id == link.targetId }
                                else -> null
                            }
                        }
                    if (parent != null && parent.lat != null && parent.lon != null) {
                        val angle = i * 2.399963
                        lat = parent.lat!! + cos(angle) * 0.00015
                        lon = parent.lon!! + sin(angle) * 0.00015
                    }
                }
                if (lat != null && lon != null) {
                    val lx = ((lon - minLon) / lonSpan).toFloat() * 2f - 1f
                    val ly = (1f - ((lat - minLat) / latSpan).toFloat()) * 2f - 1f
                    nodes[i] = n.copy(layoutX = lx, layoutY = ly, lat = lat, lon = lon)
                }
            }
            runForceLayout(nodes, links, strength = 0.25f, iterations = 30)
            return true
        }
        runForceLayout(nodes, links, strength = 1f, iterations = 80)
        return false
    }

    /** Guarantee visible spread and [-1,1]-ish coordinates for the canvas renderer. */
    private fun finalizeNodeLayout(nodes: MutableList<GraphVisNode>) {
        if (nodes.isEmpty()) return
        var orphan = 0
        nodes.indices.forEach { i ->
            val n = nodes[i]
            if (n.layoutX == 0f && n.layoutY == 0f) {
                val angle = orphan++ * 2.399963f
                nodes[i] = n.copy(layoutX = cos(angle) * 0.65f, layoutY = sin(angle) * 0.65f)
            }
        }
        var minX = nodes.minOf { it.layoutX }
        var maxX = nodes.maxOf { it.layoutX }
        var minY = nodes.minOf { it.layoutY }
        var maxY = nodes.maxOf { it.layoutY }
        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX < 0.05f && spanY < 0.05f && nodes.size > 1) {
            nodes.indices.forEach { i ->
                val angle = (i.toFloat() / nodes.size) * (Math.PI * 2).toFloat()
                nodes[i] =
                    nodes[i].copy(
                        layoutX = cos(angle) * 0.82f,
                        layoutY = sin(angle) * 0.82f,
                    )
            }
            minX = nodes.minOf { it.layoutX }
            maxX = nodes.maxOf { it.layoutX }
            minY = nodes.minOf { it.layoutY }
            maxY = nodes.maxOf { it.layoutY }
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val span = maxOf(maxX - minX, maxY - minY, 0.2f)
        nodes.indices.forEach { i ->
            val n = nodes[i]
            nodes[i] =
                n.copy(
                    layoutX = ((n.layoutX - cx) / span * 1.65f).coerceIn(-0.95f, 0.95f),
                    layoutY = ((n.layoutY - cy) / span * 1.65f).coerceIn(-0.95f, 0.95f),
                )
        }
    }

    private fun runForceLayout(
        nodes: MutableList<GraphVisNode>,
        links: List<GraphVisLink>,
        strength: Float,
        iterations: Int,
    ) {
        if (nodes.isEmpty()) return
        val positions =
            nodes.mapIndexed { index, _ ->
                val angle = (index.toFloat() / nodes.size) * (Math.PI * 2).toFloat()
                floatArrayOf(cos(angle) * 0.85f, sin(angle) * 0.85f)
            }.toMutableList()
        val indexById = nodes.mapIndexed { i, n -> n.id to i }.toMap()

        repeat(iterations) {
            val vx = FloatArray(nodes.size)
            val vy = FloatArray(nodes.size)
            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    var dx = positions[j][0] - positions[i][0]
                    var dy = positions[j][1] - positions[i][1]
                    val d2 = maxOf(dx * dx + dy * dy, 0.04f)
                    val f = 0.08f / d2
                    vx[i] -= dx * f
                    vy[i] -= dy * f
                    vx[j] += dx * f
                    vy[j] += dy * f
                }
            }
            links.forEach { link ->
                val ai = indexById[link.sourceId] ?: return@forEach
                val bi = indexById[link.targetId] ?: return@forEach
                val dx = positions[bi][0] - positions[ai][0]
                val dy = positions[bi][1] - positions[ai][1]
                val pull = 0.04f * strength
                vx[ai] += dx * pull
                vy[ai] += dy * pull
                vx[bi] -= dx * pull
                vy[bi] -= dy * pull
            }
            for (i in nodes.indices) {
                vx[i] -= positions[i][0] * 0.02f * strength
                vy[i] -= positions[i][1] * 0.02f * strength
                positions[i][0] += vx[i]
                positions[i][1] += vy[i]
            }
        }
        nodes.indices.forEach { i ->
            nodes[i] = nodes[i].copy(layoutX = positions[i][0], layoutY = positions[i][1])
        }
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
        val nodeById = nodes.associateBy { it.id }
        val observedByScan =
            edges
                .filter { it.relation == KnowledgeGraphBuilder.REL_OBSERVED }
                .groupBy { it.scanId }
        observedByScan.forEach { (_, scanEdges) ->
            val counters = mutableMapOf<String, Int>()
            scanEdges.forEach { edge ->
                val scanCoord = coords[edge.fromNodeId] ?: return@forEach
                if (coords[edge.toNodeId] != null) return@forEach
                val category =
                    nodeById[edge.toNodeId]?.metadataJson?.let { meta ->
                        runCatching { org.json.JSONObject(meta).optString("category") }.getOrNull()
                    }.orEmpty()
                val idx = counters.getOrDefault(category, 0)
                counters[category] = idx + 1
                val offset = signalOffsetDegrees(category, idx)
                coords[edge.toNodeId] =
                    Pair(scanCoord.first + offset.first, scanCoord.second + offset.second)
            }
        }
        return coords
    }

    private fun signalOffsetDegrees(category: String, index: Int): Pair<Double, Double> {
        val baseAngle =
            when (category.uppercase()) {
                "SENSORS" -> 0.0
                "NFC" -> 1.2
                "BLUETOOTH" -> 2.4
                "WIFI" -> 3.6
                "BLE" -> 4.8
                else -> 0.6
            }
        val radius =
            when (category.uppercase()) {
                "SENSORS" -> 0.00042
                "NFC" -> 0.00028
                "BLUETOOTH" -> 0.00024
                "WIFI" -> 0.00020
                "BLE" -> 0.00018
                else -> 0.00022
            }
        val angle = baseAngle + index * 0.35
        return Pair(radius * kotlin.math.cos(angle), radius * kotlin.math.sin(angle))
    }

    private fun signalSummaryForScan(
        scanId: String,
        nodes: List<GraphNodeEntity>,
        edges: List<GraphEdgeEntity>,
    ): String {
        val nodeById = nodes.associateBy { it.id }
        val categories =
            edges
                .filter { it.relation == KnowledgeGraphBuilder.REL_OBSERVED && it.scanId == scanId }
                .mapNotNull { edge ->
                    nodeById[edge.toNodeId]?.metadataJson?.let { meta ->
                        runCatching { org.json.JSONObject(meta).optString("category") }.getOrNull()
                    }
                }
        if (categories.isEmpty()) return "0 signals"
        return buildList {
            categories.groupingBy { it }.eachCount().forEach { (cat, count) ->
                val label =
                    runCatching { com.signalsoop.app.model.SignalCategory.valueOf(cat) }
                        .getOrNull()
                        ?.label ?: cat
                add("$count $label")
            }
        }.joinToString(" · ")
    }

    private fun coordsFromMetadata(metadataJson: String?): Pair<Double, Double>? {
        val meta = metadataJson?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: return null
        if (!meta.has("lat") || !meta.has("lon")) return null
        val lat = meta.optDouble("lat")
        val lon = meta.optDouble("lon")
        if (lat == 0.0 && lon == 0.0) return null
        return Pair(lat, lon)
    }

    private fun offsetDegrees(seed: Int, metersApprox: Double): Pair<Double, Double> {
        val angle = seed * 2.399963f
        return Pair(metersApprox * cos(angle), metersApprox * sin(angle))
    }

    private fun signalKeyFromNodeId(nodeId: String): String? =
        if (nodeId.startsWith("signal:")) nodeId.removePrefix("signal:") else null

}
