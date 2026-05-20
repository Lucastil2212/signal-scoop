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
import androidx.compose.foundation.shape.CircleShape
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

    val allRadio = remember(snapshot) { viewModel.radioFindingsForScan(scanId) }
    val filtered =
        if (categoryFilter == SignalCategory.ALL) {
            allRadio
        } else {
            allRadio.filter { it.category == categoryFilter }
        }
    val bleCount = allRadio.count { it.category == SignalCategory.BLE }
    val wifiCount = allRadio.count { it.category == SignalCategory.WIFI }
    val btCount = allRadio.count { it.category == SignalCategory.BLUETOOTH }

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
                        "Saved scan session — everything detected in this run",
                        color = ScoopMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { ScanDetailSummary(snapshot, bleCount, wifiCount, btCount) }
            snapshot.riskSummary?.let { risk ->
                item { RiskCard(summary = risk) }
            }
            item {
                MediaActionsRow(scanId = scanId, signalKey = null, viewModel = viewModel)
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
                    bleCount = bleCount,
                    wifiCount = wifiCount,
                    btCount = btCount,
                    total = allRadio.size,
                    onSelect = { categoryFilter = it },
                )
            }
            item {
                Text(
                    if (categoryFilter == SignalCategory.ALL) {
                        "All radio signals (${filtered.size})"
                    } else {
                        "${categoryFilter.label} (${filtered.size} of ${allRadio.size})"
                    },
                    color = ScoopWhite,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No ${categoryFilter.label.lowercase()} signals in this scan. Try another filter or run a new scan on the Scan tab.",
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
    bleCount: Int,
    wifiCount: Int,
    btCount: Int,
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
                "Detected: $bleCount BLE · $wifiCount Wi-Fi · $btCount Bluetooth",
                color = ScoopWhite,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScanCategoryFilters(
    selected: SignalCategory,
    bleCount: Int,
    wifiCount: Int,
    btCount: Int,
    total: Int,
    onSelect: (SignalCategory) -> Unit,
) {
    val scroll = rememberScrollState()
    val chips =
        listOf(
            SignalCategory.ALL to "All ($total)",
            SignalCategory.BLE to "BLE ($bleCount)",
            SignalCategory.WIFI to "Wi-Fi ($wifiCount)",
            SignalCategory.BLUETOOTH to "Bluetooth ($btCount)",
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
            Text(
                "Your name for this signal: $petName",
                color = ScoopGreen,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onName) {
            Text("Give this signal a friendly name", color = ScoopGreen)
        }
    }
}

private fun scanSignalKey(finding: Finding): String? =
    when {
        finding.id.startsWith("ble-") -> finding.id.removePrefix("ble-")
        finding.id.startsWith("wifi-") -> finding.id.removePrefix("wifi-")
        else -> finding.id.takeIf { finding.category == SignalCategory.BLUETOOTH }
    }
