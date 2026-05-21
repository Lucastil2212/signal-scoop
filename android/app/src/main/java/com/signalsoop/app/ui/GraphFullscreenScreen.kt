package com.signalsoop.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.ui.graph.KnowledgeGraphGeoTimelineView
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphFullscreenScreen(
    viewModel: HistoryViewModel,
    onDismiss: () -> Unit,
    onOpenGraphTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    ScanDetailSheet(
        scanId = uiState.scanDetailScanId,
        viewModel = viewModel,
        uiState = uiState,
        onDismiss = viewModel::dismissScanDetail,
    )

    GraphDetailSheet(
        nodeId = uiState.graphDetailNodeId,
        link = uiState.graphDetailLink,
        viewModel = viewModel,
        uiState = uiState,
        onDismiss = viewModel::dismissGraphDetail,
        onOpenScanDetail = { scanId ->
            viewModel.dismissGraphDetail()
            viewModel.openScanDetail(scanId)
        },
        onOpenTimelineForScan = {
            viewModel.dismissGraphDetail()
            onDismiss()
            onOpenGraphTab()
            viewModel.openScanDetail(it)
        },
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScoopBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Knowledge map", color = ScoopWhite)
                        Text(
                            "Tap scans · nodes · lines · ← back when done",
                            color = ScoopMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = ScoopWhite)
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { viewModel.refreshGraphAndInsights() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh graph", tint = ScoopGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScoopBlack),
            )
        },
    ) { padding ->
        KnowledgeGraphGeoTimelineView(
            visualization = uiState.graphVisualization,
            filterScanId = uiState.graphFilterScanId,
            onFilterScanChange = viewModel::setGraphTimelineFilter,
            onNodeSelected = viewModel::onGraphNodeSelected,
            onLinkSelected = viewModel::onGraphLinkSelected,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}
