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
        val visible = linkedNodeIds(visualization, scanNodeId, filterScanId)
        val scanLabel = visualization.timelineScans.find { it.scanId == filterScanId }?.label ?: "Scan"
        return GraphViewSlice(
            nodes = visualization.nodes.filter { it.id in visible },
            links =
                visualization.links.filter {
                    it.sourceId in visible && it.targetId in visible
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
        var changed = true
        while (changed) {
            changed = false
            visualization.links.forEach { link ->
                val related =
                    link.scanId == filterScanId ||
                        link.sourceId == scanNodeId ||
                        link.targetId == scanNodeId ||
                        link.sourceId in visible ||
                        link.targetId in visible
                if (related) {
                    if (visible.add(link.sourceId)) changed = true
                    if (visible.add(link.targetId)) changed = true
                }
            }
        }
        return visible
    }
}
