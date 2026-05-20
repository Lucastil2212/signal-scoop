package com.signalsoop.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.signalsoop.app.R
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.ScanUiState
import com.signalsoop.app.ScanViewModel
import com.signalsoop.app.ui.components.KnowledgeGraphPreviewCard
import com.signalsoop.app.ui.components.FindingCard
import com.signalsoop.app.ui.components.RiskCard
import com.signalsoop.app.ui.components.ManticoreFooter
import com.signalsoop.app.ui.components.SecurityInfoCard
import com.signalsoop.app.ui.components.SentinelDefenseCard
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalScoopScreen(
    viewModel: ScanViewModel,
    historyViewModel: HistoryViewModel,
    onScanClick: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenGraphFullscreen: () -> Unit,
    onOpenGraphTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val historyState by historyViewModel.uiState.collectAsState()
    val insights = historyState.insights
    val listState = rememberLazyListState()
    val radioFindings =
        uiState.findings.filter {
            it.category != SignalCategory.SYSTEM && it.category != SignalCategory.ALL
        }
    val filtered =
        when (uiState.selectedCategory) {
            SignalCategory.ALL -> uiState.findings.filter { it.category != SignalCategory.SYSTEM }
            else -> uiState.findings.filter { it.category == uiState.selectedCategory }
        }

    LaunchedEffect(uiState.findings.size, uiState.isScanning) {
        if (!uiState.isScanning && uiState.findings.isNotEmpty()) {
            listState.animateScrollToItem(3)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = ScoopBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Signal Scoop", color = ScoopWhite)
                        Text(
                            "Defensive RF sentinel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ScoopMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScoopBlack),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScanActionButton(
                    uiState = uiState,
                    onScanClick = onScanClick,
                )
            }

            item {
                Text(
                    uiState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScoopMuted,
                )
            }

            item {
                CategoryFilters(
                    selected = uiState.selectedCategory,
                    onSelect = viewModel::selectCategory,
                )
            }

            if (radioFindings.isNotEmpty()) {
                item {
                    Text(
                        "${radioFindings.size} signals in this scan",
                        style = MaterialTheme.typography.labelLarge,
                        color = ScoopGreen,
                    )
                }
            }

            if (uiState.isScanning && uiState.findings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = ScoopGreen)
                    }
                }
            }

            if (!uiState.isScanning && uiState.findings.isEmpty()) {
                item {
                    EmptyState()
                }
            }

            if (!uiState.isScanning && uiState.findings.isNotEmpty() && filtered.isEmpty()) {
                item {
                    FilteredEmptyState(selected = uiState.selectedCategory)
                }
            }

            items(
                filtered,
                key = { finding -> "${finding.id}-${finding.category}-${finding.title}" },
            ) { finding ->
                FindingCard(finding = finding)
            }

            uiState.riskSummary?.let { summary ->
                item { RiskCard(summary = summary) }
            }

            uiState.sentinelReport?.let { report ->
                item { SentinelDefenseCard(report = report) }
            }

            item {
                KnowledgeGraphPreviewCard(
                    visualization = historyState.graphVisualization,
                    filterScanId = historyState.graphFilterScanId,
                    onFilterScanChange = historyViewModel::setGraphTimelineFilter,
                    scanCount = insights?.totalScans ?: historyState.snapshots.size,
                    placeCount = insights?.uniquePlaces ?: 0,
                    signalCount = insights?.recurringSignals?.size ?: 0,
                    onOpenFullscreen = onOpenGraphFullscreen,
                    onOpenGraphTab = onOpenGraphTab,
                    onNodeSelected = historyViewModel::onGraphNodeSelected,
                    onLinkSelected = historyViewModel::onGraphLinkSelected,
                    onOpenScanDetail = historyViewModel::openScanDetail,
                )
            }

            item {
                HeroHeader()
            }

            item {
                SecurityInfoCard()
            }

            item { ManticoreFooter() }
        }
    }
}

@Composable
private fun ScanActionButton(
    uiState: ScanUiState,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onScanClick,
        enabled = !uiState.isScanning,
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ScoopGreen,
            contentColor = ScoopBlack,
            disabledContainerColor = ScoopGreen.copy(alpha = 0.35f),
        ),
    ) {
        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = ScoopBlack,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text("Scanning…")
        } else {
            Text(
                if (uiState.permissionNeeded) "Grant permissions & scan" else "Scan nearby signals",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HeroHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.signal_scoop_logo),
            contentDescription = "Signal Scoop logo",
            modifier = Modifier.size(140.dp),
            contentScale = ContentScale.Fit,
        )
        RowWithIcons()
        Text(
            "Scan BLE, Wi-Fi, paired Bluetooth, NFC, and phone sensors. Everything stays on your device — no accounts, no cloud.",
            style = MaterialTheme.typography.bodyMedium,
            color = ScoopMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun RowWithIcons() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Radar, contentDescription = null, tint = ScoopBlue)
        Icon(Icons.Rounded.Security, contentDescription = null, tint = ScoopGreen)
    }
}

@Composable
private fun CategoryFilters(
    selected: SignalCategory,
    onSelect: (SignalCategory) -> Unit,
) {
    val categories = listOf(
        SignalCategory.ALL,
        SignalCategory.BLE,
        SignalCategory.WIFI,
        SignalCategory.BLUETOOTH,
        SignalCategory.SENSORS,
        SignalCategory.NFC,
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ScoopBlue.copy(alpha = 0.25f),
                    selectedLabelColor = ScoopWhite,
                    labelColor = ScoopMuted,
                ),
            )
        }
    }
}

@Composable
private fun FilteredEmptyState(selected: SignalCategory) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "No ${selected.label} signals in this scan",
            style = MaterialTheme.typography.titleMedium,
            color = ScoopWhite,
        )
        Text(
            "Tap All to see everything found, or run another scan.",
            style = MaterialTheme.typography.bodyMedium,
            color = ScoopMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Ready when you are",
            style = MaterialTheme.typography.titleLarge,
            color = ScoopWhite,
        )
        Text(
            "Grant permissions when prompted, then tap Scan. Location may be required for Wi-Fi results on Android.",
            style = MaterialTheme.typography.bodyMedium,
            color = ScoopMuted,
            textAlign = TextAlign.Center,
        )
    }
}
