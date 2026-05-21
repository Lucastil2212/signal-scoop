package com.signalsoop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.history.KnowledgeGraphBuilder
import com.signalsoop.app.history.ScanFindingCounts
import com.signalsoop.app.history.ScanSnapshot
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.ui.components.FindingCard
import com.signalsoop.app.ui.components.RiskCard
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
fun ScanDetailSheet(
    scanId: String?,
    viewModel: HistoryViewModel,
    uiState: com.signalsoop.app.HistoryUiState,
    onDismiss: () -> Unit,
) {
    if (scanId == null) return
    val snapshot = viewModel.snapshotForScan(scanId) ?: return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryFilter by remember(scanId) { mutableStateOf(SignalCategory.ALL) }

    val counts = remember(snapshot) { ScanFindingCounts.from(snapshot.findings) }
    val allFindings = remember(snapshot) { KnowledgeGraphBuilder.displayableFindings(snapshot.findings) }
    val filtered =
        if (categoryFilter == SignalCategory.ALL) {
            allFindings
        } else {
            allFindings.filter { it.category == categoryFilter }
        }

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
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(snapshot.name, color = ScoopWhite, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Saved scan — same categories as a live scan (BLE, Wi-Fi, Bluetooth, NFC, sensors)",
                        color = ScoopMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { ScanDetailSummary(snapshot, counts) }
            snapshot.riskSummary?.let { risk ->
                item { RiskCard(summary = risk) }
            }
            item {
                Text(
                    "Filter signals",
                    color = ScoopMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            item {
                ScanCategoryFilters(
                    selected = categoryFilter,
                    counts = counts,
                    onSelect = { categoryFilter = it },
                )
            }
            item {
                Text(
                    if (categoryFilter == SignalCategory.ALL) {
                        "All signals (${filtered.size})"
                    } else {
                        "${categoryFilter.label} (${filtered.size} of ${counts.total})"
                    },
                    color = ScoopWhite,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No ${categoryFilter.label.lowercase()} in this scan. Try another filter.",
                        color = ScoopMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(filtered, key = { "${scanId}-${it.id}" }) { finding ->
                    ScanSignalRow(
                        finding = finding,
                        petName =
                            scanSignalKey(finding)?.let { key ->
                                viewModel.aliasForKey(key, uiState.vault)
                            },
                        onName = {
                            scanSignalKey(finding)?.let { key ->
                                viewModel.beginAlias(key, viewModel.aliasForKey(key, uiState.vault))
                            }
                        },
                    )
                }
            }
            item {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = ScoopMuted)
                }
            }
        }
    }
}

@Composable
private fun ScanDetailSummary(
    snapshot: ScanSnapshot,
    counts: ScanFindingCounts,
) {
    val time = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(snapshot.scannedAtEpochMs))
    Surface(color = ScoopSurfaceHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("When: $time", color = ScoopWhite, style = MaterialTheme.typography.bodyMedium)
            snapshot.geoFix?.let {
                Text(
                    "Where: ${it.formatCoordinates()} · ${it.formatAccuracy()}",
                    color = ScoopGreen,
                    style = MaterialTheme.typography.bodySmall,
                )
            } ?: Text(
                "Where: no GPS fix for this scan",
                color = ScoopMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Detected: ${counts.summaryLine()}",
                color = ScoopWhite,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScanCategoryFilters(
    selected: SignalCategory,
    counts: ScanFindingCounts,
    onSelect: (SignalCategory) -> Unit,
) {
    val scroll = rememberScrollState()
    val chips =
        listOf(
            SignalCategory.ALL to "All (${counts.total})",
            SignalCategory.BLE to "BLE (${counts.ble})",
            SignalCategory.WIFI to "Wi-Fi (${counts.wifi})",
            SignalCategory.BLUETOOTH to "Bluetooth (${counts.bluetooth})",
            SignalCategory.NFC to "NFC (${counts.nfc})",
            SignalCategory.SENSORS to "Sensors (${counts.sensors})",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (cat, label) ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = { Text(label) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScoopGreen,
                        selectedLabelColor = ScoopBlack,
                        labelColor = ScoopMuted,
                    ),
            )
        }
    }
}

@Composable
private fun ScanSignalRow(
    finding: Finding,
    petName: String?,
    onName: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FindingCard(finding = finding)
        if (petName != null) {
            Text("Pet name: $petName", color = ScoopGreen, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onName) { Text("Name signal", color = ScoopGreen) }
    }
}

private fun scanSignalKey(finding: Finding): String? =
    KnowledgeGraphBuilder.graphSignalKeyFrom(finding)
