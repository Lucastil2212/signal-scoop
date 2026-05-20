package com.signalsoop.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.signalsoop.app.history.GraphVisualization
import com.signalsoop.app.ui.graph.KnowledgeGraphGeoTimelineView
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun KnowledgeGraphPreviewCard(
    visualization: GraphVisualization?,
    filterScanId: String?,
    onFilterScanChange: (String?) -> Unit,
    scanCount: Int,
    placeCount: Int,
    signalCount: Int,
    onOpenFullscreen: () -> Unit,
    onOpenGraphTab: () -> Unit,
    onNodeSelected: (nodeId: String, nodeType: String, label: String) -> Unit,
    onLinkSelected: (sourceId: String, targetId: String, relation: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasGraph = visualization?.nodes?.isNotEmpty() == true

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ScoopSurfaceHigh,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Hub, contentDescription = null, tint = ScoopGreen)
                    Column {
                        Text("Knowledge graph", style = MaterialTheme.typography.titleMedium, color = ScoopWhite)
                        Text(
                            "$scanCount scans · $placeCount places · $signalCount signals",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScoopMuted,
                        )
                    }
                }
                if (hasGraph) {
                    CopyIconButton(
                        label = "graph stats",
                        value = "Knowledge graph: $scanCount scans, $placeCount places, $signalCount signals",
                    )
                }
            }

            KnowledgeGraphGeoTimelineView(
                visualization = visualization,
                filterScanId = filterScanId,
                onFilterScanChange = onFilterScanChange,
                onNodeSelected = onNodeSelected,
                onLinkSelected = onLinkSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                emptyMessage = "Save a scan to see your graph. Tap nodes for scan and signal details.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenFullscreen,
                    enabled = hasGraph,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ScoopGreen,
                            contentColor = ScoopBlack,
                        ),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.OpenInFull, contentDescription = null)
                        Text("Full screen")
                    }
                }
                OutlinedButton(
                    onClick = onOpenGraphTab,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Graph hub", color = ScoopWhite)
                }
            }
        }
    }
}
