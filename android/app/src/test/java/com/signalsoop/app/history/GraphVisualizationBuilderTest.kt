package com.signalsoop.app.history

import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class GraphVisualizationBuilderTest {
    @Test
    fun build_placesNodesWithSpreadLayout() {
        val scanId = "abc"
        val nodes =
            listOf(
                GraphNodeEntity(
                    id = KnowledgeGraphBuilder.scanNodeId(scanId),
                    nodeType = KnowledgeGraphBuilder.NODE_SCAN,
                    label = "Scan 1",
                    metadataJson = """{"lat":37.77,"lon":-122.42}""",
                ),
                GraphNodeEntity(
                    id = "place:37.7700,-122.4200",
                    nodeType = KnowledgeGraphBuilder.NODE_PLACE,
                    label = "Place",
                    metadataJson = """{"lat":37.77,"lon":-122.42}""",
                ),
                GraphNodeEntity(
                    id = "signal:aa:bb:cc:dd:ee:ff",
                    nodeType = KnowledgeGraphBuilder.NODE_SIGNAL,
                    label = "BLE device",
                    metadataJson = null,
                ),
            )
        val edges =
            listOf(
                GraphEdgeEntity(
                    fromNodeId = KnowledgeGraphBuilder.scanNodeId(scanId),
                    toNodeId = "place:37.7700,-122.4200",
                    relation = KnowledgeGraphBuilder.REL_AT_PLACE,
                    scanId = scanId,
                    weight = 1,
                ),
                GraphEdgeEntity(
                    fromNodeId = KnowledgeGraphBuilder.scanNodeId(scanId),
                    toNodeId = "signal:aa:bb:cc:dd:ee:ff",
                    relation = KnowledgeGraphBuilder.REL_OBSERVED,
                    scanId = scanId,
                    weight = 1,
                ),
            )

        val viz =
            GraphVisualizationBuilder.build(
                nodes = nodes,
                edges = edges,
                aliases = emptyList(),
                userNodes = emptyList(),
                deviceLinks = emptyList(),
                evrusLinks = emptyList(),
                scanGpsById = mapOf(scanId to Pair(37.77, -122.42)),
                scanEpochById = mapOf(scanId to 1_700_000_000_000L),
                scanLabelsById = mapOf(scanId to "Test scan"),
            )

        assertEquals(3, viz.nodeCount)
        assertTrue(viz.linkCount >= 2)
        assertTrue(viz.usesGps)
        assertEquals(1, viz.timelineScans.size)

        val spread = layoutSpread(viz.nodes)
        assertTrue("nodes should not all stack at origin (spread=$spread)", spread > 0.08f)
    }

    @Test
    fun build_forceLayoutWhenNoGps() {
        val nodes =
            listOf(
                GraphNodeEntity(
                    id = "scan:1",
                    nodeType = KnowledgeGraphBuilder.NODE_SCAN,
                    label = "Scan",
                    metadataJson = null,
                ),
                GraphNodeEntity(
                    id = "signal:wifi-1",
                    nodeType = KnowledgeGraphBuilder.NODE_SIGNAL,
                    label = "Wi-Fi",
                    metadataJson = null,
                ),
            )
        val edges =
            listOf(
                GraphEdgeEntity(
                    fromNodeId = "scan:1",
                    toNodeId = "signal:wifi-1",
                    relation = KnowledgeGraphBuilder.REL_OBSERVED,
                    scanId = "1",
                    weight = 1,
                ),
            )

        val viz =
            GraphVisualizationBuilder.build(
                nodes = nodes,
                edges = edges,
                aliases = emptyList(),
                userNodes = emptyList(),
                deviceLinks = emptyList(),
                evrusLinks = emptyList(),
                scanGpsById = emptyMap(),
            )

        assertEquals(2, viz.nodeCount)
        assertFalse(viz.usesGps)
        assertTrue(layoutSpread(viz.nodes) > 0.1f)
    }

    private fun layoutSpread(nodes: List<GraphVisNode>): Float {
        if (nodes.size < 2) return 1f
        var sum = 0f
        var count = 0
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val dx = nodes[i].layoutX - nodes[j].layoutX
                val dy = nodes[i].layoutY - nodes[j].layoutY
                sum += sqrt(dx * dx + dy * dy)
                count++
            }
        }
        return sum / count.coerceAtLeast(1)
    }
}
