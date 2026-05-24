package com.signalsoop.app.history

import androidx.compose.ui.graphics.Color

data class GraphTimelineScan(
    val scanId: String,
    val scanNodeId: String,
    val label: String,
    val epochMs: Long,
    val lat: Double?,
    val lon: Double?,
    val color: Color,
    val signalCount: Int,
    val signalSummary: String = "",
    /** Every graph signal node from this scan's saved findings (full spectrum). */
    val signalNodeIds: Set<String> = emptySet(),
)

/** Nodes and links visible for the current timeline filter. */
data class GraphViewSlice(
    val nodes: List<GraphVisNode>,
    val links: List<GraphVisLink>,
    val label: String,
)

object GraphTimelineFilter {
    fun slice(visualization: GraphVisualization, filterScanId: String?): GraphViewSlice {
        if (filterScanId == null) {
            return GraphViewSlice(
                nodes = visualization.nodes,
                links = visualization.links,
                label = "All scans",
            )
        }
        val scanNodeId = KnowledgeGraphBuilder.scanNodeId(filterScanId)
        val timelineScan = visualization.timelineScans.find { it.scanId == filterScanId }
        val visible = linkedNodeIds(visualization, scanNodeId, filterScanId).toMutableSet()
        timelineScan?.signalNodeIds?.let { visible.addAll(it) }
        val scanLabel = timelineScan?.label ?: "Scan"
        return GraphViewSlice(
            nodes = visualization.nodes.filter { it.id in visible },
            links =
                visualization.links.filter { link ->
                    (link.sourceId in visible && link.targetId in visible) &&
                        (link.scanId == null || link.scanId == filterScanId)
                },
            label = scanLabel,
        )
    }

    private fun linkedNodeIds(
        visualization: GraphVisualization,
        scanNodeId: String,
        filterScanId: String,
    ): Set<String> {
        val visible = mutableSetOf(scanNodeId)
        visualization.links.forEach { link ->
            val taggedToScan = link.scanId == filterScanId
            val connectsScanNode = link.sourceId == scanNodeId || link.targetId == scanNodeId
            if (!taggedToScan && !connectsScanNode) return@forEach
            when (link.relation) {
                KnowledgeGraphBuilder.REL_OBSERVED,
                KnowledgeGraphBuilder.REL_AT_PLACE,
                KnowledgeGraphBuilder.REL_REPEAT,
                -> {
                    visible.add(link.sourceId)
                    visible.add(link.targetId)
                }
            }
        }
        return visible
    }
}
