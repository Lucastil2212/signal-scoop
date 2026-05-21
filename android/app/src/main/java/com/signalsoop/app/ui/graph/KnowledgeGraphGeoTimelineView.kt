package com.signalsoop.app.ui.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signalsoop.app.history.GraphTimelineFilter
import com.signalsoop.app.history.GraphVisualization
import com.signalsoop.app.ui.KnowledgeGraphCanvasView
import com.signalsoop.app.ui.theme.ScoopMuted

@Composable
fun KnowledgeGraphGeoTimelineView(
    visualization: GraphVisualization?,
    filterScanId: String?,
    onFilterScanChange: (String?) -> Unit,
    onNodeSelected: (nodeId: String, nodeType: String, label: String) -> Unit,
    onLinkSelected: (sourceId: String, targetId: String, relation: String) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Save a scan on the Scan tab to build your knowledge graph.",
    showChrome: Boolean = true,
    showTimeline: Boolean = true,
    footer: @Composable (() -> Unit)? = null,
) {
    if (visualization == null || visualization.nodes.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                emptyMessage,
                color = ScoopMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val slice = GraphTimelineFilter.slice(visualization, filterScanId)
    val showMap = visualization.geoNodeCount > 0

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showMap) {
                KnowledgeGraphMapLayer(
                    visualization = visualization,
                    filterScanId = filterScanId,
                    onNodeSelected = onNodeSelected,
                    onLinkSelected = onLinkSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                KnowledgeGraphCanvasView(
                    visualization = visualization,
                    filterScanId = filterScanId,
                    onNodeSelected = onNodeSelected,
                    onLinkSelected = onLinkSelected,
                    modifier = Modifier.fillMaxSize(),
                    emptyMessage = emptyMessage,
                )
            }
            if (showChrome) {
                GraphGraphChrome(
                    nodeCount = slice.nodes.size,
                    linkCount = slice.links.size,
                    usesMap = showMap,
                    filterLabel = slice.label,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                )
            }
            GraphMapLegendOverlay(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp, end = 72.dp),
            )
        }
        if (showTimeline) {
            GraphTimelineBar(
                scans = visualization.timelineScans,
                filterScanId = filterScanId,
                filterLabel = slice.label,
                onSelectScan = onFilterScanChange,
            )
        }
        footer?.invoke()
    }
}
