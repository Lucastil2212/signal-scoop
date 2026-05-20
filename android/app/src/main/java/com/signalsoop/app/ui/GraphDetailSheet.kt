package com.signalsoop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.history.GraphColorPalette
import com.signalsoop.app.history.GraphLinkSelection
import com.signalsoop.app.history.GraphVisLink
import com.signalsoop.app.history.GraphVisNode
import com.signalsoop.app.history.KnowledgeGraphBuilder
import com.signalsoop.app.history.ScanSnapshot
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.ui.components.FindingCard
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphDetailSheet(
    nodeId: String?,
    link: GraphLinkSelection?,
    viewModel: HistoryViewModel,
    uiState: com.signalsoop.app.HistoryUiState,
    onDismiss: () -> Unit,
    onOpenTimelineForScan: (scanId: String) -> Unit,
) {
    if (nodeId == null && link == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val viz = uiState.graphVisualization

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ScoopBlack,
        contentColor = ScoopWhite,
        dragHandle = {
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScoopMuted.copy(alpha = 0.6f)),
            )
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (link != null && viz != null) {
                linkDetailItems(link, viz, viewModel, uiState, onDismiss, onOpenTimelineForScan)
            } else if (nodeId != null) {
                nodeDetailItems(nodeId, viz, viewModel, uiState, onDismiss, onOpenTimelineForScan)
            }
            item {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = ScoopMuted)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.linkDetailItems(
    link: GraphLinkSelection,
    viz: com.signalsoop.app.history.GraphVisualization,
    viewModel: HistoryViewModel,
    uiState: com.signalsoop.app.HistoryUiState,
    onDismiss: () -> Unit,
    onOpenTimelineForScan: (scanId: String) -> Unit,
) {
    val source = viz.nodes.find { it.id == link.sourceId }
    val target = viz.nodes.find { it.id == link.targetId }
    val edge = viz.links.find { it.sourceId == link.sourceId && it.targetId == link.targetId && it.relation == link.relation }
    val relColor = GraphColorPalette.relationColor(link.relation)

    item {
        DetailHeader(
            title = GraphColorPalette.relationLabel(link.relation),
            subtitle = "Relationship",
            accentColor = relColor,
        )
    }
    item {
        RelationPill(color = relColor, label = GraphColorPalette.relationLabel(link.relation))
    }
    item {
        Text(
            GraphColorPalette.relationDescription(link.relation),
            color = ScoopWhite,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    edge?.epochMs?.let { epoch ->
        item {
            Text(
                "When: ${formatTime(epoch)}",
                color = ScoopGreen,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    item {
        Text("From", color = ScoopMuted, style = MaterialTheme.typography.labelMedium)
    }
    item {
        NodeSummaryCard(
            node = source,
            fallbackId = link.sourceId,
            onOpen = { viewModel.openGraphNodeDetail(link.sourceId) },
        )
    }
    item {
        Text("To", color = ScoopMuted, style = MaterialTheme.typography.labelMedium)
    }
    item {
        NodeSummaryCard(
            node = target,
            fallbackId = link.targetId,
            onOpen = { viewModel.openGraphNodeDetail(link.targetId) },
        )
    }
    edge?.scanId?.let { scanId ->
        item {
            Button(
                onClick = {
                    onDismiss()
                    onOpenTimelineForScan(scanId)
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ScoopGreen,
                        contentColor = ScoopBlack,
                    ),
            ) {
                Text("Open linked scan in Timeline")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.nodeDetailItems(
    nodeId: String,
    viz: com.signalsoop.app.history.GraphVisualization?,
    viewModel: HistoryViewModel,
    uiState: com.signalsoop.app.HistoryUiState,
    onDismiss: () -> Unit,
    onOpenTimelineForScan: (scanId: String) -> Unit,
) {
    val node = viz?.nodes?.find { it.id == nodeId }
    val scanId = viewModel.scanIdFromGraphNode(nodeId)
    val signalKey = viewModel.signalKeyFromGraphNode(nodeId)
    val accent = node?.color ?: GraphColorPalette.nodeTypeColor(node?.type ?: "")

    item {
        DetailHeader(
            title = node?.label ?: nodeId,
            subtitle = nodeTypeLabel(node),
            accentColor = accent,
        )
    }
    node?.timeLabel?.let { time ->
        item {
            Text("Time: $time", color = ScoopGreen, style = MaterialTheme.typography.bodySmall)
        }
    }
    node?.signalCategory?.let { cat ->
        item {
            RelationPill(color = GraphColorPalette.signalColor(cat), label = GraphColorPalette.signalLabel(cat))
        }
    }
    node?.lat?.let { lat ->
        node.lon?.let { lon ->
            item {
                Text("Location: $lat, $lon", color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    when {
        scanId != null -> {
            val snapshot = viewModel.snapshotForScan(scanId)
            item { ScanSummaryBlock(snapshot) }
            if (snapshot != null) {
                item {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenTimelineForScan(scanId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = ScoopGreen,
                                contentColor = ScoopBlack,
                            ),
                    ) {
                        Text("Open full scan in Timeline")
                    }
                }
                val signals = viewModel.radioFindingsForScan(scanId)
                item {
                    Text(
                        "Signals in this scan (${signals.size})",
                        color = ScoopWhite,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(signals.take(40), key = { "${scanId}-${it.id}" }) { finding ->
                    SignalDetailBlock(
                        finding = finding,
                        petName = signalKeyFrom(finding)?.let { key -> viewModel.aliasForKey(key, uiState.vault) },
                        onPetName = {
                            signalKeyFrom(finding)?.let { key ->
                                viewModel.beginAlias(key, viewModel.aliasForKey(key, uiState.vault))
                            }
                        },
                    )
                }
            }
        }
        signalKey != null -> {
            val linkedScans = viewModel.scanIdsForSignal(signalKey)
            item {
                Text("Signal key: $signalKey", color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
                if (linkedScans.isNotEmpty()) {
                    Text(
                        "Seen in ${linkedScans.size} scan(s)",
                        color = ScoopGreen,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            val findings = viewModel.findingsForSignalKey(signalKey)
            items(findings.take(20), key = { "${it.id}-${it.detail.hashCode()}" }) { finding ->
                SignalDetailBlock(
                    finding = finding,
                    petName = viewModel.aliasForKey(signalKey, uiState.vault),
                    onPetName = {
                        viewModel.beginAlias(signalKey, viewModel.aliasForKey(signalKey, uiState.vault))
                    },
                )
            }
            linkedScans.forEach { id ->
                item(key = "scan-link-$id") {
                    val snap = viewModel.snapshotForScan(id)
                    if (snap != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenTimelineForScan(id)
                            },
                        ) {
                            Text("View scan: ${snap.name}", color = ScoopGreen)
                        }
                    }
                }
            }
        }
        node?.type == KnowledgeGraphBuilder.NODE_PLACE -> {
            item {
                val linked = viewModel.scanIdsAtPlace(nodeId)
                Text("Scans at this place (${linked.size})", color = ScoopWhite, style = MaterialTheme.typography.titleSmall)
            }
            val linked = viewModel.scanIdsAtPlace(nodeId)
            items(linked, key = { "place-scan-$it" }) { id ->
                val snap = viewModel.snapshotForScan(id)
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenTimelineForScan(id)
                    },
                ) {
                    Text(snap?.name ?: id, color = ScoopGreen)
                }
            }
        }
        else -> {
            item {
                Text(node?.rawLabel ?: "No extra details.", color = ScoopMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String, accentColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(accentColor),
        )
        Column {
            Text(title, color = ScoopWhite, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = ScoopMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RelationPill(color: Color, label: String) {
    Surface(
        color = color.copy(alpha = 0.28f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.85f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(color),
            )
            Text(label, color = ScoopWhite, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NodeSummaryCard(node: GraphVisNode?, fallbackId: String, onOpen: () -> Unit) {
    val color = node?.color ?: GraphColorPalette.nodeTypeColor(node?.type ?: "")
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = ScoopSurfaceHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(14.dp).clip(CircleShape).background(color),
            )
            Column(Modifier.weight(1f)) {
                Text(node?.label ?: fallbackId, color = ScoopWhite, style = MaterialTheme.typography.bodyLarge)
                Text(nodeTypeLabel(node), color = ScoopMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("View", color = ScoopGreen, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun nodeTypeLabel(node: GraphVisNode?): String =
    when (node?.type) {
        KnowledgeGraphBuilder.NODE_SCAN -> "Scan session"
        KnowledgeGraphBuilder.NODE_PLACE -> "Place"
        KnowledgeGraphBuilder.NODE_SIGNAL ->
            GraphColorPalette.signalLabel(node.signalCategory) + " signal"
        "USER" -> "Your note"
        "EVRUS" -> "EVRUS"
        "DEVICE" -> "Device"
        else -> node?.type ?: "Node"
    }

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun ScanSummaryBlock(snapshot: ScanSnapshot?) {
    if (snapshot == null) {
        Text("Scan not found in local history.", color = ScoopMuted)
        return
    }
    val time = formatTime(snapshot.scannedAtEpochMs)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(time, color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
        snapshot.geoFix?.let {
            Text(
                "GPS ${it.formatCoordinates()} · ${it.formatAccuracy()}",
                color = ScoopGreen,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        snapshot.riskSummary?.let {
            Text(
                "Risk: ${it.level.label} ${it.score}/100",
                color = ScoopMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SignalDetailBlock(
    finding: Finding,
    petName: String?,
    onPetName: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FindingCard(finding = finding)
        if (petName != null) {
            Text("Pet name: $petName", color = ScoopGreen, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onPetName) { Text("Name signal", color = ScoopGreen) }
    }
}

private fun signalKeyFrom(finding: Finding): String? =
    when {
        finding.id.startsWith("ble-") -> finding.id.removePrefix("ble-")
        finding.id.startsWith("wifi-") -> finding.id.removePrefix("wifi-")
        else -> finding.id.takeIf { finding.category == SignalCategory.BLUETOOTH }
    }
