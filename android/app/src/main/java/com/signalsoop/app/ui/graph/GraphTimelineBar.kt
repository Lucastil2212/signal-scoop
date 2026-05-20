package com.signalsoop.app.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.signalsoop.app.history.GraphTimelineScan
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GraphTimelineBar(
    scans: List<GraphTimelineScan>,
    filterScanId: String?,
    filterLabel: String,
    onSelectScan: (String?) -> Unit,
    onOpenScanDetail: (scanId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (scans.isEmpty()) return

    val timeFmt = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
    val scroll = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ScoopSurfaceHigh,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Past scans", color = ScoopWhite, style = MaterialTheme.typography.labelLarge)
                    Text("Tap to view signals · filters map", color = ScoopMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text(filterLabel, color = ScoopMuted, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filterScanId == null,
                    onClick = { onSelectScan(null) },
                    label = { Text("All") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScoopGreen,
                            selectedLabelColor = ScoopBlack,
                        ),
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scans.forEach { scan ->
                    val selected = filterScanId == scan.scanId
                    Surface(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectScan(scan.scanId)
                                    onOpenScanDetail(scan.scanId)
                                }
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) ScoopGreen else ScoopMuted.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp),
                                ),
                        color = if (selected) ScoopBlack else ScoopSurfaceHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(scan.color),
                            )
                            Column {
                                Text(
                                    scan.label.take(22),
                                    color = ScoopWhite,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    timeFmt.format(Date(scan.epochMs)),
                                    color = ScoopMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "${scan.signalCount} signals",
                                    color = ScoopMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GraphColorLegend(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = ScoopBlack.copy(alpha = 0.82f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendRow(Color(0xFF39FF14), "Scan (color = session)")
            LegendRow(Color(0xFF7AE7FF), "BLE signal")
            LegendRow(Color(0xFFFFB020), "Wi-Fi signal")
            LegendRow(Color(0xFFFF4D6D), "Bluetooth signal")
            LegendRow(Color(0xFF00AEEF), "Place")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(color),
        )
        Text(label, color = ScoopMuted, style = MaterialTheme.typography.labelSmall)
    }
}
